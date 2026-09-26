<?php

use App\Http\Controllers\LegacyApiController;
use Illuminate\Support\Facades\Route;

Route::get('/', function () {
    return response(
        '<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><title>متن‌یاب</title><body style="font-family:Tahoma,sans-serif;max-width:800px;margin:4rem auto;padding:1rem"><h1>متن‌یاب</h1><p>نسخه جدید سرویس در حال استقرار مرحله‌ای است.</p></body></html>',
        200,
        ['Content-Type' => 'text/html; charset=utf-8']
    );
});

Route::get('health', [LegacyApiController::class, 'health']);
Route::get('news', [LegacyApiController::class, 'news']);
Route::get('get_contents', [LegacyApiController::class, 'getContents']);
Route::post('statistics', [LegacyApiController::class, 'statistics']);
Route::get('download_content', [LegacyApiController::class, 'downloadContent']);
Route::get('download_content_img', [LegacyApiController::class, 'downloadContentImage']);
