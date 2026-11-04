<?php

namespace App\Services;

use Carbon\CarbonImmutable;

/**
 * اشتراک‌های خوداظهاری و اعتمادمحور. هر اشتراک یک «کد اشتراک» دارد که در برنامه وارد می‌شود
 * تا روزشمار برنامه و پنل مدیریت یکسان باشد و پیام‌های مدیر به برنامه برسد.
 */
final class SubscriptionStore
{
    private JsonStore $store;

    public function __construct(private SiteSettings $settings)
    {
        $this->store = new JsonStore('subscriptions');
    }

    public static function today(): CarbonImmutable
    {
        return CarbonImmutable::now('Asia/Tehran')->startOfDay();
    }

    public static function newCode(): string
    {
        $abc = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
        $part = fn () => implode('', array_map(fn () => $abc[random_int(0, strlen($abc) - 1)], range(1, 4)));

        return 'MT-'.$part().'-'.$part();
    }

    public static function normalizeCode(?string $code): string
    {
        $c = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', SupportReportService::latinize((string) $code)));
        if (str_starts_with($c, 'MT') && strlen($c) === 10) {
            return 'MT-'.substr($c, 2, 4).'-'.substr($c, 6, 4);
        }

        return '';
    }

    public function all(): array
    {
        return $this->store->read();
    }

    public function find(string $code): ?array
    {
        $code = self::normalizeCode($code);

        return $code === '' ? null : ($this->all()[$code] ?? null);
    }

    /** ثبت اشتراک تازه یا تمدید اشتراک موجود (دوره از پایان دورهٔ فعلی یا از امروز ادامه می‌یابد) */
    public function register(array $in, array $plan, ?string $renewCode = null): array
    {
        return $this->store->update(function (array &$all) use ($in, $plan, $renewCode) {
            $today = self::today();
            $code = $renewCode ? self::normalizeCode($renewCode) : '';
            $existing = $code !== '' ? ($all[$code] ?? null) : null;

            if ($existing === null) {
                do { $code = self::newCode(); } while (isset($all[$code]));
                $start = isset($in['start']) ? CarbonImmutable::parse($in['start'], 'Asia/Tehran')->startOfDay() : $today;
                $existing = [
                    'code' => $code,
                    'name' => $in['name'],
                    'contact' => $in['contact'],
                    'created_at' => now('Asia/Tehran')->format('Y-m-d H:i:s'),
                    'history' => [],
                    'status' => 'active',
                    'notes' => '',
                    'contacted_at' => null,
                    'last_seen' => null,
                    'clients' => [],
                ];
                $type = 'new';
            } else {
                $end = CarbonImmutable::parse($existing['end'], 'Asia/Tehran');
                $start = $end->greaterThan($today) ? $end : $today;
                $existing['status'] = 'active';
                $existing['contacted_at'] = null;
                $type = 'renew';
            }

            $end = $start->addDays((int) $plan['days']);
            $existing['plan_id'] = $plan['id'];
            $existing['plan_title'] = $plan['title'];
            $existing['price'] = (int) $plan['price'];
            $existing['start'] = $type === 'new' ? $start->toDateString() : ($existing['start'] ?? $start->toDateString());
            $existing['period_start'] = $start->toDateString();
            $existing['end'] = $end->toDateString();
            $existing['history'][] = [
                'type' => $type,
                'plan_id' => $plan['id'],
                'price' => (int) $plan['price'],
                'start' => $start->toDateString(),
                'end' => $end->toDateString(),
                'tracking_code' => $in['tracking_code'] ?? null,
                'card_last4' => $in['card_last4'] ?? null,
                'at' => now('Asia/Tehran')->format('Y-m-d H:i:s'),
            ];
            $all[$code] = $existing;

            return $existing;
        });
    }

    /** اعمال اقدام‌های ساده مدیر */
    public function adminAction(string $code, string $action, array $data = []): bool
    {
        return (bool) $this->store->update(function (array &$all) use ($code, $action, $data) {
            if (! isset($all[$code])) {
                return false;
            }
            $s = &$all[$code];
            switch ($action) {
                case 'contacted': $s['contacted_at'] = now('Asia/Tehran')->format('Y-m-d H:i'); break;
                case 'cancel': $s['status'] = 'cancelled'; break;
                case 'reactivate': $s['status'] = 'active'; break;
                case 'extend':
                    $days = max(1, min(3650, (int) ($data['days'] ?? 0)));
                    $end = CarbonImmutable::parse($s['end'], 'Asia/Tehran');
                    $base = $end->greaterThan(self::today()) ? $end : self::today();
                    $s['end'] = $base->addDays($days)->toDateString();
                    $s['history'][] = ['type' => 'admin_extend', 'days' => $days, 'price' => 0, 'at' => now('Asia/Tehran')->format('Y-m-d H:i:s')];
                    break;
                case 'note': $s['notes'] = mb_substr((string) ($data['notes'] ?? ''), 0, 1000); break;
                case 'delete': unset($all[$code]); break;
                default: return false;
            }

            return true;
        });
    }

    /** ثبت آخرین زمان استفاده از برنامه با این کد */
    public function touch(string $code, string $systemId, string $version): ?array
    {
        return $this->store->update(function (array &$all) use ($code, $systemId, $version) {
            if (! isset($all[$code])) {
                return null;
            }
            $now = now('Asia/Tehran')->format('Y-m-d H:i:s');
            $all[$code]['last_seen'] = $now;
            if ($systemId !== '') {
                $all[$code]['clients'][substr($systemId, 0, 64)] = ['last_seen' => $now, 'version' => mb_substr($version, 0, 12)];
                $all[$code]['clients'] = array_slice($all[$code]['clients'], -5, null, true);
            }

            return $all[$code];
        });
    }

    /** وضعیت محاسبه‌شده برای نمایش در پنل و برنامه */
    public function view(array $s): array
    {
        $today = self::today();
        $start = CarbonImmutable::parse($s['period_start'] ?? $s['start'], 'Asia/Tehran');
        $end = CarbonImmutable::parse($s['end'], 'Asia/Tehran');
        $left = max(0, (int) $today->diffInDays($end, false));
        $total = max(1, (int) $start->diffInDays($end, false));
        $reminder = $this->settings->number('reminder_days') ?: 7;
        $status = ($s['status'] ?? 'active') === 'cancelled' ? 'cancelled'
            : ($left <= 0 ? 'expired' : ($left <= $reminder ? 'expiring' : 'active'));

        return $s + [
            'days_left' => $left,
            'days_total' => $total,
            'day_number' => min($total, max(1, (int) $start->diffInDays($today, false) + 1)),
            'state' => $status,
        ];
    }

    public function stats(): array
    {
        $views = array_map(fn ($s) => $this->view($s), array_values($this->all()));
        $since30 = now('Asia/Tehran')->subDays(30)->format('Y-m-d H:i:s');
        $monthStart = now('Asia/Tehran')->startOfMonth()->format('Y-m-d H:i:s');
        $rev30 = $revMonth = $revTotal = 0;
        $renewals = 0;
        $mrr = 0.0;
        $mix = [];
        foreach ($views as $v) {
            foreach ($v['history'] as $h) {
                $p = (int) ($h['price'] ?? 0);
                $revTotal += $p;
                if (($h['at'] ?? '') >= $since30) { $rev30 += $p; }
                if (($h['at'] ?? '') >= $monthStart) { $revMonth += $p; }
                if (($h['type'] ?? '') === 'renew') { $renewals++; }
            }
            if (in_array($v['state'], ['active', 'expiring'], true)) {
                $mrr += ((int) $v['price']) / max(1, $v['days_total']) * 30;
                $mix[$v['plan_title']] = ($mix[$v['plan_title']] ?? 0) + 1;
            }
        }
        $count = fn ($st) => count(array_filter($views, fn ($v) => $v['state'] === $st));
        $dayAgo = now('Asia/Tehran')->subDay()->format('Y-m-d H:i:s');

        return [
            'total' => count($views),
            'active' => $count('active') + $count('expiring'),
            'expiring' => $count('expiring'),
            'expired' => $count('expired'),
            'need_contact' => count(array_filter($views, fn ($v) => in_array($v['state'], ['expiring', 'expired'], true) && empty($v['contacted_at']))),
            'active_today' => count(array_filter($views, fn ($v) => ($v['last_seen'] ?? '') >= $dayAgo)),
            'revenue_30' => $rev30,
            'revenue_month' => $revMonth,
            'revenue_total' => $revTotal,
            'mrr' => (int) round($mrr),
            'renewals' => $renewals,
            'plan_mix' => $mix,
        ];
    }
}
