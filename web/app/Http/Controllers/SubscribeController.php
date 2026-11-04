<?php

namespace App\Http\Controllers;

use App\Services\MessageStore;
use App\Services\SiteSettings;
use App\Services\SubscriptionStore;
use App\Services\SupportReportService;
use App\Support\SpamGuard;
use Illuminate\Http\Request;

class SubscribeController extends Controller
{
    public function show(Request $request, SiteSettings $settings, SubscriptionStore $subs)
    {
        $code = SubscriptionStore::normalizeCode($request->query('code'));
        $current = $code ? $subs->find($code) : null;

        $s = $settings->all();

        return view('subscribe', [
            'orgTiers' => ['min' => max(2, (int) $s['numbers']['org_min_seats']), 't1' => (int) $s['numbers']['org_tier1'], 't2' => (int) $s['numbers']['org_tier2'], 't3' => (int) $s['numbers']['org_tier3']],
            'type' => $current && ! empty($current['org']) ? 'org' : ($request->query('type') === 'org' ? 'org' : 'personal'),
            'settings' => $settings->all(),
            'plans' => $settings->plans(),
            'cards' => $settings->cards(),
            'renew' => $current ? $subs->view($current) : null,
            'selected' => (string) $request->query('plan', ''),
        ]);
    }

    public function store(Request $request, SiteSettings $settings, SubscriptionStore $subs)
    {
        abort_unless($settings->flag('subscribe_enabled'), 404);
        if (SpamGuard::isSpam($request, ['name'])) {
            return back()->withErrors(['form' => 'ارسال فرم کامل نشد؛ لطفاً چند ثانیه بعد دوباره «ثبت» را بزنید.'])->withInput();
        }
        $request->merge([
            'card_last4' => SupportReportService::normalizeDigits($request->input('card_last4')),
            'tracking_code' => trim(SupportReportService::latinize((string) $request->input('tracking_code'))),
        ]);
        $renew = SubscriptionStore::normalizeCode($request->input('renew_code'));
        $existing = $renew ? $subs->find($renew) : null;

        $isOrg = $request->input('type') === 'org' || ! empty($existing['org']);
        $minSeats = max(2, $settings->number('org_min_seats'));
        $rules = [
            'plan' => ['required', 'string', 'max:10'],
            'tracking_code' => [$settings->flag('require_tracking_code') ? 'required' : 'nullable', 'string', 'max:40'],
            'card_last4' => ['nullable', 'digits:4'],
            'trust' => ['accepted'],
        ];
        if (! $existing) {
            $rules['name'] = ['required', 'string', 'max:80'];
            $rules['contact'] = ['required', 'string', 'max:120'];
            $rules['start'] = ['nullable', 'date_format:Y-m-d'];
        }
        if ($isOrg) {
            abort_unless($settings->flag('org_enabled'), 404);
            $rules['seats'] = ['required', 'integer', 'min:'.$minSeats, 'max:2000'];
            if (! $existing) {
                $rules['org'] = ['required', 'string', 'max:100'];
            }
        }
        $data = $request->validate($rules, [
            'name.required' => 'نام خود را بنویسید.',
            'contact.required' => 'موبایل یا ایمیل را بنویسید تا موعد تمدید را یادآوری کنیم.',
            'tracking_code.required' => 'کد پیگیری واریز را بنویسید.',
            'card_last4.digits' => 'چهار رقم آخر کارت باید چهار رقم باشد.',
            'trust.accepted' => 'لطفاً تیک تأیید را بزنید تا ثبت کنیم. 🌷',
            'seats.min' => 'اشتراک سازمانی از :min کاربر شروع می‌شود.',
            'org.required' => 'نام سازمان یا مجموعه را بنویسید.',
        ]);
        $plan = $settings->plan($data['plan']);
        if (! $plan) {
            return back()->withErrors(['plan' => 'یک پلن معتبر انتخاب کنید.'])->withInput();
        }

        if ($isOrg) {
            $seats = (int) $data['seats'];
            $plan = ['id' => $plan['id'], 'title' => 'سازمانی '.$plan['title'].' · '.$seats.' کاربر', 'days' => $plan['days'], 'price' => $settings->orgQuote($plan, $seats)];
        }
        $record = $subs->register([
            'org' => $isOrg ? ($existing['org'] ?? trim((string) $data['org'])) : null,
            'seats' => $isOrg ? (int) $data['seats'] : null,
            'name' => $existing['name'] ?? trim($data['name']),
            'contact' => $existing['contact'] ?? trim($data['contact']),
            'start' => $data['start'] ?? null,
            'tracking_code' => $data['tracking_code'] ?: null,
            'card_last4' => $data['card_last4'] ?: null,
        ], $plan, $existing ? $renew : null);

        return redirect()->route('subscribe.done', ['code' => $record['code']])
            ->with('subscribed', $existing ? 'renew' : 'new');
    }

    public function done(Request $request, SubscriptionStore $subs, SiteSettings $settings)
    {
        $s = $subs->find((string) $request->query('code'));
        abort_unless($s && session('subscribed'), 404);
        session()->keep('subscribed');

        return view('subscribe-done', ['s' => $subs->view($s), 'kind' => session('subscribed'), 'settings' => $settings->all()]);
    }

    /** فرم پشتیبانی و پیشنهاد قابلیت از صفحهٔ اصلی */
    public function contact(Request $request, SiteSettings $settings, MessageStore $messages)
    {
        $type = $request->input('type') === 'feature' ? 'feature' : 'support';
        abort_unless($settings->flag($type === 'feature' ? 'feature_requests_enabled' : 'contact_enabled'), 404);
        $back = redirect()->to(route('home').'#contact');
        if (SpamGuard::isSpam($request, ['message'])) {
            return $back->with('contact_status', 'پیام شما دریافت شد. سپاسگزاریم.');
        }
        $data = $request->validate([
            'name' => ['required', 'string', 'max:80'],
            'contact' => ['required', 'string', 'max:120'],
            'message' => ['required', 'string', 'min:10', 'max:2000'],
            'code' => ['nullable', 'string', 'max:20'],
        ], [
            'name.required' => 'نام خود را بنویسید.',
            'contact.required' => 'موبایل یا ایمیل را بنویسید تا بتوانیم پاسخ دهیم.',
            'message.required' => 'متن پیام را بنویسید.',
            'message.min' => 'پیام کمی کوتاه است؛ دست‌کم چند کلمه بنویسید.',
        ]);
        $hash = sha1(mb_strtolower(trim($data['contact'])).'|'.trim($data['message']));
        if (! $messages->isDuplicate($hash)) {
            $messages->addInbox([
                'type' => $type,
                'name' => trim($data['name']),
                'contact' => trim($data['contact']),
                'message' => trim($data['message']),
                'code' => SubscriptionStore::normalizeCode($data['code'] ?? '') ?: null,
                'hash' => $hash,
            ]);
        }

        return $back->with('contact_status', $type === 'feature'
            ? 'پیشنهاد شما ثبت شد. ممنون که در ساختن متن‌یاب همراه ما هستید.'
            : 'پیام شما رسید. '.$settings->text('support_hours'));
    }
}
