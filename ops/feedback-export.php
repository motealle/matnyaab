<?php
declare(strict_types=1);
// خروجی یک‌بارمصرف پیشنهادهای قابلیت (بدون نام و راه تماس) برای مخزن؛ پس از اجرا خودش را پاک می‌کند.
header('Content-Type: application/json; charset=utf-8');
$expected = '__EXPORT_TOKEN__';
$provided = (string) ($_SERVER['HTTP_X_MATNYAAB_EXPORT_TOKEN'] ?? '');
if ($expected === '' || strlen($expected) < 32 || !hash_equals($expected, $provided)) {
    http_response_code(403);
    echo json_encode(['ok' => false, 'error' => 'forbidden']);
    exit;
}
$self = __FILE__;
register_shutdown_function(static function () use ($self) { @unlink($self); });

$file = dirname(__DIR__) . '/matnyaab-laravel-stage/shared/data/inbox.json';
$all = is_file($file) ? json_decode((string) file_get_contents($file), true) : [];
if (!is_array($all)) { $all = []; }

function redact(string $t): string {
    $t = preg_replace('/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/iu', '[ایمیل]', $t) ?? $t;
    $t = preg_replace('/(\+?98|0)?[\s-]*9[\d۰-۹]{2}[\s-]*[\d۰-۹]{3}[\s-]*[\d۰-۹]{4}/u', '[شماره]', $t) ?? $t;
    $t = preg_replace('/[\d۰-۹]{8,}/u', '[عدد]', $t) ?? $t;
    return mb_substr(trim($t), 0, 3000);
}

$out = [];
foreach ($all as $m) {
    if (!is_array($m) || ($m['type'] ?? '') !== 'feature') { continue; }
    $out[] = [
        'id' => (string) ($m['id'] ?? ''),
        'at' => (string) ($m['at'] ?? ''),
        'source' => (string) ($m['source'] ?? 'site'),
        'app_version' => (string) ($m['app_version'] ?? ''),
        'status' => (string) ($m['status'] ?? 'new'),
        'message' => redact((string) ($m['message'] ?? '')),
    ];
}
echo json_encode(['ok' => true, 'count' => count($out), 'requests' => $out], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
