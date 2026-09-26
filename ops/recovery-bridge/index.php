<?php
declare(strict_types=1);

$static = __DIR__ . '/matnyaab.ir.html';
if (is_file($static)) {
    header('Content-Type: text/html; charset=utf-8');
    readfile($static);
    exit;
}

header('Content-Type: text/html; charset=utf-8');
?><!doctype html>
<html lang="fa" dir="rtl">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>متن‌یاب</title></head>
<body style="font-family:Tahoma,sans-serif;max-width:800px;margin:4rem auto;padding:1rem">
<h1>متن‌یاب</h1>
<p>سرویس متن‌یاب در حال انتقال فنی است. سرویس‌های برنامه در دسترس هستند.</p>
</body></html>
