<?php

use App\Http\Controllers\AuthController;
use App\Http\Controllers\LegacyApiController;
use App\Http\Controllers\ProfileController;
use Illuminate\Support\Facades\Route;

Route::get('/', function () {
    return response(
        '<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><title>متن‌یاب</title><body style="font-family:Tahoma,sans-serif;max-width:800px;margin:4rem auto;padding:1rem"><h1>متن‌یاب</h1><p><a href="/login">ورود به حساب کاربری</a></p><p>نسخه جدید سرویس در حال استقرار مرحله‌ای است.</p></body></html>',
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

Route::middleware('guest')->group(function () {
    Route::get('login', [AuthController::class, 'showLogin'])->name('login');
    Route::post('login', [AuthController::class, 'login'])->name('login.submit');
});

Route::middleware('auth')->group(function () {
    Route::get('profile', ProfileController::class)->name('profile');
    Route::match(['get', 'post'], 'logout', [AuthController::class, 'logout'])->name('logout');
});
