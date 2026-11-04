<?php

namespace App\Http\Controllers;

use App\Services\SiteSettings;
use Illuminate\View\View;

class HomeController extends Controller
{
    public function __invoke(SiteSettings $settings): View
    {
        $plans = $settings->plans();
        $fa = fn ($n) => strtr(number_format($n), ['0'=>'۰','1'=>'۱','2'=>'۲','3'=>'۳','4'=>'۴','5'=>'۵','6'=>'۶','7'=>'۷','8'=>'۸','9'=>'۹',','=>'٬']);
        $planText = implode('، ', array_map(fn ($p) => $p['title'].' '.$fa($p['price']).' تومان', $plans)).'.';
        $faq = [
            ['q' => 'هزینهٔ اشتراک متن‌یاب چقدر است؟', 'a' => $planText.' پرداخت کارت‌به‌کارت است و کد اشتراک همان لحظه داده می‌شود.'],
            ['q' => '«اعتمادمحور» یعنی چه؟', 'a' => 'ما پرداخت‌ها را بررسی نمی‌کنیم. شما پلن و پرداخت‌تان را خوداظهاری می‌کنید، کد می‌گیرید و انتخاب درست را به وجدان شما می‌سپاریم. اگر فعلاً امکان پرداخت ندارید، در برنامه گزینهٔ «سه روز مهمانی» هست.'],
            ['q' => 'متن‌یاب در چه فایل‌هایی جست‌وجو می‌کند؟', 'a' => 'Word (doc و docx)، PDF، پاورپوینت، اکسل، OneNote، RTF، ODT، EPUB، HTML، XML و فایل‌های متنی. PDFهایی که فقط تصویر اسکن‌شده‌اند متن قابل جست‌وجو ندارند.'],
            ['q' => 'آیا فایل‌های من به اینترنت ارسال می‌شود؟', 'a' => 'خیر. فهرست‌گیری و جست‌وجو کاملاً روی رایانهٔ خودتان انجام می‌شود و اسناد شما جایی ارسال نمی‌شوند.'],
            ['q' => 'چطور نصب کنم؟', 'a' => 'نصب‌کنندهٔ متن‌یاب را دانلود و اجرا کنید؛ نیازی به دسترسی مدیر ویندوز نیست و میان‌بر برنامه ساخته می‌شود.'],
            ['q' => 'به چه سیستمی نیاز دارم؟', 'a' => 'ویندوز ۱۰ یا ۱۱ نسخهٔ ۶۴ بیتی. برای کتابخانه‌های بسیار بزرگ، حافظهٔ رم بیشتر سرعت فهرست‌گیری را بالا می‌برد.'],
            ['q' => 'پیش‌تر اشتراک خریده‌ام؛ تکلیف چیست؟', 'a' => 'از اعتماد شما سپاسگزاریم. سوابق خرید در حساب کاربری‌تان محفوظ است؛ برای نسخهٔ ۲ کافی است از صفحهٔ ثبت اشتراک، کد بگیرید یا از بخش پشتیبانی پیام بدهید.'],
            ['q' => 'چطور اشتراکم را تمدید کنم؟', 'a' => 'در برنامه روی نشان اشتراک بزنید و «تمدید در سایت» را انتخاب کنید؛ با همان کد قبلی، دورهٔ تازه ثبت می‌شود و روزشمار برنامه خودکار به‌روز می‌شود.'],
        ];

        $site = url('/');
        $jsonLd = [
            '@context' => 'https://schema.org',
            '@graph' => [
                [
                    '@type' => 'SoftwareApplication',
                    'name' => 'متن‌یاب',
                    'alternateName' => 'Matnyaab',
                    'operatingSystem' => 'Windows 10, Windows 11',
                    'applicationCategory' => 'UtilitiesApplication',
                    'inLanguage' => 'fa',
                    'description' => 'جست‌وجوی سریع درون متن فایل‌های Word، PDF، پاورپوینت، اکسل و OneNote روی رایانهٔ خودتان؛ با اشتراک اعتمادمحور.',
                    'url' => $site,
                    'downloadUrl' => $site.'/downloadFiles/Matnyaab-Setup-2.1.exe',
                    'softwareVersion' => '2.1',
                    'image' => $site.'/assets/matnyaab-v2/icon-256.png',
                    'screenshot' => [
                        $site.'/assets/matnyaab-v2/shot-main-blue.webp',
                        $site.'/assets/matnyaab-v2/shot-main-night.webp',
                        $site.'/assets/matnyaab-v2/shot-main-paper.webp',
                    ],
                    'offers' => array_map(fn ($p) => ['@type' => 'Offer', 'name' => 'اشتراک '.$p['title'], 'price' => (string) ($p['price'] * 10), 'priceCurrency' => 'IRR'], $plans),
                ],
                [
                    '@type' => 'WebSite',
                    'name' => 'متن‌یاب',
                    'url' => $site,
                    'inLanguage' => 'fa-IR',
                ],
                [
                    '@type' => 'FAQPage',
                    'mainEntity' => array_map(fn (array $item): array => [
                        '@type' => 'Question',
                        'name' => $item['q'],
                        'acceptedAnswer' => ['@type' => 'Answer', 'text' => $item['a']],
                    ], $faq),
                ],
            ],
        ];

        return view('home', ['faq' => $faq, 'jsonLd' => $jsonLd, 'settings' => $settings->all(), 'plans' => $plans, 'cards' => $settings->cards()]);
    }
}
