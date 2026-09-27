@extends('layouts.app')

@section('title', 'متن‌یاب | جستجوی حرفه‌ای در محتوای فایل‌ها')
@section('meta_description', 'متن‌یاب؛ جستجوی سریع و محلی درون فایل‌های Word، PDF، PowerPoint، Excel، متن ساده و صفحات وب.')

@push('styles')
<link rel="stylesheet" href="/assets/matnyaab-hero-v1/hero.css">
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
    .feature-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:13px}
    .feature-card{min-height:215px;padding:22px;border-radius:18px;border:1px solid var(--line);background:linear-gradient(155deg,var(--soft-card),transparent);transition:.25s ease;position:relative;overflow:hidden}
    .feature-card:hover{transform:translateY(-3px);border-color:rgba(73,155,224,.24)}
    .feature-icon{width:42px;height:42px;border-radius:12px;display:grid;place-items:center;background:rgba(73,155,224,.08);border:1px solid rgba(73,155,224,.14);margin-bottom:34px}
    .feature-icon svg{width:20px;height:20px;stroke:var(--accent-soft);fill:none;stroke-width:1.7}
    .feature-card h3{font-size:17px;font-weight:550;margin:0 0 5px}.feature-card p{font-size:13px;font-weight:300;color:var(--muted);margin:0;line-height:1.95}

    .workflow{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;counter-reset:step}.workflow-card{border-top:1px solid var(--line-strong);padding-top:20px;counter-increment:step}
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

    @media(max-width:900px){
        .hero-grid{grid-template-columns:1fr;gap:42px}.product-stage{max-width:680px}.feature-grid{grid-template-columns:1fr 1fr}.section-head{align-items:start;flex-direction:column}
    }
    @media(max-width:640px){
        .hero{padding-top:55px}.hero-title{letter-spacing:-1px}.feature-grid,.workflow,.account-grid{grid-template-columns:1fr}
        .download-panel{padding:23px}.release-grid{grid-template-columns:1fr}.stage-caption{left:10px;bottom:10px}.real-shot{min-height:230px}
    }
    @media(prefers-reduced-motion:reduce){
        .type-caret{animation:none}.real-shot:after{animation:none}.hero-copy,.hero-actions,.hero-meta,.product-stage,.type-line{opacity:1;transform:none;animation:none}
    }
</style>
@endpush

@section('content')
@include('partials.home-hero')

<section class="section" id="screenshots">
    <div class="container product-overview">
        <div>
            <span class="eyebrow">از یادآوری تا یافتن</span>
            <h2>کتابخانه‌تان را<br>از نو کشف کنید.</h2>
            <p>نام فایل را به یاد ندارید؟ چند واژه از متن را جست‌وجو کنید. متن‌یاب درون فایل‌های شما می‌گردد و نتیجه‌های مرتبط را پیش رویتان می‌گذارد.</p>
            <a class="text-link" href="#workflow">آشنایی با روش جست‌وجو ←</a>
        </div>
        <figure class="product-stage">
            <div class="real-shot"><img src="/static/sc1.png" alt="محیط نرم‌افزار متن‌یاب و نتایج جست‌وجو" loading="lazy"></div>
            <figcaption class="stage-caption">نمای محیط متن‌یاب · جست‌وجو در نسخه ویندوز</figcaption>
        </figure>
    </div>
</section>

<section class="section" id="features">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">جست‌وجویی که به متن فایل می‌رسد</span><h2>برای آرشیوهایی که هر روز بزرگ‌تر می‌شوند</h2></div>
            <p>وقتی تعداد فایل‌ها زیاد می‌شود، نام فایل دیگر کافی نیست. متن‌یاب درون اسناد را جست‌وجو می‌کند تا سریع‌تر به مطلب موردنظر برسید.</p>
        </div>
        <div class="feature-grid">
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M13 2 4 14h7l-1 8 9-12h-7z"/></svg></div><h3>نتایج مرتبط، زودتر در دسترس</h3><p>نتایج بر پایه میزان ارتباط مرتب می‌شوند تا مسیر رسیدن به سند موردنظر کوتاه‌تر شود.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M6 2h8l4 4v16H6z"/><path d="M14 2v5h5"/></svg></div><h3>فرمت‌های متنوع</h3><p>Word، PDF، PowerPoint، Excel، TXT، HTML و فرمت‌های متنی متداول.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M4 5h16M4 12h10M4 19h7"/><circle cx="18" cy="17" r="3"/></svg></div><h3>جست‌وجو در پوشه‌ها و زیرپوشه‌ها</h3><p>پوشه‌ها و زیرپوشه‌ها را یک‌بار آماده کنید و بعد هر قدر خواستید در محتوای آن‌ها جست‌وجو کنید.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M4 7h6l2 2h8v10H4z"/><path d="M4 7V5h6l2 2"/></svg></div><h3>برای پژوهش و آرشیوهای شخصی</h3><p>برای کتابخانه‌های شخصی، پرونده‌های پژوهشی و مجموعه‌هایی که پیدا کردن مطلب در آن‌ها زمان‌بر شده است.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M5 4h14v16H5z"/><path d="M8 8h8M8 12h8M8 16h5"/></svg></div><h3>بسته‌های محتوایی</h3><p>مجموعه‌های محتوایی آماده را نیز می‌توانید در کنار فایل‌های خودتان جست‌وجو کنید.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8M12 17v4"/></svg></div><h3>کلاینت ویندوز</h3><p>جست‌وجو روی رایانه شما انجام می‌شود و فایل‌ها همان‌جا در اختیار خودتان می‌مانند.</p></article>
        </div>
    </div>
</section>

<section class="section" id="workflow">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">از پوشه تا نتیجه</span><h2>از انتخاب پوشه تا رسیدن به متن موردنظر</h2></div>
            <p>پس از آماده‌سازی اولیه، برای جست‌وجوهای بعدی نیازی نیست فایل‌ها را یکی‌یکی باز کنید.</p>
        </div>
        <div class="workflow">
            <div class="workflow-card"><h3>پوشه را انتخاب کنید</h3><p>آرشیو، کتابخانه یا پوشه پروژه‌ای که می‌خواهید قابل جستجو شود.</p></div>
            <div class="workflow-card"><h3>متن فایل‌ها آماده جست‌وجو می‌شود</h3><p>متن‌یاب محتوای قابل خواندن فایل‌ها را آماده می‌کند تا جست‌وجوهای بعدی سریع انجام شوند.</p></div>
            <div class="workflow-card"><h3>چند واژه‌ای را که به یاد دارید وارد کنید</h3><p>متن‌یاب میان فایل‌ها می‌گردد و نتیجه‌های مرتبط را پیش رویتان می‌گذارد.</p></div>
        </div>
    </div>
</section>

<section class="section" id="download">
    <div class="container">
        <div class="download-panel">
            <div>
                <span class="eyebrow">نسخه‌های ویندوز</span>
                <h2>نسخه مناسب خودتان را دریافت کنید</h2>
                <p>نسخه کلاسیک یا نسخه جدید متن‌یاب را برای رایانه ویندوزی خود دریافت کنید.</p>
                <div class="download-note"><span>ویندوز x64</span><span>هر دو نسخه قابل استفاده‌اند</span><span>بسته‌های محتوایی از داخل برنامه مدیریت می‌شوند</span></div>
            </div>

            <div class="release-grid">
                <article class="release-card">
                    <div><span class="eyebrow">پایدار / کلاسیک</span><h3>نسخه کلاسیک متن‌یاب</h3></div>
                    <p data-release-content="classic">نسخه کلاسیک را دانلود کنید و با نصب آن، جست‌وجو در فایل‌های خود را آغاز کنید.</p>
                    <div class="release-meta">
                        <span><strong>نوع فایل:</strong> <span data-release-delivery="classic">نصب‌کننده EXE ویندوز x64</span></span>
                        <span><strong>اندازه:</strong> <span data-release-size="classic">در حال دریافت اطلاعات…</span></span>
                    </div>
                    <a class="btn" data-release-link="classic" href="/update/MATNYAAB_x64_setup.exe">دانلود نسخه کلاسیک</a>
                </article>

                <article class="release-card">
                    <div><span class="eyebrow">نسخه جدید</span><h3>نسخه جدید متن‌یاب</h3></div>
                    <p data-release-content="kotlin">فایل را از حالت فشرده خارج کنید و برنامه را اجرا کنید. بسته‌های محتوایی را می‌توانید جداگانه از داخل برنامه دریافت کنید.</p>
                    <div class="release-meta">
                        <span><strong>نوع فایل:</strong> <span data-release-delivery="kotlin">ZIP آماده اجرا برای ویندوز x64</span></span>
                        <span><strong>اندازه:</strong> <span data-release-size="kotlin">در حال دریافت اطلاعات…</span></span>
                    </div>
                    <a class="btn btn-primary" data-release-link="kotlin" href="/downloadFiles/MATNYAAB-Kotlin-1.2-win-x64-portable.zip">دانلود نسخه جدید</a>
                </article>
            </div>
        </div>
    </div>
</section>

<section class="section" id="account">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">حساب کاربری</span><h2>اشتراک و سوابق خریدتان در دسترس شماست</h2></div>
            <p>وارد حساب خود شوید تا وضعیت اشتراک، سریال و سوابق خریدتان را ببینید.</p>
        </div>
        <div class="account-grid">
            <div class="account-card"><h3>پیش‌تر در متن‌یاب حساب ساخته‌اید؟</h3><p>با ایمیل و رمز عبور خود وارد شوید و جزئیات اشتراک و سریال را ببینید.</p><a class="btn btn-primary" href="{{ route('login') }}">ورود به حساب</a></div>
            @if ((bool) config('services.sms.production_enabled'))
                <div class="account-card"><h3>می‌خواهید حساب تازه‌ای بسازید؟</h3><p>ثبت‌نام را آغاز کنید و پس از تأیید شماره موبایل، حساب‌تان آماده استفاده خواهد بود.</p><a class="btn" href="{{ route('register') }}">ساخت حساب</a></div>
            @else
                <div class="account-card"><h3>ثبت‌نام آنلاین موقتاً در دسترس نیست</h3><p>اگر پیش‌تر حساب ساخته‌اید، از بخش ورود به حساب خود دسترسی دارید.</p><a class="btn" href="{{ route('login') }}">ورود به حساب</a></div>
            @endif
        </div>
    </div>
</section>
@endsection

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
                ['classic', 'kotlin'].forEach(key => {
                    const item = releases[key];
                    if (!item) return;
                    const link = document.querySelector('[data-release-link="' + key + '"]');
                    const size = document.querySelector('[data-release-size="' + key + '"]');
                    const delivery = document.querySelector('[data-release-delivery="' + key + '"]');
                    const content = document.querySelector('[data-release-content="' + key + '"]');
                    if (link && item.url) link.href = item.url;
                    if (size && item.size_bytes) size.textContent = formatBytes(Number(item.size_bytes));
                    if (delivery && item.delivery) delivery.textContent = item.delivery;
                    if (content && item.content_summary) content.textContent = item.content_summary;
                });
            })
            .catch(() => {});

    })();
</script>
@endpush
