<?php

use App\Http\Controllers\AdminController;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\LegacyApiController;
use App\Http\Controllers\ProfileController;
use App\Http\Controllers\PasswordRecoveryController;
use App\Http\Controllers\RegistrationController;
use App\Http\Controllers\SubscriptionController;
use Illuminate\Support\Facades\Route;

Route::view('/', 'home')->name('home');
Route::redirect('/index', '/#features');
Route::redirect('/download', '/#download');
Route::redirect('/screenshots', '/#screenshots');
Route::redirect('/contact', '/#contact');

Route::get('health', [LegacyApiController::class, 'health']);
Route::get('news', [LegacyApiController::class, 'news']);
Route::get('get_contents', [LegacyApiController::class, 'getContents']);
Route::post('statistics', [LegacyApiController::class, 'statistics']);
Route::get('download_content', [LegacyApiController::class, 'downloadContent']);
Route::get('download_content_img', [LegacyApiController::class, 'downloadContentImage']);
Route::match(['get', 'post'], 'callback-gateway', [SubscriptionController::class, 'callbackGateway'])->name('callback_gateway');

Route::middleware('guest')->group(function () {
    Route::get('login', [AuthController::class, 'showLogin'])->name('login');
    Route::post('login', [AuthController::class, 'login'])->name('login.submit');
    Route::get('register', [RegistrationController::class, 'showRegister'])->name('register');
    Route::post('register', [RegistrationController::class, 'register'])->name('register.submit');

    Route::get('restore_password', [PasswordRecoveryController::class, 'showRequest'])->name('password.restore');
    Route::post('restore_password', [PasswordRecoveryController::class, 'send'])->name('password.restore.send');
    Route::get('restore_password_done', [PasswordRecoveryController::class, 'done'])->name('password.restore.done');
    Route::get('restore/{user}/{expires}/{token}', [PasswordRecoveryController::class, 'showConfirm'])->name('password.restore.confirm');
    Route::post('restore/{user}/{expires}/{token}', [PasswordRecoveryController::class, 'confirm'])->name('password.restore.confirm.submit');
});

Route::middleware('auth')->group(function () {
    Route::get('adminarea', [AdminController::class, 'index'])->name('admin.dashboard');
    Route::post('adminarea/gift', [AdminController::class, 'gift'])->name('admin.gift');

    Route::get('smsconfirm', [RegistrationController::class, 'showSmsConfirm'])->name('smsconfirm');
    Route::post('smsconfirm', [RegistrationController::class, 'smsConfirm'])->name('smsconfirm.submit');
    Route::get('profile', ProfileController::class)->name('profile');
    Route::get('change_password', [AuthController::class, 'showChangePassword'])->name('password.change');
    Route::post('change_password', [AuthController::class, 'changePassword'])->name('password.change.submit');
    Route::post('buysubscription', [SubscriptionController::class, 'buySubscription'])->name('buysubscription');
    Route::match(['get', 'post'], 'logout', [AuthController::class, 'logout'])->name('logout');
});
