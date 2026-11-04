<?php

namespace App\Services;

use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Hash;

/**
 * ورود مستقل به پنل مدیریت (نام کاربری/رمز در پوشهٔ دادهٔ مشترک).
 * مدیران قبلی (کاربران superuser) همچنان با ورود معمولی سایت دسترسی دارند.
 */
final class AdminAuth
{
    /** رمز اولیه فقط تا اولین تغییر معتبر است و پس از ورود، تغییرش اجباری است. */
    private const BOOTSTRAP_HASH = '$2y$12$BPDCOFqjX0OV1W0ZMzy5reieLFnxxziubdCJkiixjBpv.3EIePGke';

    public static function check(): bool
    {
        return (bool) (Auth::user()?->is_superuser) || session()->has('mx_admin');
    }

    public static function name(): string
    {
        return (string) (session('mx_admin') ?: Auth::user()?->username ?: 'مدیر');
    }

    public static function credentials(): array
    {
        $c = (new JsonStore('admin'))->read();

        return $c['username'] ?? null ? $c : ['username' => 'admin', 'hash' => self::BOOTSTRAP_HASH, 'must_change' => true];
    }

    public static function attempt(string $username, string $password): bool
    {
        $c = self::credentials();
        $ok = hash_equals(mb_strtolower($c['username']), mb_strtolower(trim($username))) && password_verify($password, $c['hash']);
        if ($ok) {
            session()->regenerate();
            session(['mx_admin' => $c['username']]);
        }

        return $ok;
    }

    public static function mustChange(): bool
    {
        return session()->has('mx_admin') && ! empty(self::credentials()['must_change']);
    }

    public static function change(string $username, string $password): void
    {
        (new JsonStore('admin'))->update(function (array &$d) use ($username, $password) {
            $d = ['username' => trim($username), 'hash' => Hash::make($password), 'must_change' => false, 'updated_at' => now('Asia/Tehran')->format('Y-m-d H:i:s')];
        });
        if (session()->has('mx_admin')) {
            session(['mx_admin' => trim($username)]);
        }
    }
}
