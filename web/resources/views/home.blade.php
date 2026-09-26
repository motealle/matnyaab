@extends('layouts.app')

@section('title', 'متن‌یاب | جستجوی حرفه‌ای در محتوای فایل‌ها')
@section('meta_description', 'متن‌یاب؛ جستجوی سریع و محلی درون فایل‌های Word، PDF، PowerPoint، Excel، متن ساده و صفحات وب.')

@push('styles')
<style>
    .hero{padding:86px 0 72px;position:relative;overflow:hidden}
    .hero:before{
        content:"";position:absolute;inset:auto auto -120px 50%;transform:translateX(-50%);
        width:780px;height:360px;border-radius:50%;
        background:radial-gradient(circle,rgba(124,58,237,.15),transparent 68%);filter:blur(24px);pointer-events:none
    }
    .hero-grid{display:grid;grid-template-columns:1.04fr .96fr;gap:66px;align-items:center;position:relative}
    .hero-kicker{
        display:inline-flex;align-items:center;gap:9px;padding:7px 11px;border-radius:999px;
        background:rgba(139,92,246,.08);border:1px solid rgba(139,92,246,.17);
        color:#c4b5fd;font-size:12px;font-weight:700
    }
    .pulse{width:7px;height:7px;background:#a78bfa;border-radius:50%;box-shadow:0 0 20px #8b5cf6}
    .hero h1{font-size:clamp(42px,5vw,72px);line-height:1.22;letter-spacing:-2px;margin:22px 0 20px;max-width:850px}
    .gradient-text{
        background:linear-gradient(105deg,#fff 12%,#d8ccff 45%,#a78bfa 70%,#818cf8);
        -webkit-background-clip:text;background-clip:text;color:transparent
    }
    .hero-copy{font-size:17px;color:#aaaabd;max-width:720px;line-height:2;margin:0}
    .hero-actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:30px}
    .hero-meta{display:flex;gap:24px;flex-wrap:wrap;margin-top:28px;color:#737382;font-size:12px}
    .hero-meta span{display:flex;align-items:center;gap:7px}
    .hero-meta i{width:6px;height:6px;border-radius:50%;background:#34d399;display:inline-block}

    .product-stage{
        position:relative;border:1px solid var(--line);border-radius:26px;padding:14px;
        background:linear-gradient(145deg,rgba(255,255,255,.055),rgba(255,255,255,.012));
        box-shadow:0 36px 100px rgba(0,0,0,.48),0 0 90px rgba(124,58,237,.08)
    }
    .app-window{border:1px solid rgba(255,255,255,.09);border-radius:18px;overflow:hidden;background:#0a0a0e}
    .window-top{height:43px;display:flex;align-items:center;justify-content:space-between;padding:0 14px;border-bottom:1px solid var(--line);background:#0e0e13}
    .dots{display:flex;gap:6px}.dots span{width:8px;height:8px;border-radius:50%;background:#30303a}.dots span:first-child{background:#8b5cf6}
    .window-title{font-size:11px;color:#6f6f7c}
    .search-ui{padding:18px}
    .search-input{display:flex;align-items:center;gap:10px;background:#111118;border:1px solid rgba(255,255,255,.11);border-radius:11px;padding:12px 13px;color:#6f6f7d}
    .search-input svg{width:18px;stroke:#a78bfa;fill:none;stroke-width:1.8}
    .chips{display:flex;gap:7px;margin:13px 0 15px}.chip{padding:5px 8px;border-radius:7px;font-size:10px;color:#9999aa;background:#121219;border:1px solid var(--line)}
    .result-row{display:grid;grid-template-columns:37px 1fr auto;align-items:center;gap:11px;padding:11px 0;border-top:1px solid rgba(255,255,255,.055)}
    .file-icon{width:36px;height:36px;border-radius:9px;display:grid;place-items:center;background:#15151e;color:#c4b5fd;font-size:11px;font-weight:800;border:1px solid var(--line)}
    .result-title{font-size:12px;color:#dbdbe5}.result-snippet{font-size:10px;color:#676775;margin-top:1px}
    .score{font-size:10px;color:#34d399}
    .stage-badge{position:absolute;left:-22px;bottom:30px;padding:10px 12px;border:1px solid var(--line);border-radius:11px;background:rgba(16,16,21,.88);backdrop-filter:blur(16px);box-shadow:var(--shadow);font-size:11px;color:#b5b5c1}
    .stage-badge b{color:#fff}

    .section{padding:72px 0}
    .section-head{display:flex;justify-content:space-between;align-items:end;gap:24px;margin-bottom:28px}
    .section-head h2{font-size:34px;line-height:1.45;letter-spacing:-.8px;margin:5px 0 0}
    .section-head p{max-width:530px;color:var(--muted);margin:0;font-size:14px}
    .feature-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:13px}
    .feature-card{
        min-height:215px;padding:22px;border-radius:18px;border:1px solid var(--line);
        background:linear-gradient(155deg,rgba(255,255,255,.045),rgba(255,255,255,.015));
        transition:.25s ease;position:relative;overflow:hidden
    }
    .feature-card:hover{transform:translateY(-3px);border-color:rgba(167,139,250,.22);background:linear-gradient(155deg,rgba(255,255,255,.065),rgba(255,255,255,.02))}
    .feature-icon{width:42px;height:42px;border-radius:12px;display:grid;place-items:center;background:rgba(139,92,246,.08);border:1px solid rgba(139,92,246,.14);margin-bottom:34px}
    .feature-icon svg{width:20px;height:20px;stroke:#c4b5fd;fill:none;stroke-width:1.7}
    .feature-card h3{font-size:17px;margin:0 0 5px}.feature-card p{font-size:13px;color:var(--muted);margin:0;line-height:1.9}

    .workflow{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;counter-reset:step}
    .workflow-card{border-top:1px solid var(--line-strong);padding-top:20px;counter-increment:step}
    .workflow-card:before{content:"0" counter(step);font-size:11px;color:#6e5bb3;font-weight:800;letter-spacing:.12em}
    .workflow-card h3{margin:8px 0 5px;font-size:17px}.workflow-card p{margin:0;color:var(--muted);font-size:13px}

    .download-panel{
        border:1px solid rgba(167,139,250,.16);border-radius:24px;padding:32px;
        background:
            radial-gradient(600px 300px at 100% 0,rgba(124,58,237,.14),transparent 60%),
            linear-gradient(145deg,#111117,#0c0c10);
        display:grid;grid-template-columns:1fr auto;gap:30px;align-items:center
    }
    .download-panel h2{margin:0 0 6px;font-size:29px}.download-panel p{margin:0;color:var(--muted);font-size:14px}
    .download-note{display:flex;gap:18px;flex-wrap:wrap;color:#747482;font-size:11px;margin-top:14px}
    .download-note span:before{content:"✓";color:#34d399;margin-left:6px}

    .account-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}
    .account-card{padding:24px;border:1px solid var(--line);border-radius:18px;background:#0d0d12}
    .account-card h3{margin:0 0 7px}.account-card p{color:var(--muted);font-size:13px;margin:0 0 18px}

    @media(max-width:900px){
        .hero-grid{grid-template-columns:1fr;gap:42px}.product-stage{max-width:650px}
        .feature-grid{grid-template-columns:1fr 1fr}.section-head{align-items:start;flex-direction:column}
    }
    @media(max-width:640px){
        .hero{padding-top:52px}.hero h1{letter-spacing:-1px}
        .feature-grid,.workflow,.account-grid{grid-template-columns:1fr}
        .download-panel{grid-template-columns:1fr;padding:23px}
        .stage-badge{left:12px;bottom:12px}
    }
</style>
@endpush

@section('content')
<section class="hero">
    <div class="container hero-grid">
        <div>
            <div class="hero-kicker"><span class="pulse"></span> موتور جستجوی شخصی برای فایل‌های شما</div>
            <h1><span class="gradient-text">دانشتان گم نمی‌شود.</span><br>هر چیزی را داخل فایل‌ها پیدا کنید.</h1>
            <p class="hero-copy">
                متن‌یاب هزاران سند را روی رایانه شما ایندکس می‌کند تا میان Word، PDF، PowerPoint، Excel،
                متن ساده و صفحات وب، در چند لحظه به عبارت موردنظر برسید.
            </p>
            <div class="hero-actions">
                <a class="btn btn-primary" href="#download">دانلود برای ویندوز</a>
                <a class="btn" href="#workflow">ببینید چطور کار می‌کند</a>
            </div>
            <div class="hero-meta">
                <span><i></i> پردازش محلی فایل‌ها</span>
                <span><i></i> مناسب آرشیوهای بزرگ</span>
                <span><i></i> حساب‌های قبلی حفظ شده‌اند</span>
            </div>
        </div>

        <div class="product-stage" aria-label="نمای شبیه‌سازی شده از جستجوی متن‌یاب">
            <div class="app-window">
                <div class="window-top">
                    <span class="window-title">MATNYAAB / SEARCH</span>
                    <span class="dots"><span></span><span></span><span></span></span>
                </div>
                <div class="search-ui">
                    <div class="search-input">
                        <svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 21 21"/></svg>
                        <span>مثلاً: «تاریخ فلسفه اسلامی»</span>
                    </div>
                    <div class="chips"><span class="chip">PDF</span><span class="chip">DOCX</span><span class="chip">HTML</span><span class="chip">همه پوشه‌ها</span></div>
                    <div class="result-row">
                        <div class="file-icon">PDF</div>
                        <div><div class="result-title">مبانی و تاریخ اندیشه اسلامی.pdf</div><div class="result-snippet">... عبارت جستجو شده در فصل سوم، صفحه ۱۲۸ ...</div></div>
                        <div class="score">96%</div>
                    </div>
                    <div class="result-row">
                        <div class="file-icon">DOC</div>
                        <div><div class="result-title">یادداشت‌های پژوهش.docx</div><div class="result-snippet">... نتیجه مرتبط از میان اسناد پوشه پژوهش ...</div></div>
                        <div class="score">88%</div>
                    </div>
                    <div class="result-row">
                        <div class="file-icon">HTM</div>
                        <div><div class="result-title">آرشیو مقاله.html</div><div class="result-snippet">... یافتن دقیق عبارت بدون باز کردن تک‌تک فایل‌ها ...</div></div>
                        <div class="score">81%</div>
                    </div>
                </div>
            </div>
            <div class="stage-badge"><b>جستجو در چند ثانیه</b><br><span>به‌جای گشتن میان صدها فایل</span></div>
        </div>
    </div>
</section>

<section class="section" id="features">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">FEATURES</span><h2>ساخته شده برای آرشیوهای واقعی</h2></div>
            <p>وقتی تعداد فایل‌ها زیاد می‌شود، نام فایل دیگر کافی نیست. متن‌یاب خودِ محتوای فایل را قابل جستجو می‌کند.</p>
        </div>
        <div class="feature-grid">
            <article class="feature-card">
                <div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M13 2 4 14h7l-1 8 9-12h-7z"/></svg></div>
                <h3>جستجوی سریع و رتبه‌بندی‌شده</h3><p>نتایج مرتبط‌تر بالاتر دیده می‌شوند تا سریع‌تر به سند درست برسید.</p>
            </article>
            <article class="feature-card">
                <div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M6 2h8l4 4v16H6z"/><path d="M14 2v5h5"/></svg></div>
                <h3>فرمت‌های متنوع</h3><p>Word، PDF، PowerPoint، Excel، TXT، HTML و فرمت‌های متنی متداول.</p>
            </article>
            <article class="feature-card">
                <div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M4 5h16M4 12h10M4 19h7"/><circle cx="18" cy="17" r="3"/></svg></div>
                <h3>جستجوی عمیق در پوشه‌ها</h3><p>پوشه‌ها و زیرپوشه‌های بزرگ را یک‌بار ایندکس کنید و بارها جستجو کنید.</p>
            </article>
            <article class="feature-card">
                <div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M4 7h6l2 2h8v10H4z"/><path d="M4 7V5h6l2 2"/></svg></div>
                <h3>مناسب پژوهش و آرشیو</h3><p>برای کتابخانه‌های شخصی، پرونده‌های پژوهشی و مجموعه‌های حجیم اسناد.</p>
            </article>
            <article class="feature-card">
                <div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M5 4h14v16H5z"/><path d="M8 8h8M8 12h8M8 16h5"/></svg></div>
                <h3>بسته‌های محتوایی</h3><p>پشتیبانی از مجموعه‌های محتوایی آماده برای شروع سریع‌تر جستجو.</p>
            </article>
            <article class="feature-card">
                <div class="feature-icon"><svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8M12 17v4"/></svg></div>
                <h3>کلاینت ویندوز</h3><p>جستجو روی سیستم خودتان انجام می‌شود و فایل‌ها در اختیار شما می‌مانند.</p>
            </article>
        </div>
    </div>
</section>

<section class="section" id="workflow">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">WORKFLOW</span><h2>سه مرحله تا پیدا کردن هر متن</h2></div>
            <p>فرآیند استفاده ساده است و بعد از ایندکس اولیه، جستجوهای بعدی بسیار سریع انجام می‌شوند.</p>
        </div>
        <div class="workflow">
            <div class="workflow-card"><h3>پوشه را انتخاب کنید</h3><p>آرشیو، کتابخانه یا پوشه پروژه‌ای که می‌خواهید قابل جستجو شود.</p></div>
            <div class="workflow-card"><h3>اجازه دهید ایندکس ساخته شود</h3><p>متن‌یاب محتوای قابل استخراج فایل‌ها را برای جستجوی سریع آماده می‌کند.</p></div>
            <div class="workflow-card"><h3>عبارت را جستجو کنید</h3><p>نتایج مرتبط را ببینید و مستقیم به فایل موردنظر برسید.</p></div>
        </div>
    </div>
</section>

<section class="section" id="download">
    <div class="container">
        <div class="download-panel">
            <div>
                <span class="eyebrow">WINDOWS APP</span>
                <h2>متن‌یاب را روی ویندوز نصب کنید</h2>
                <p>نسخه پایدار فعلی در دسترس است و کلاینت جدید Kotlin در حال آماده‌سازی برای انتشار مرحله‌ای است.</p>
                <div class="download-note"><span>نصب‌کننده x64</span><span>حساب‌های قبلی حفظ می‌شوند</span><span>به‌روزرسانی مرحله‌ای</span></div>
            </div>
            <a class="btn btn-primary" href="/update/MATNYAAB_x64_setup.exe">دریافت نصب‌کننده</a>
        </div>
    </div>
</section>

<section class="section" id="account">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">ACCOUNT</span><h2>حساب و اشتراک شما همان‌جاست</h2></div>
            <p>داده‌های کاربران و خریدهای قبلی در مهاجرت حفظ شده‌اند و زیرساخت جدید روی همان اطلاعات کار می‌کند.</p>
        </div>
        <div class="account-grid">
            <div class="account-card">
                <h3>کاربر متن‌یاب هستید؟</h3>
                <p>با همان حساب قبلی وارد شوید، وضعیت اشتراک و سریال خود را ببینید.</p>
                <a class="btn btn-primary" href="{{ route('login') }}">ورود به حساب</a>
            </div>
            <div class="account-card">
                <h3>تازه با متن‌یاب آشنا شدید؟</h3>
                <p>ثبت‌نام جدید پس از فعال‌سازی کامل سرویس پیامک از همین مسیر انجام می‌شود.</p>
                <a class="btn" href="{{ route('register') }}">صفحه ثبت‌نام</a>
            </div>
        </div>
    </div>
</section>
@endsection
