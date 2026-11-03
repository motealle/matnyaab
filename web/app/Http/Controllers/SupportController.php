<?php

namespace App\Http\Controllers;

use App\Services\SupportReportService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class SupportController extends Controller
{
    public function store(Request $request, SupportReportService $reports): RedirectResponse
    {
        // فیلد پنهان ضدربات: کاربر واقعی آن را خالی می‌گذارد
        if (trim((string) $request->input('website', '')) !== '') {
            return redirect()->to(route('home').'#support')->with('support_status', 'سپاس از حمایت شما.');
        }

        $request->merge([
            'amount' => SupportReportService::normalizeDigits($request->input('amount')),
            'card_last4' => SupportReportService::normalizeDigits($request->input('card_last4')),
        ]);

        $data = $request->validate([
            'name' => ['required', 'string', 'max:80'],
            'contact' => ['required', 'string', 'max:120'],
            'amount' => ['required', 'digits_between:3,12'],
            'card_last4' => ['nullable', 'digits:4'],
            'paid_at' => ['nullable', 'string', 'max:40'],
            'message' => ['nullable', 'string', 'max:600'],
        ], [
            'name.required' => 'لطفاً نام خود را بنویسید.',
            'contact.required' => 'یک راه ارتباطی (موبایل یا ایمیل) بنویسید.',
            'amount.required' => 'مبلغ واریزی را به تومان بنویسید.',
            'amount.digits_between' => 'مبلغ را فقط با رقم و به تومان بنویسید.',
            'card_last4.digits' => 'چهار رقم آخر کارت باید دقیقاً چهار رقم باشد.',
        ], [
            'name' => 'نام',
            'contact' => 'راه ارتباطی',
            'amount' => 'مبلغ',
        ]);

        try {
            $reports->append([
                'received_at' => now('Asia/Tehran')->format('Y-m-d H:i:s'),
                'name' => trim($data['name']),
                'contact' => trim($data['contact']),
                'amount_toman' => (int) $data['amount'],
                'card_last4' => $data['card_last4'] ?: null,
                'paid_at' => isset($data['paid_at']) ? trim((string) $data['paid_at']) : null,
                'message' => isset($data['message']) ? trim((string) $data['message']) : null,
                'user_id' => $request->user()?->id,
            ]);
        } catch (\RuntimeException) {
            return redirect()->to(route('home').'#support')
                ->withInput()
                ->withErrors(['support' => 'ثبت اعلام واریز انجام نشد. لطفاً چند دقیقهٔ دیگر دوباره تلاش کنید.']);
        }

        return redirect()->to(route('home').'#support')
            ->with('support_status', 'اعلام واریز شما ثبت شد. از حمایت‌تان صمیمانه سپاسگزاریم.');
    }
}
