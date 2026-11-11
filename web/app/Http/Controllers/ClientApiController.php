<?php

namespace App\Http\Controllers;

use App\Services\MessageStore;
use App\Services\SiteSettings;
use App\Services\SubscriptionStore;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/** API هماهنگی برنامهٔ ویندوز با سایت: پیکربندی، فعال‌سازی کد اشتراک و گزارش استفاده */
class ClientApiController extends Controller
{
    public function __construct(private SiteSettings $settings, private SubscriptionStore $subs, private MessageStore $messages) {}

    private function config(): array
    {
        $s = $this->settings->all();

        return [
            'plans' => array_map(fn ($p) => ['id' => $p['id'], 'title' => $p['title'], 'days' => (int) $p['days'], 'price' => (int) $p['price']], $this->settings->plans()),
            'cards' => array_map(fn ($c) => ['number' => preg_replace('/\D/', '', $c['number']), 'holder' => $c['holder']], $this->settings->cards()),
            'trial_enabled' => (bool) $s['flags']['trial_enabled'],
            'ai_catalog' => array_values(array_map(fn ($a) => [
                'id' => $a['id'], 'title' => $a['title'], 'kind' => $a['kind'], 'base_url' => $a['base_url'], 'hint' => $a['hint'] ?? '',
                'models' => array_values(array_filter(array_map('trim', explode(',', (string) $a['models'])))), 'enabled' => (bool) $a['enabled'],
            ], $s['ai_catalog'] ?? [])),
            'trial_days' => max(1, (int) $s['numbers']['trial_days']),
            'reminder_days' => max(1, (int) $s['numbers']['reminder_days']),
            'subscribe_url' => url('/subscribe'),
            'contact_url' => url('/#contact'),
            'today' => SubscriptionStore::today()->toDateString(),
        ];
    }

    private function subPayload(?array $s): ?array
    {
        if (! $s) {
            return null;
        }
        $v = $this->subs->view($s);

        return [
            'code' => $v['code'], 'holder' => $v['name'], 'plan_id' => $v['plan_id'], 'plan_title' => $v['plan_title'],
            'start' => $v['period_start'] ?? $v['start'], 'end' => $v['end'], 'days_left' => $v['days_left'],
            'days_total' => $v['days_total'], 'day_number' => $v['day_number'], 'state' => $v['state'],
            'org' => $v['org'] ?? null, 'seats' => (int) ($v['seats'] ?? 1),
        ];
    }

    public function configEndpoint(): JsonResponse
    {
        return response()->json(['ok' => true, 'config' => $this->config()]);
    }

    public function activate(Request $request): JsonResponse
    {
        $s = $this->subs->find((string) $request->input('code'));
        if (! $s || ($s['status'] ?? '') === 'cancelled') {
            return response()->json(['ok' => false, 'error' => 'این کد اشتراک پیدا نشد؛ لطفاً آن را دوباره از صفحهٔ ثبت اشتراک کپی کنید.', 'config' => $this->config()], 404);
        }

        return $this->ping($request);
    }

    public function ping(Request $request): JsonResponse
    {
        $code = \App\Services\SubscriptionStore::normalizeCode((string) $request->input('code'));
        $systemId = (string) $request->input('system_id', '');
        $version = (string) $request->input('app_version', '');
        $s = $code ? $this->subs->touch($code, $systemId, $version) : null;
        $payload = $this->subPayload($s && ($s['status'] ?? '') !== 'cancelled' ? $s : null);
        $this->messages->touchInstall($systemId, $version, $payload['code'] ?? null, (string) ($payload['state'] ?? $request->input('mode', 'none')));
        $notices = $this->settings->flag('client_messages_enabled')
            ? $this->messages->noticesFor($payload['code'] ?? null, $payload['state'] ?? (string) $request->input('mode'))
            : [];

        return response()->json(['ok' => true, 'subscription' => $payload, 'messages' => $notices, 'config' => $this->config()]);
    }
}
