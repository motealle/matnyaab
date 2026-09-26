<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;

final class SmsService
{
    public function sendConfirmationCode(string $phoneNumber, string $confirmCode): void
    {
        $url = (string) config('services.sms.url', '');
        $username = (string) config('services.sms.username', '');
        $password = (string) config('services.sms.password', '');
        $from = (string) config('services.sms.number', '');

        if ($url === '' || $username === '' || $password === '' || $from === '') {
            throw new \RuntimeException('ارسال کد تأیید در حال حاضر در دسترس نیست. لطفاً کمی بعد دوباره تلاش کنید.');
        }

        $response = Http::timeout(20)->get($url, [
            'from' => $from,
            'to' => $phoneNumber,
            'username' => $username,
            'password' => $password,
            'message' => 'کد تأیید متن‌یاب: '.$confirmCode,
        ]);

        if (! $response->successful()) {
            throw new \RuntimeException('ارسال کد تأیید انجام نشد. لطفاً دوباره تلاش کنید.');
        }
    }
}
