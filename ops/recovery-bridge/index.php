<?php
declare(strict_types=1);

$laravel = __DIR__ . '/laravel.php';
if (is_file($laravel)) {
    require $laravel;
    exit;
}

header('Content-Type: text/html; charset=utf-8');
http_response_code(503);
?><!doctype html>
<html lang="fa" dir="rtl">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>متن‌یاب</title></head>
<body style="font-family:Tahoma,sans-serif;max-width:800px;margin:4rem auto;padding:1rem">
<h1>متن‌یاب</h1>
<p>سرویس وب در حال راه‌اندازی است. لطفاً چند دقیقه دیگر دوباره تلاش کنید.</p>
</body></html>
