<?php

use App\Http\Controllers\AdminController;
use App\Http\Controllers\AdminPanelController;
use App\Http\Controllers\ClientApiController;
use App\Http\Controllers\SubscribeController;
use App\Http\Middleware\AdminAccess;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\HomeController;
use App\Http\Controllers\LegacyApiController;
use App\Http\Controllers\ProfileController;
use App\Http\Controllers\PasswordRecoveryController;
use App\Http\Controllers\RegistrationController;
use App\Http\Controllers\SubscriptionController;
use App\Http\Controllers\SupportController;
use Illuminate\Support\Facades\Route;

Route::get('/', HomeController::class)->name('home');
Route::redirect('/index', '/#features');
Route::redirect('/download', '/#download');
Route::redirect('/screenshots', '/#whats-new');
Route::redirect('/contact', '/#support');

Route::get('health', [LegacyApiController::class, 'health']);
Route::get('news', [LegacyApiController::class, 'news']);
Route::match(['get', 'post'], 'get_contents', [LegacyApiController::class, 'getContents']);
Route::match(['get', 'post'], 'statistics', [LegacyApiController::class, 'statistics']);
Route::get('download_content', [LegacyApiController::class, 'downloadContent']);
Route::get('download_content_img', [LegacyApiController::class, 'downloadContentImage']);
Route::get('subscribe', [SubscribeController::class, 'show'])->name('subscribe');
Route::post('subscribe', [SubscribeController::class, 'store'])->middleware('throttle:8,10')->name('subscribe.store');
Route::get('subscribe/done', [SubscribeController::class, 'done'])->name('subscribe.done');
Route::post('contact', [SubscribeController::class, 'contact'])->middleware('throttle:4,10')->name('contact.store');
Route::prefix('api/client')->middleware('throttle:30,1')->group(function () {
    Route::get('config', [ClientApiController::class, 'configEndpoint']);
    Route::post('activate', [ClientApiController::class, 'activate']);
    Route::post('ping', [ClientApiController::class, 'ping']);
    Route::post('feedback', [ClientApiController::class, 'feedback'])->middleware('throttle:5,10');
});
Route::get('adminarea/login', [AdminPanelController::class, 'showLogin'])->name('admin.login');
Route::post('adminarea/login', [AdminPanelController::class, 'login'])->middleware('throttle:6,15')->name('admin.login.submit');
Route::middleware(AdminAccess::class)->prefix('adminarea')->group(function () {
    Route::get('/', [AdminPanelController::class, 'index'])->name('admin.dashboard');
    Route::get('account', [AdminPanelController::class, 'account'])->name('admin.account');
    Route::post('account', [AdminPanelController::class, 'accountSave'])->name('admin.account.save');
    Route::post('logout', [AdminPanelController::class, 'logout'])->name('admin.logout');
    Route::post('subscriptions', [AdminPanelController::class, 'subscriptionAction'])->name('admin.subs.action');
    Route::post('subscriptions/create', [AdminPanelController::class, 'subscriptionCreate'])->name('admin.subs.create');
    Route::post('inbox', [AdminPanelController::class, 'inboxAction'])->name('admin.inbox.action');
    Route::post('notices', [AdminPanelController::class, 'noticeCreate'])->name('admin.notices.create');
    Route::post('notices/action', [AdminPanelController::class, 'noticeAction'])->name('admin.notices.action');
    Route::post('purge', [AdminPanelController::class, 'purge'])->name('admin.purge');
    Route::post('settings', [AdminPanelController::class, 'settingsSave'])->name('admin.settings.save');
    Route::post('gift', [AdminController::class, 'gift'])->name('admin.gift');
    Route::post('license/global', [AdminController::class, 'updateGlobalLicensePolicy'])->name('admin.license.global');
    Route::post('license/user', [AdminController::class, 'updateUserLicensePolicy'])->name('admin.license.user');
});
Route::post('support', [SupportController::class, 'store'])->middleware('throttle:6,10')->name('support.store');
Route::get('support', fn () => redirect('/#support'));
Route::redirect('/donate', '/#support');
Route::get('robots.txt', fn () => response(
    "User-agent: *\nDisallow: /adminarea\nDisallow: /profile\nDisallow: /change_password\nDisallow: /restore/\n\nSitemap: ".url('/sitemap.xml')."\n",
    200, ['Content-Type' => 'text/plain; charset=utf-8']));
Route::get('sitemap.xml', function () {
    $urls = [
        ['loc' => url('/'), 'priority' => '1.0', 'changefreq' => 'weekly'],
        ['loc' => url('/subscribe'), 'priority' => '0.8', 'changefreq' => 'monthly'],
        ['loc' => url('/login'), 'priority' => '0.3', 'changefreq' => 'yearly'],
    ];
    return response()->view('sitemap', ['urls' => $urls, 'lastmod' => '2026-11-04'])
        ->header('Content-Type', 'application/xml; charset=utf-8');
});
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

    Route::get('smsconfirm', [RegistrationController::class, 'showSmsConfirm'])->name('smsconfirm');
    Route::post('smsconfirm', [RegistrationController::class, 'smsConfirm'])->name('smsconfirm.submit');
    Route::get('profile', ProfileController::class)->name('profile');
    Route::get('change_password', [AuthController::class, 'showChangePassword'])->name('password.change');
    Route::post('change_password', [AuthController::class, 'changePassword'])->name('password.change.submit');
    Route::post('buysubscription', [SubscriptionController::class, 'buySubscription'])->name('buysubscription');
    Route::match(['get', 'post'], 'logout', [AuthController::class, 'logout'])->name('logout');
});
