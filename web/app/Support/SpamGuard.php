<?php

namespace App\Support;

use Illuminate\Http\Request;

/**
 * ضداسپم ساده و بی‌دردسر برای کاربر: فیلد پنهان (honeypot)، توکن زمان‌دار امضاشده
 * (ارسال زودتر از ۳ ثانیه یا دیرتر از ۶ ساعت رد می‌شود) و محدودیت تعداد پیوند.
 * محدودیت تعداد درخواست با میان‌افزار throttle روی مسیرها اعمال می‌شود.
 */
final class SpamGuard
{
    public static function token(): string
    {
        $t = (string) time();

        return $t.'.'.substr(hash_hmac('sha256', $t, (string) config('app.key')), 0, 20);
    }

    public static function fields(): string
    {
        return '<input type="hidden" name="_ts" value="'.e(self::token()).'">'
            .'<div class="hp-field" aria-hidden="true"><label>وب‌سایت<input name="website" tabindex="-1" autocomplete="off"></label></div>';
    }

    /** @return bool true اگر درخواست مشکوک به اسپم باشد */
    public static function isSpam(Request $r, array $textFields = []): bool
    {
        if (trim((string) $r->input('website', '')) !== '') {
            return true;
        }
        [$t, $sig] = array_pad(explode('.', (string) $r->input('_ts', ''), 2), 2, '');
        if (! ctype_digit($t) || ! hash_equals(substr(hash_hmac('sha256', $t, (string) config('app.key')), 0, 20), $sig)) {
            return true;
        }
        $age = time() - (int) $t;
        if ($age < 3 || $age > 6 * 3600) {
            return true;
        }
        foreach ($textFields as $f) {
            if (preg_match_all('~https?://|www\.~i', (string) $r->input($f, '')) > 2) {
                return true;
            }
        }

        return false;
    }
}
