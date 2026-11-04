<?php

namespace App\Services;

/**
 * ذخیره‌سازی سادهٔ JSON با قفل فایل، در پوشهٔ دادهٔ مشترک (خارج از ریشهٔ وب).
 * به پایگاه دادهٔ SQLite تولیدی دست نمی‌زند.
 */
final class JsonStore
{
    private string $file;

    public function __construct(string $name)
    {
        $dir = (string) config('services.matnyaab.data_dir');
        if (! is_dir($dir)) {
            @mkdir($dir, 0750, true);
        }
        $this->file = rtrim($dir, '/\\').DIRECTORY_SEPARATOR.$name.'.json';
    }

    public function read(): array
    {
        if (! is_file($this->file)) {
            return [];
        }
        $data = json_decode((string) @file_get_contents($this->file), true);

        return is_array($data) ? $data : [];
    }

    /** خواندن، تغییر و نوشتن اتمی با قفل انحصاری. خروجی تابع برگردانده می‌شود. */
    public function update(callable $fn): mixed
    {
        $fp = fopen($this->file, 'c+');
        if ($fp === false) {
            throw new \RuntimeException('data store is not writable');
        }
        try {
            flock($fp, LOCK_EX);
            $raw = stream_get_contents($fp);
            $data = json_decode($raw === false || $raw === '' ? '[]' : $raw, true);
            $data = is_array($data) ? $data : [];
            $result = $fn($data);
            $json = json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
            ftruncate($fp, 0);
            rewind($fp);
            fwrite($fp, (string) $json);
            fflush($fp);

            return $result;
        } finally {
            flock($fp, LOCK_UN);
            fclose($fp);
        }
    }
}
