<?php

namespace App\Services;

/**
 * ذخیرهٔ «اعلام واریز» حمایت داوطلبانه در یک فایل JSONL خارج از ریشهٔ عمومی وب.
 * تغییری در پایگاه دادهٔ SQLite تولیدی ایجاد نمی‌کند.
 */
final class SupportReportService
{
    private string $path;

    public function __construct()
    {
        $this->path = (string) config('services.support.reports_path', storage_path('app/private/support-reports.jsonl'));
    }

    public function append(array $report): void
    {
        $dir = dirname($this->path);
        if (! is_dir($dir)) {
            @mkdir($dir, 0750, true);
        }

        $line = json_encode($report, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        if ($line === false || file_put_contents($this->path, $line.PHP_EOL, FILE_APPEND | LOCK_EX) === false) {
            throw new \RuntimeException('support report could not be stored');
        }
    }

    /** @return array<int, array<string, mixed>> جدیدترین‌ها اول */
    public function latest(int $limit = 50): array
    {
        if (! is_file($this->path) || ! is_readable($this->path)) {
            return [];
        }

        $lines = @file($this->path, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) ?: [];
        $rows = [];
        foreach (array_reverse($lines) as $line) {
            $row = json_decode($line, true);
            if (is_array($row)) {
                $rows[] = $row;
            }
            if (count($rows) >= $limit) {
                break;
            }
        }

        return $rows;
    }

    public function count(): int
    {
        if (! is_file($this->path)) {
            return 0;
        }

        return count(@file($this->path, FILE_SKIP_EMPTY_LINES) ?: []);
    }

    /** تبدیل ارقام فارسی/عربی به لاتین و حذف جداکننده‌ها */
    public static function normalizeDigits(?string $value): string
    {
        $value = (string) $value;
        $value = strtr($value, [
            '۰' => '0', '۱' => '1', '۲' => '2', '۳' => '3', '۴' => '4', '۵' => '5', '۶' => '6', '۷' => '7', '۸' => '8', '۹' => '9',
            '٠' => '0', '١' => '1', '٢' => '2', '٣' => '3', '٤' => '4', '٥' => '5', '٦' => '6', '٧' => '7', '٨' => '8', '٩' => '9',
        ]);

        return preg_replace('/[^0-9]/', '', $value) ?? '';
    }
}
