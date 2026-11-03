<?php

namespace App\Http\Controllers;

use Illuminate\View\View;

class HomeController extends Controller
{
    public function __invoke(): View
    {
        $faq = [
            ['q' => 'آیا متن‌یاب واقعاً رایگان است؟', 'a' => 'بله. متن‌یاب ۲ با همهٔ امکانات و بدون هیچ محدودیت حجمی یا زمانی رایگان است. اگر برنامه به کارتان آمد، می‌توانید به‌دلخواه از توسعهٔ آن حمایت کنید.'],
            ['q' => 'متن‌یاب در چه فایل‌هایی جست‌وجو می‌کند؟', 'a' => 'Word (doc و docx)، PDF، پاورپوینت، اکسل، OneNote، RTF، ODT، EPUB، HTML، XML و فایل‌های متنی. PDFهایی که فقط تصویر اسکن‌شده‌اند متن قابل جست‌وجو ندارند.'],
            ['q' => 'آیا فایل‌های من به اینترنت ارسال می‌شود؟', 'a' => 'خیر. فهرست‌گیری و جست‌وجو کاملاً روی رایانهٔ خودتان انجام می‌شود و اسناد شما جایی ارسال نمی‌شوند.'],
            ['q' => 'برای اجرا به نصب یا ثبت‌نام نیاز است؟', 'a' => 'خیر. فایل ZIP را از حالت فشرده خارج کنید و Matnyaab.exe را اجرا کنید. ثبت‌نام و سریال لازم نیست.'],
            ['q' => 'به چه سیستمی نیاز دارم؟', 'a' => 'ویندوز ۱۰ یا ۱۱ نسخهٔ ۶۴ بیتی. برای کتابخانه‌های بسیار بزرگ، حافظهٔ رم بیشتر سرعت فهرست‌گیری را بالا می‌برد.'],
            ['q' => 'پیش‌تر اشتراک خریده‌ام؛ تکلیف چیست؟', 'a' => 'از اعتماد شما سپاسگزاریم. سوابق خرید در حساب کاربری‌تان محفوظ است و نسخهٔ جدید بدون نیاز به سریال، همهٔ امکانات را در اختیارتان می‌گذارد.'],
            ['q' => 'چطور از متن‌یاب حمایت کنم؟', 'a' => 'مبلغ دلخواه را به کارت اعلام‌شده در بخش «حمایت» واریز کنید و در صورت تمایل، واریز را با فرم همان بخش اعلام کنید.'],
        ];

        $site = url('/');
        $jsonLd = [
            '@context' => 'https://schema.org',
            '@graph' => [
                [
                    '@type' => 'SoftwareApplication',
                    'name' => 'متن‌یاب',
                    'alternateName' => 'Matnyaab',
                    'softwareVersion' => '2.0',
                    'operatingSystem' => 'Windows 10, Windows 11',
                    'applicationCategory' => 'UtilitiesApplication',
                    'inLanguage' => 'fa',
                    'description' => 'جست‌وجوی سریع درون متن فایل‌های Word، PDF، پاورپوینت، اکسل و OneNote روی رایانهٔ خودتان؛ رایگان و بدون محدودیت.',
                    'url' => $site,
                    'downloadUrl' => $site.'/downloadFiles/Matnyaab-2.0-win-x64.zip',
                    'image' => $site.'/assets/matnyaab-v2/icon-256.png',
                    'screenshot' => [
                        $site.'/assets/matnyaab-v2/shot-main-blue.webp',
                        $site.'/assets/matnyaab-v2/shot-main-night.webp',
                        $site.'/assets/matnyaab-v2/shot-main-paper.webp',
                    ],
                    'offers' => ['@type' => 'Offer', 'price' => '0', 'priceCurrency' => 'IRR'],
                    'isAccessibleForFree' => true,
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

        return view('home', ['faq' => $faq, 'jsonLd' => $jsonLd]);
    }
}
