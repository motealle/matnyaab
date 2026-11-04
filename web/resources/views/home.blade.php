@extends('layouts.app')

@section('title', 'متن‌یاب | جستجوی حرفه‌ای در محتوای فایل‌ها؛ اشتراک اعتمادمحور برای ویندوز')
@section('meta_description', 'متن‌یاب ۲ با اشتراک اعتمادمحور (از ماهی ۴۱ هزار تومان): جست‌وجوی سریع درون متن فایل‌های Word، PDF، پاورپوینت، اکسل و OneNote روی رایانهٔ خودتان؛ مناسب پژوهشگران، طلاب، دانشجویان و کتابخانه‌های شخصی.')

@push('styles')
<link rel="stylesheet" href="/assets/matnyaab-hero-v1/hero.css?v=20260928-mobile">
<link rel="preload" as="image" href="/assets/matnyaab-v2/shot-main-blue.webp">
<style>
    .product-stage{margin:0;border:1px solid var(--line);border-radius:22px;padding:12px;background:var(--surface);box-shadow:var(--shadow)}
    .real-shot{border-radius:14px;overflow:hidden;background:var(--surface-2)}
    .real-shot img{display:block;width:100%;height:auto;object-fit:contain}
    .stage-caption{padding:14px 8px 2px;font-size:12px;color:var(--muted)}
    .product-overview{display:grid;grid-template-columns:.8fr 1.2fr;align-items:center;gap:60px}
    .product-overview h2{font-weight:300;font-size:34px;line-height:1.7;margin:12px 0}
    .product-overview p{color:var(--muted);font-size:14px;line-height:2.1}
    @media(max-width:800px){.product-overview{grid-template-columns:1fr;gap:26px}}
    .section{padding:72px 0}.section-head{display:flex;justify-content:space-between;align-items:end;gap:24px;margin-bottom:28px}
    .section-head h2{font-size:34px;font-weight:400;line-height:1.5;letter-spacing:-.7px;margin:5px 0 0}.section-head p{max-width:530px;color:var(--muted);margin:0;font-size:14px;font-weight:300}
    .feature-grid{display:flex;flex-wrap:wrap;justify-content:center;gap:13px}.feature-grid>*{flex:1 1 280px;max-width:420px;min-width:0}
    .feature-card{min-height:215px;padding:22px;border-radius:18px;border:1px solid var(--line);background:linear-gradient(155deg,var(--soft-card),transparent);transition:.25s ease;position:relative;overflow:hidden}
    .feature-card:hover{transform:translateY(-3px);border-color:rgba(73,155,224,.24)}
    .feature-icon{width:42px;height:42px;border-radius:12px;display:grid;place-items:center;background:rgba(73,155,224,.08);border:1px solid rgba(73,155,224,.14);margin-bottom:34px}
    .feature-icon svg{width:20px;height:20px;stroke:var(--accent-soft);fill:none;stroke-width:1.7}
    .feature-card h3{font-size:17px;font-weight:550;margin:0 0 5px}.feature-card p{font-size:13px;font-weight:300;color:var(--muted);margin:0;line-height:1.95}

    .workflow{display:flex;flex-wrap:wrap;justify-content:center;gap:14px;counter-reset:step}.workflow-card{border-top:1px solid var(--line-strong);padding-top:20px;counter-increment:step}
    .workflow-card:before{content:"0" counter(step);font-size:11px;color:var(--accent);font-weight:700;letter-spacing:.12em}
    .workflow-card h3{margin:8px 0 5px;font-size:17px;font-weight:550}.workflow-card p{margin:0;color:var(--muted);font-size:13px;font-weight:300}

    .download-panel{
        border:1px solid rgba(73,155,224,.17);border-radius:24px;padding:32px;
        background:radial-gradient(600px 300px at 100% 0,rgba(26,104,181,.12),transparent 60%),linear-gradient(145deg,var(--surface),var(--surface-2))
    }
    .download-panel h2{margin:0 0 6px;font-size:29px;font-weight:450}.download-panel p{margin:0;color:var(--muted);font-size:14px;font-weight:300}
    .download-note{display:flex;gap:18px;flex-wrap:wrap;color:var(--muted-2);font-size:11px;margin-top:14px}.download-note span:before{content:"✓";color:var(--green);margin-left:6px}
    .release-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:24px}
    .release-card{padding:20px;border:1px solid var(--line);border-radius:16px;background:var(--soft-card);display:flex;flex-direction:column;gap:12px}
    .release-card h3{margin:0;font-size:18px}.release-card p{font-size:12px;line-height:1.95}
    .release-meta{display:grid;gap:5px;color:var(--muted-2);font-size:11px}
    .release-meta strong{color:var(--text);font-weight:650}
    .release-card .btn{margin-top:auto}

    .account-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.account-card{padding:24px;border:1px solid var(--line);border-radius:18px;background:var(--surface)}
    .account-card h3{margin:0 0 7px;font-weight:550}.account-card p{color:var(--muted);font-size:13px;font-weight:300;margin:0 0 18px}


    .whats-new{border:1px solid rgba(73,155,224,.2);border-radius:26px;padding:30px;background:radial-gradient(700px 320px at 0 0,rgba(26,104,181,.14),transparent 60%),linear-gradient(160deg,var(--surface),var(--surface-2))}
    .new-badge{display:inline-flex;align-items:center;gap:6px;padding:4px 12px;border-radius:999px;background:rgba(52,211,153,.12);color:var(--green);font-size:12px;font-weight:700;border:1px solid rgba(52,211,153,.25)}
    .new-badge:before{content:"";width:7px;height:7px;border-radius:50%;background:var(--green);box-shadow:0 0 10px var(--green)}
    .whats-new h2{font-size:34px;font-weight:500;margin:12px 0 6px;line-height:1.5}
    .whats-new .lead{color:var(--muted);font-size:15px;max-width:720px;margin:0 0 22px}
    .new-grid{display:flex;flex-wrap:wrap;justify-content:center;gap:12px;margin-bottom:26px}.new-grid>*{flex:1 1 260px;min-width:0}
    .new-item{padding:16px 18px;border-radius:16px;border:1px solid var(--line);background:var(--soft-card)}
    .new-item h3{margin:0 0 4px;font-size:15px;font-weight:650}.new-item p{margin:0;color:var(--muted);font-size:13px;line-height:1.9}
    .gallery{display:grid;gap:12px}
    .gallery-tabs{display:flex;gap:8px;flex-wrap:wrap}
    .gallery-tabs button{font:inherit;font-size:13px;padding:7px 14px;border-radius:999px;border:1px solid var(--line-strong);background:var(--button-bg);color:var(--text);cursor:pointer}
    .gallery-tabs button[aria-selected="true"]{background:var(--accent);border-color:var(--accent);color:#fff}
    .gallery-tabs button:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
    .gallery-frame{margin:0;border:1px solid var(--line);border-radius:18px;padding:10px;background:var(--surface);box-shadow:var(--shadow)}
    .gallery-frame img{display:block;width:100%;height:auto;border-radius:10px}
    .gallery-frame figcaption{padding:10px 6px 2px;color:var(--muted);font-size:13px}
    .gallery-frame[hidden]{display:none}
    .hero-free{display:inline-block;margin-top:6px;font-size:13px;color:var(--green);font-weight:600}

    .download-main{display:grid;grid-template-columns:1.2fr .8fr;gap:22px;align-items:center;margin-top:22px}
    .download-main .release-card{border-color:rgba(73,155,224,.3);background:linear-gradient(160deg,rgba(26,104,181,.10),transparent)}
    .download-main .btn-primary{font-size:16px;padding:14px 22px}
    .classic-card{display:flex;gap:14px;align-items:center;padding:14px;border:1px dashed var(--line-strong);border-radius:16px;font-size:12px;color:var(--muted)}
    .classic-card img{width:120px;height:auto;border-radius:8px;border:1px solid var(--line)}
    .classic-card a{color:var(--accent-soft)}
    .req-list{display:flex;flex-wrap:wrap;gap:8px 16px;color:var(--muted-2);font-size:12px;margin:0;padding:0;list-style:none}
    .req-list li:before{content:"✓";color:var(--green);margin-left:6px}

    .support-grid{display:grid;grid-template-columns:.9fr 1.1fr;gap:20px;align-items:start}
    .support-steps{list-style:persian;margin:0 0 18px;padding-right:22px;color:var(--muted);font-size:14px;line-height:2.1}
    .support-steps strong{color:var(--text)}
    .bank-card{position:relative;display:grid;gap:6px;padding:24px;border-radius:20px;color:#fff;overflow:hidden;
        background:radial-gradient(260px 160px at 90% 10%,rgba(255,255,255,.18),transparent 70%),linear-gradient(135deg,#0b4f8a,#1d7fd1 60%,#2ea0e6);
        box-shadow:0 22px 60px rgba(11,79,138,.35)}
    .bank-card-label{font-size:12px;opacity:.8}
    .bank-card-number{font-size:clamp(20px,4.6vw,28px);font-weight:700;letter-spacing:.08em;font-family:"Vazirmatn",Consolas,monospace;text-align:left}
    .bank-card-holder{font-size:15px;font-weight:600}
    .bank-card-copy{justify-self:start;margin-top:8px;background:rgba(255,255,255,.16);border-color:rgba(255,255,255,.35);color:#fff}
    .bank-card-copy:hover{background:rgba(255,255,255,.26)}
    .account-note{margin-top:16px;padding:14px 16px;border:1px solid var(--line);border-radius:14px;font-size:13px;color:var(--muted);display:grid;gap:3px}
    .account-note strong{color:var(--text)}.account-note a{color:var(--accent-soft)}
    .support-form h3{margin:0 0 2px;font-size:19px}.support-form .muted{font-size:12px;margin:0 0 14px;color:var(--muted)}
    .form-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px 12px;margin-bottom:14px}
    .form-wide{grid-column:1/-1}
    .support-form textarea.input{resize:vertical;min-height:84px;font-family:inherit}
    .field-error{display:block;color:var(--red);font-size:12px;margin-top:3px}
    .field-label small{color:var(--muted-2);font-weight:400}
    .hp-field{position:absolute!important;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}

    .price-grid{display:flex;flex-wrap:wrap;justify-content:center;gap:16px}.price-grid>.price-card{flex:1 1 240px;max-width:340px;min-width:0}
    .price-card{position:relative;padding:22px;border:1px solid var(--line);border-radius:20px;background:var(--surface);display:grid;gap:8px}
    .price-card.is-best{border-color:rgba(52,211,153,.45);box-shadow:0 18px 50px rgba(16,185,129,.12)}
    .price-card .new-badge{position:absolute;top:-12px;right:18px;background:var(--surface)}
    .price-card h3{margin:0;font-size:18px}.price b{font-size:32px;font-weight:800}.price span{color:var(--muted);font-size:13px}
    .price-card p{margin:0;color:var(--muted);font-size:13px;min-height:44px}.price-card .btn{align-self:end}.price-card{align-content:start}
    .pay-wrap{display:grid;grid-template-columns:1.1fr .9fr;gap:22px;align-items:start;margin-top:28px}
    .card-row{display:flex;flex-wrap:wrap;justify-content:center;gap:16px;margin-top:22px}.card-row>.bank-card{flex:1 1 300px;max-width:440px;min-width:0}
    .support-form{max-width:760px;margin-inline:auto}
    .type-pick{display:inline-flex;gap:6px;align-items:center;padding:7px 14px;border:1px solid var(--line-strong);border-radius:999px;cursor:pointer;font-size:14px}
    .type-pick:has(input:checked){border-color:var(--accent);background:rgba(88,176,237,.12)}
    .faq{display:grid;gap:10px}
    .faq details{border:1px solid var(--line);border-radius:14px;padding:14px 18px;background:var(--soft-card)}
    .faq summary{cursor:pointer;font-weight:600;font-size:15px}
    .faq details p{margin:8px 0 0;color:var(--muted);font-size:14px}
    @media(max-width:900px){.pay-wrap{grid-template-columns:1fr}.download-main,.support-grid{grid-template-columns:1fr}}
    @media(max-width:640px){.form-grid{grid-template-columns:1fr}.whats-new{padding:22px}.whats-new h2{font-size:26px}.classic-card{flex-direction:column;align-items:flex-start}}
    @media(max-width:900px){
        .hero-grid{grid-template-columns:1fr;gap:42px}.product-stage{max-width:680px}.section-head{align-items:start;flex-direction:column}
    }
    @media(max-width:640px){
        .hero{padding-top:55px}.hero-title{letter-spacing:-1px}.feature-grid,.workflow,.account-grid{grid-template-columns:1fr}
        .download-panel{padding:23px}.release-grid{grid-template-columns:1fr}.stage-caption{left:10px;bottom:10px}.real-shot{min-height:230px}
    }
    @media(prefers-reduced-motion:reduce){
        .type-caret{animation:none}.real-shot:after{animation:none}.hero-copy,.hero-actions,.hero-meta,.product-stage,.type-line{opacity:1;transform:none;animation:none}
    }

    /* ===== سیستم چیدمان یکپارچه: یک عرض محتوا، ستون‌های صریح، بدون ردیف یتیم ===== */
    :root{--max:1180px}
    @media(min-width:1800px){:root{--max:1360px}.section{padding:96px 0}}
    .section-head{display:block;max-width:none;margin-bottom:32px}
    .section-head p{max-width:720px;margin:12px 0 0}
    .feature-grid,.new-grid,.workflow,.price-grid{display:grid;gap:16px;justify-content:stretch}
    .feature-grid>*,.new-grid>*,.price-grid>.price-card{flex:none;max-width:none;min-width:0}
    .feature-grid,.new-grid{grid-template-columns:repeat(3,minmax(0,1fr))}
    .workflow{grid-template-columns:repeat(3,minmax(0,1fr))}
    .price-grid{grid-template-columns:repeat(var(--cols,3),minmax(0,1fr))}
    .pay-wrap{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px;align-items:stretch;margin-top:28px}
    .pay-cards{display:grid;gap:14px;align-content:start}.pay-cards .bank-card{max-width:none}
    .support-form{max-width:none;margin:0}
    .contact-grid{grid-template-columns:repeat(3,minmax(0,1fr))}
    @media(max-width:1099px){.price-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
    @media(max-width:999px){.feature-grid,.new-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.pay-wrap{grid-template-columns:1fr}.contact-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
    .download-main{align-items:start}
    @media(max-width:759px){.workflow{grid-template-columns:1fr}}
    @media(max-width:639px){.feature-grid,.new-grid,.price-grid,.contact-grid{grid-template-columns:1fr}}
</style>
@endpush

@section('content')
@if ($settings['flags']['announcement_enabled'] && $settings['texts']['announcement'] !== '')
<div class="container" style="margin-top:14px"><div class="alert alert-success" role="status">📣 {{ $settings['texts']['announcement'] }}</div></div>
@endif
@include('partials.home-hero')

<section class="section" id="whats-new">
    <div class="container">
        <div class="whats-new">
            <span class="new-badge">تازه منتشر شد</span>
            <h2>متن‌یاب ۲؛ کامل‌تر، سبک‌تر و زیباتر از همیشه</h2>
            <p class="lead">نسخهٔ تازهٔ متن‌یاب با نصب‌کنندهٔ ویندوزی و همهٔ امکانات، بدون قفل و سقف حجمی در دسترس است. ظاهر برنامه از نو طراحی شده، افزودن پوشه ساده‌تر شده و برای مطالعه‌های طولانی، پوسته‌های رنگی متنوعی به آن افزوده شده است.</p>

            <div class="new-grid">
                <div class="new-item"><h3>اشتراک اعتمادمحور</h3><p>بدون سریال و سقف حجمی؛ پلن را خودتان اعلام می‌کنید و روزشمار برنامه موعد تمدید را به شما یادآوری می‌کند.</p></div>
                <div class="new-item"><h3>پنج پوستهٔ رنگی</h3><p>آبی، زمردی، یاسی، «کاغذی» برای مطالعهٔ طولانی و «شب» برای کار در تاریکی؛ با یک کلیک.</p></div>
                <div class="new-item"><h3>افزودن پوشه در چند ثانیه</h3><p>نوع اسناد را با چند گزینهٔ ساده انتخاب کنید؛ پیشرفت کار و زمان تقریبی باقیمانده را ببینید.</p></div>
                <div class="new-item"><h3>کتابخانه‌ای مرتب</h3><p>هر مجموعه با تعداد فایل‌ها و حجم آن نمایش داده می‌شود؛ زیرپوشه‌ها جمع‌وجور و قابل گسترش‌اند.</p></div>
                <div class="new-item"><h3>خوانایی بیشتر</h3><p>قلم بزرگ‌تر، دکمه‌های واضح‌تر، نماد تازه و پیش‌نمایشی که واژه‌های جست‌وجوشده را برجسته می‌کند.</p></div>
                <div class="new-item"><h3>میان‌برهای سریع</h3><p><kbd>Ctrl</kbd>+<kbd>F</kbd> برای جست‌وجو و <kbd>Ctrl</kbd>+<kbd>O</kbd> برای افزودن پوشه، به‌همراه راهنمای کامل فارسی.</p></div>
            </div>

            <div class="gallery" data-gallery>
                <div class="gallery-tabs" role="tablist" aria-label="نماهای متن‌یاب ۲">
                    <button type="button" role="tab" aria-selected="true" aria-controls="shot-1" id="tab-1">جست‌وجو و پیش‌نمایش</button>
                    <button type="button" role="tab" aria-selected="false" aria-controls="shot-2" id="tab-2" tabindex="-1">پوستهٔ شب</button>
                    <button type="button" role="tab" aria-selected="false" aria-controls="shot-3" id="tab-3" tabindex="-1">پوستهٔ کاغذی</button>
                    <button type="button" role="tab" aria-selected="false" aria-controls="shot-4" id="tab-4" tabindex="-1">افزودن پوشه</button>
                </div>
                <figure class="gallery-frame" role="tabpanel" id="shot-1" aria-labelledby="tab-1">
                    <img src="/assets/matnyaab-v2/shot-main-blue.webp" width="1860" height="1170" alt="متن‌یاب ۲: نتایج جست‌وجوی واژهٔ عدالت در کتابخانهٔ پژوهشی و پیش‌نمایش متن با واژه‌های برجسته" decoding="async">
                    <figcaption>جست‌وجوی «عدالت» در یک کتابخانهٔ پژوهشی؛ نتایج بر پایهٔ ارتباط مرتب شده‌اند و متن سند با واژه‌های برجسته نمایش داده می‌شود.</figcaption>
                </figure>
                <figure class="gallery-frame" role="tabpanel" id="shot-2" aria-labelledby="tab-2" hidden>
                    <img src="/assets/matnyaab-v2/shot-main-night.webp" width="1860" height="1170" alt="متن‌یاب ۲ در پوستهٔ تیرهٔ شب همراه با درخت محدودهٔ جست‌وجو" loading="lazy" decoding="async">
                    <figcaption>پوستهٔ «شب» برای کار در محیط کم‌نور؛ درخت محدودهٔ جست‌وجو تعداد فایل‌های هر مجموعه را نشان می‌دهد.</figcaption>
                </figure>
                <figure class="gallery-frame" role="tabpanel" id="shot-3" aria-labelledby="tab-3" hidden>
                    <img src="/assets/matnyaab-v2/shot-main-paper.webp" width="1860" height="1170" alt="متن‌یاب ۲ در پوستهٔ کاغذی مناسب مطالعهٔ طولانی" loading="lazy" decoding="async">
                    <figcaption>پوستهٔ «کاغذی» با رنگ‌های گرم و آرام، برای ساعت‌ها مطالعه بدون خستگی چشم.</figcaption>
                </figure>
                <figure class="gallery-frame" role="tabpanel" id="shot-4" aria-labelledby="tab-4" hidden>
                    <img src="/assets/matnyaab-v2/shot-add-folder.webp" width="840" height="780" alt="پنجرهٔ افزودن پوشه در متن‌یاب ۲ با انتخاب نوع اسناد" loading="lazy" decoding="async" style="max-width:560px;margin-inline:auto">
                    <figcaption>افزودن پوشه: نام مجموعه، نوع اسناد و شروع؛ تنظیمات بیشتر فقط وقتی لازم باشد باز می‌شود.</figcaption>
                </figure>
            </div>
        </div>
    </div>
</section>

<section class="section" id="features">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">جست‌وجویی که به متن فایل می‌رسد</span><h2>برای آرشیوهایی که هر روز بزرگ‌تر می‌شوند</h2></div>
            <p>وقتی تعداد فایل‌ها زیاد می‌شود، نام فایل دیگر کافی نیست. متن‌یاب درون اسناد را جست‌وجو می‌کند تا سریع‌تر به مطلب موردنظر برسید.</p>
        </div>
        <div class="feature-grid">
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M13 2 4 14h7l-1 8 9-12h-7z"/></svg></div><h3>نتایج مرتبط، زودتر در دسترس</h3><p>نتایج بر پایهٔ میزان ارتباط مرتب می‌شوند و تعداد موارد یافت‌شده در هر سند نمایش داده می‌شود.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M6 2h8l4 4v16H6z"/><path d="M14 2v5h5"/></svg></div><h3>فرمت‌های متنوع</h3><p>Word، PDF، پاورپوینت، اکسل، OneNote، RTF، ODT، EPUB، HTML و متن ساده.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M4 5h16M4 12h10M4 19h7"/><circle cx="18" cy="17" r="3"/></svg></div><h3>جست‌وجوی پیشرفته</h3><p>«همهٔ این کلمات»، «هر یک از کلمات»، «بدون این کلمات» و فاصلهٔ مجاز میان واژه‌ها؛ با جست‌وجوی دقیق یا بخشی از واژه.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M4 7h6l2 2h8v10H4z"/><path d="M4 7V5h6l2 2"/></svg></div><h3>برای پژوهش و آرشیوهای شخصی</h3><p>برای کتابخانه‌های شخصی، پرونده‌های پژوهشی، جزوه‌ها و مجموعه‌هایی که پیدا کردن مطلب در آن‌ها زمان‌بر شده است.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M5 4h14v16H5z"/><path d="M8 8h8M8 12h8M8 16h5"/></svg></div><h3>بسته‌های محتوایی</h3><p>مجموعه‌های محتوایی آماده را از داخل برنامه دریافت کنید و در کنار فایل‌های خودتان جست‌وجو کنید.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg></div><h3>فایل‌ها نزد خودتان می‌مانند</h3><p>فهرست‌گیری و جست‌وجو کاملاً روی رایانهٔ شما انجام می‌شود؛ هیچ سندی به جایی ارسال نمی‌شود.</p></article>
        </div>
    </div>
</section>

<section class="section" id="workflow">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">از پوشه تا نتیجه</span><h2>سه قدم تا رسیدن به متن موردنظر</h2></div>
            <p>پس از آماده‌سازی اولیه، برای جست‌وجوهای بعدی نیازی نیست فایل‌ها را یکی‌یکی باز کنید.</p>
        </div>
        <div class="workflow">
            <div class="workflow-card"><h3>پوشه را اضافه کنید</h3><p>آرشیو، کتابخانه یا پوشهٔ پروژه‌ای که می‌خواهید جست‌وجوپذیر شود.</p></div>
            <div class="workflow-card"><h3>متن فایل‌ها آماده می‌شود</h3><p>متن‌یاب محتوای فایل‌ها را یک بار می‌خواند تا جست‌وجوهای بعدی در کسری از ثانیه انجام شوند.</p></div>
            <div class="workflow-card"><h3>چند واژه را جست‌وجو کنید</h3><p>نتیجه‌های مرتبط را ببینید، در متن میان موارد یافت‌شده جابه‌جا شوید و فایل اصلی را باز کنید.</p></div>
        </div>
    </div>
</section>

<section class="section" id="download">
    <div class="container">
        <div class="download-panel">
            <span class="eyebrow">دانلود</span>
            <h2>متن‌یاب ۲ را دریافت کنید</h2>
            <p>نصب‌کنندهٔ ویندوز را دریافت کنید، با چند کلیک نصب کنید و با کد اشتراک یا سه روز مهمانی شروع کنید.</p>

            <div class="download-main">
                <article class="release-card">
                    <div><span class="new-badge">نسخهٔ ۲٫۰</span><h3 style="margin-top:10px">متن‌یاب ۲ برای ویندوز</h3></div>
                    <p data-release-content="kotlin">نصب‌کننده را اجرا کنید؛ میان‌بر متن‌یاب روی میز کار و منوی شروع ساخته می‌شود.</p>
                    <div class="release-meta">
                        <span><strong>نوع فایل:</strong> <span data-release-delivery="kotlin">نصب‌کنندهٔ ویندوز ۱۰ و ۱۱ (۶۴ بیتی)</span></span>
                        <span><strong>اندازه:</strong> <span data-release-size="kotlin">در حال دریافت اطلاعات…</span></span>
                    </div>
                    <a class="btn btn-primary" data-release-link="kotlin" href="/downloadFiles/Matnyaab-Setup-2.2.exe">دانلود نصب‌کنندهٔ متن‌یاب ۲ <span aria-hidden="true">↓</span></a>
                    <small class="muted" style="color:var(--muted-2)">نسخهٔ بدون نصب: <a data-release-portable href="/downloadFiles/Matnyaab-2.2-win-x64-portable.zip" style="color:var(--accent-soft)">فایل ZIP</a></small>
                </article>
                <div style="display:grid;gap:14px">
                    <ul class="req-list">
                        <li>ویندوز ۱۰ یا ۱۱، ۶۴ بیتی</li>
                        <li>نصب با چند کلیک</li>
                        <li>بدون سریال؛ با کد اشتراک</li>
                        <li>فارسی و راست‌به‌چپ</li>
                    </ul>
                    <div class="classic-card">
                        <img src="/static/sc1.png" alt="نمای نسخهٔ قدیمی متن‌یاب" loading="lazy" width="120" height="75">
                        <div>
                            <strong style="color:var(--text)">نسخهٔ قدیمی (کلاسیک)</strong><br>
                            اگر به نسخهٔ پیشین عادت دارید، همچنان در دسترس است: <a data-release-link="classic" href="/update/MATNYAAB_x64_setup.exe">دریافت نسخهٔ قدیمی</a>
                            <span data-release-size="classic" hidden></span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>

@include('partials.pricing')
@include('partials.contact')

<section class="section" id="faq">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">پرسش‌های پرتکرار</span><h2>پیش از دانلود بدانید</h2></div>
        </div>
        <div class="faq">
            @foreach ($faq as $item)
                <details><summary>{{ $item['q'] }}</summary><p>{{ $item['a'] }}</p></details>
            @endforeach
        </div>
    </div>
</section>
@endsection

@push('head')
<script type="application/ld+json">{!! json_encode($jsonLd, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_HEX_TAG) !!}</script>
@endpush

@push('scripts')
<script src="/assets/matnyaab-hero-v1/hero.js" defer></script>
<script>
    (() => {
        const formatBytes = bytes => {
            if (!Number.isFinite(bytes) || bytes <= 0) return 'نامشخص';
            const units = ['B', 'KB', 'MB', 'GB'];
            let value = bytes, unit = 0;
            while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
            return new Intl.NumberFormat('fa-IR', { maximumFractionDigits: 1 }).format(value) + ' ' + units[unit];
        };

        fetch('/downloadFiles/releases.json', { cache: 'no-store' })
            .then(response => response.ok ? response.json() : Promise.reject())
            .then(releases => {
                const kotlin = releases.kotlin;
                // فقط وقتی نسخهٔ ۲ واقعاً منتشر شده باشد، پیوند به‌روز شود
                if (kotlin && String(kotlin.version || '').startsWith('2')) {
                    const link = document.querySelector('[data-release-link="kotlin"]');
                    const size = document.querySelector('[data-release-size="kotlin"]');
                    const delivery = document.querySelector('[data-release-delivery="kotlin"]');
                    const content = document.querySelector('[data-release-content="kotlin"]');
                    if (link && kotlin.url) link.href = kotlin.url;
                    const portable = document.querySelector('[data-release-portable]');
                    if (portable && kotlin.portable_url) portable.href = kotlin.portable_url;
                    if (size && kotlin.size_bytes) size.textContent = formatBytes(Number(kotlin.size_bytes));
                    if (delivery && kotlin.delivery) delivery.textContent = kotlin.delivery;
                    if (content && kotlin.content_summary) content.textContent = kotlin.content_summary;
                }
                const classic = releases.classic;
                const classicLink = document.querySelector('[data-release-link="classic"]');
                if (classic && classicLink && classic.url) classicLink.href = classic.url;
            })
            .catch(() => {});

        // گالری تب‌دار: کلیک و کلیدهای جهت
        const gallery = document.querySelector('[data-gallery]');
        if (gallery) {
            const tabs = [...gallery.querySelectorAll('[role="tab"]')];
            const select = tab => {
                tabs.forEach(t => {
                    const on = t === tab;
                    t.setAttribute('aria-selected', String(on));
                    t.tabIndex = on ? 0 : -1;
                    document.getElementById(t.getAttribute('aria-controls')).hidden = !on;
                });
            };
            tabs.forEach((tab, i) => {
                tab.addEventListener('click', () => select(tab));
                tab.addEventListener('keydown', e => {
                    const dir = e.key === 'ArrowLeft' ? 1 : e.key === 'ArrowRight' ? -1 : 0;
                    if (!dir) return;
                    e.preventDefault();
                    const next = tabs[(i + dir + tabs.length) % tabs.length];
                    select(next); next.focus();
                });
            });
        }

        // کپی شماره کارت
        document.querySelectorAll('[data-copy-card]').forEach(btn => {
            btn.addEventListener('click', async () => {
                const num = btn.dataset.copyCard || '';
                try { await navigator.clipboard.writeText(num); btn.textContent = 'کپی شد ✓'; }
                catch (_) { btn.textContent = num; }
                setTimeout(() => { btn.textContent = 'کپی شماره کارت'; }, 2500);
            });
        });
    })();
</script>
@endpush
