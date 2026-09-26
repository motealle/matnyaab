<?php

namespace App\Support;

use Illuminate\Support\Facades\Hash;

final class DjangoPassword
{
    public static function verify(string $plain, string $encoded): bool
    {
        if ($encoded === '' || str_starts_with($encoded, '!')) {
            return false;
        }

        if (str_starts_with($encoded, '$2y$') || str_starts_with($encoded, '$2a$')
            || str_starts_with($encoded, '$argon2')) {
            return Hash::check($plain, $encoded);
        }

        $parts = explode('$', $encoded, 4);
        if (count($parts) !== 4) {
            return false;
        }

        [$algorithm, $iterations, $salt, $expected] = $parts;
        $rounds = filter_var($iterations, FILTER_VALIDATE_INT);
        if ($rounds === false || $rounds <= 0) {
            return false;
        }

        $digest = match ($algorithm) {
            'pbkdf2_sha256' => 'sha256',
            'pbkdf2_sha1' => 'sha1',
            default => null,
        };

        if ($digest === null) {
            return false;
        }

        $derived = hash_pbkdf2($digest, $plain, $salt, $rounds, 0, true);
        return hash_equals($expected, base64_encode($derived));
    }
}
