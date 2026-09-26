<?php

namespace App\Services;

final class SerialService
{
    private const B32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

    public function generateSerial(string $userSystemId, int $buyTimestamp, int $endTimestamp): string
    {
        return $this->encrypt($userSystemId.$buyTimestamp.$endTimestamp);
    }

    public function encrypt(string $raw): string
    {
        $key = random_bytes(16);
        $iv = random_bytes(16);
        return $this->encryptWithKeyAndIv($raw, $key, $iv);
    }

    public function decrypt(string $encoded): string
    {
        $all = self::base32Decode(trim($encoded));
        if (strlen($all) < 48) {
            throw new \InvalidArgumentException('Invalid serial payload');
        }

        $key = substr($all, 0, 16);
        $iv = substr($all, 16, 16);
        $ciphertext = substr($all, 32);
        $decrypted = openssl_decrypt(
            $ciphertext,
            'aes-128-cbc',
            $key,
            OPENSSL_RAW_DATA | OPENSSL_ZERO_PADDING,
            $iv
        );

        if (! is_string($decrypted) || $decrypted === '') {
            throw new \RuntimeException('Serial decryption failed');
        }

        $pad = ord(substr($decrypted, -1));
        if ($pad < 1 || $pad > 16) {
            throw new \RuntimeException('Invalid serial padding');
        }

        return substr($decrypted, 0, -$pad);
    }

    public function encryptWithKeyAndIv(string $raw, string $key, string $iv): string
    {
        if (strlen($key) !== 16 || strlen($iv) !== 16) {
            throw new \InvalidArgumentException('AES key and IV must be 16 bytes');
        }

        $pad = 16 - (strlen($raw) % 16);
        $padded = $raw.str_repeat(chr($pad), $pad);
        $ciphertext = openssl_encrypt(
            $padded,
            'aes-128-cbc',
            $key,
            OPENSSL_RAW_DATA | OPENSSL_ZERO_PADDING,
            $iv
        );

        if (! is_string($ciphertext)) {
            throw new \RuntimeException('Serial encryption failed');
        }

        return self::base32Encode($key.$iv.$ciphertext);
    }

    public static function base32Encode(string $data): string
    {
        $out = '';
        $buffer = 0;
        $bits = 0;

        foreach (str_split($data) as $char) {
            $buffer = ($buffer << 8) | ord($char);
            $bits += 8;

            while ($bits >= 5) {
                $out .= self::B32[($buffer >> ($bits - 5)) & 0x1F];
                $bits -= 5;
            }
        }

        if ($bits > 0) {
            $out .= self::B32[($buffer << (5 - $bits)) & 0x1F];
        }

        while (strlen($out) % 8 !== 0) {
            $out .= '=';
        }

        return $out;
    }

    public static function base32Decode(string $encoded): string
    {
        $encoded = rtrim(strtoupper($encoded), '=');
        $out = '';
        $buffer = 0;
        $bits = 0;

        foreach (str_split($encoded) as $char) {
            $value = strpos(self::B32, $char);
            if ($value === false) {
                throw new \InvalidArgumentException("Invalid base32 character: {$char}");
            }

            $buffer = ($buffer << 5) | $value;
            $bits += 5;

            if ($bits >= 8) {
                $out .= chr(($buffer >> ($bits - 8)) & 0xFF);
                $bits -= 8;
            }
        }

        return $out;
    }
}
