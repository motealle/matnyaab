@extends('layouts.app')

@section('title', 'متن‌یاب | جستجوی حرفه‌ای در محتوای فایل‌ها')
@section('meta_description', 'متن‌یاب؛ جستجوی سریع و محلی درون فایل‌های Word، PDF، PowerPoint، Excel، متن ساده و صفحات وب.')

@push('styles')
<style>
    .hero{padding:92px 0 70px;position:relative;overflow:hidden}
    .hero:before{
        content:"";position:absolute;inset:auto auto -150px 50%;transform:translateX(-50%);
        width:850px;height:400px;border-radius:50%;
        background:radial-gradient(circle,rgba(124,58,237,.13),transparent 68%);filter:blur(24px);pointer-events:none
    }
    .hero-grid{display:grid;grid-template-columns:1.03fr .97fr;gap:70px;align-items:center;position:relative}
    .hero-kicker{
        display:inline-flex;align-items:center;gap:9px;padding:7px 11px;border-radius:999px;
        background:rgba(139,92,246,.07);border:1px solid rgba(139,92,246,.17);
        color:var(--violet-2);font-size:12px;font-weight:650
    }
    .pulse{width:7px;height:7px;background:var(--violet-2);border-radius:50%;box-shadow:0 0 20px var(--violet)}
    .hero-title{
        margin:24px 0 20px;max-width:760px;font-weight:250;
        font-size:clamp(39px,4.7vw,67px);line-height:1.42;letter-spacing:-1.8px
    }
    .hero-title .quiet{display:block;color:var(--text)}
    .type-line{
        display:flex;align-items:center;gap:8px;min-height:1.6em;color:var(--violet-2);
        font-weight:300;opacity:0;transform:translateY(10px);animation:heroReveal .8s .18s ease forwards
    }
    .type-caret{display:inline-block;width:1px;height:.9em;background:var(--violet-2);animation:caretBlink .85s step-end infinite}
    .hero-copy{font-size:16px;font-weight:300;color:var(--muted);max-width:700px;line-height:2.15;margin:0;opacity:0;transform:translateY(12px);animation:heroReveal .8s .3s ease forwards}
    .hero-actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:30px;opacity:0;transform:translateY(12px);animation:heroReveal .8s .42s ease forwards}
    .hero-meta{display:flex;gap:24px;flex-wrap:wrap;margin-top:27px;color:var(--muted-2);font-size:12px;opacity:0;animation:fadeOnly .8s .55s ease forwards}
    .hero-meta span{display:flex;align-items:center;gap:7px}.hero-meta i{width:6px;height:6px;border-radius:50%;background:var(--green);display:inline-block}
    @keyframes heroReveal{to{opacity:1;transform:translateY(0)}} @keyframes fadeOnly{to{opacity:1}} @keyframes caretBlink{50%{opacity:0}}

    .product-stage{
        position:relative;border:1px solid var(--line);border-radius:26px;padding:12px;background:linear-gradient(145deg,var(--soft-card),transparent);
        box-shadow:var(--shadow),0 0 90px rgba(124,58,237,.07);opacity:0;transform:translateX(-18px);animation:stageIn .9s .18s cubic-bezier(.2,.75,.2,1) forwards
    }
    @keyframes stageIn{to{opacity:1;transform:translateX(0)}}
    .real-shot{
        position:relative;border-radius:18px;overflow:hidden;border:1px solid var(--line);background:var(--surface-2);min-height:320px;
        display:grid;place-items:center
    }
    .real-shot img{display:block;width:100%;height:auto;max-height:470px;object-fit:contain;background:var(--surface-2)}
    .real-shot:after{
        content:"";position:absolute;inset:0;pointer-events:none;
        background:linear-gradient(110deg,transparent 25%,rgba(139,92,246,.09) 46%,transparent 66%);
        transform:translateX(110%);animation:scanImage 5.6s 1.4s ease-in-out infinite
    }
    @keyframes scanImage{0%,55%{transform:translateX(110%)}78%{transform:translateX(-110%)}100%{transform:translateX(-110%)}}
    .stage-caption{
        position:absolute;left:-18px;bottom:24px;padding:10px 12px;border:1px solid var(--line);border-radius:11px;
        background:var(--header-bg);backdrop-filter:blur(16px);box-shadow:var(--shadow);font-size:11px;color:var(--muted)
    }
    .stage-caption b{color:var(--text);font-weight:650}

    .section{padding:72px 0}.section-head{display:flex;justify-content:space-between;align-items:end;gap:24px;margin-bottom:28px}
    .section-head h2{font-size:34px;font-weight:400;line-height:1.5;letter-spacing:-.7px;margin:5px 0 0}.section-head p{max-width:530px;color:var(--muted);margin:0;font-size:14px;font-weight:300}
    .feature-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:13px}
    .feature-card{min-height:215px;padding:22px;border-radius:18px;border:1px solid var(--line);background:linear-gradient(155deg,var(--soft-card),transparent);transition:.25s ease;position:relative;overflow:hidden}
    .feature-card:hover{transform:translateY(-3px);border-color:rgba(139,92,246,.24)}
    .feature-icon{width:42px;height:42px;border-radius:12px;display:grid;place-items:center;background:rgba(139,92,246,.08);border:1px solid rgba(139,92,246,.14);margin-bottom:34px}
    .feature-icon svg{width:20px;height:20px;stroke:var(--violet-2);fill:none;stroke-width:1.7}
    .feature-card h3{font-size:17px;font-weight:550;margin:0 0 5px}.feature-card p{font-size:13px;font-weight:300;color:var(--muted);margin:0;line-height:1.95}

    .workflow{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;counter-reset:step}.workflow-card{border-top:1px solid var(--line-strong);padding-top:20px;counter-increment:step}
    .workflow-card:before{content:"0" counter(step);font-size:11px;color:var(--violet);font-weight:700;letter-spacing:.12em}
    .workflow-card h3{margin:8px 0 5px;font-size:17px;font-weight:550}.workflow-card p{margin:0;color:var(--muted);font-size:13px;font-weight:300}

    .download-panel{
        border:1px solid rgba(139,92,246,.17);border-radius:24px;padding:32px;
        background:radial-gradient(600px 300px at 100% 0,rgba(124,58,237,.12),transparent 60%),linear-gradient(145deg,var(--surface),var(--surface-2));
        display:grid;grid-template-columns:1fr auto;gap:30px;align-items:center
    }
    .download-panel h2{margin:0 0 6px;font-size:29px;font-weight:450}.download-panel p{margin:0;color:var(--muted);font-size:14px;font-weight:300}
    .download-note{display:flex;gap:18px;flex-wrap:wrap;color:var(--muted-2);font-size:11px;margin-top:14px}.download-note span:before{content:"✓";color:var(--green);margin-left:6px}

    .account-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.account-card{padding:24px;border:1px solid var(--line);border-radius:18px;background:var(--surface)}
    .account-card h3{margin:0 0 7px;font-weight:550}.account-card p{color:var(--muted);font-size:13px;font-weight:300;margin:0 0 18px}

    @media(max-width:900px){
        .hero-grid{grid-template-columns:1fr;gap:42px}.product-stage{max-width:680px}.feature-grid{grid-template-columns:1fr 1fr}.section-head{align-items:start;flex-direction:column}
    }
    @media(max-width:640px){
        .hero{padding-top:55px}.hero-title{letter-spacing:-1px}.feature-grid,.workflow,.account-grid{grid-template-columns:1fr}
        .download-panel{grid-template-columns:1fr;padding:23px}.stage-caption{left:10px;bottom:10px}.real-shot{min-height:230px}
    }
    @media(prefers-reduced-motion:reduce){
        .type-caret{animation:none}.real-shot:after{animation:none}.hero-copy,.hero-actions,.hero-meta,.product-stage,.type-line{opacity:1;transform:none;animation:none}
    }
</style>
@endpush

@section('content')
<section class="hero">
    <div class="container hero-grid">
        <div>
            <div class="hero-kicker"><span class="pulse"></span> موتور جستجوی شخصی برای فایل‌های شما</div>
            <h1 class="hero-title">
                <span class="quiet">وقتی فقط بخشی از آن را یادتان مانده،</span>
                <span class="type-line"><span id="hero-phrase">متن‌یاب پیدایش می‌کند</span><span class="type-caret" aria-hidden="true"></span></span>
            </h1>
            <p class="hero-copy">
                هزاران سند روی رایانه‌تان می‌تواند مثل یک کتابخانه واحد جستجو شود؛
                Word، PDF، PowerPoint، Excel، متن ساده و صفحات وب، بدون باز کردن تک‌تک فایل‌ها.
            </p>
            <div class="hero-actions">
                <a class="btn btn-primary" href="#download">دانلود برای ویندوز</a>
                <a class="btn" href="#workflow">نحوه کار</a>
            </div>
            <div class="hero-meta">
                <span><i></i> پردازش محلی فایل‌ها</span>
                <span><i></i> مناسب آرشیوهای بزرگ</span>
                <span><i></i> حساب‌های قبلی حفظ شده‌اند</span>
            </div>
        </div>

        <figure class="product-stage" aria-label="تصویر واقعی نرم‌افزار متن‌یاب">
            <div class="real-shot">
                <img src="/static/sc1.png" alt="تصویر واقعی محیط نرم‌افزار متن‌یاب" loading="eager" fetchpriority="high">
            </div>
            <figcaption class="stage-caption"><b>تصویر واقعی برنامه</b><br>محیط نسخه دسکتاپ متن‌یاب</figcaption>
        </figure>
    </div>
</section>

<section class="section" id="features">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">FEATURES</span><h2>ساخته شده برای آرشیوهای واقعی</h2></div>
            <p>وقتی تعداد فایل‌ها زیاد می‌شود، نام فایل دیگر کافی نیست. متن‌یاب خودِ محتوای فایل را قابل جستجو می‌کند.</p>
        </div>
        <div class="feature-grid">
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M13 2 4 14h7l-1 8 9-12h-7z"/></svg></div><h3>جستجوی سریع و رتبه‌بندی‌شده</h3><p>نتایج مرتبط‌تر بالاتر دیده می‌شوند تا سریع‌تر به سند درست برسید.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M6 2h8l4 4v16H6z"/><path d="M14 2v5h5"/></svg></div><h3>فرمت‌های متنوع</h3><p>Word، PDF، PowerPoint، Excel، TXT، HTML و فرمت‌های متنی متداول.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M4 5h16M4 12h10M4 19h7"/><circle cx="18" cy="17" r="3"/></svg></div><h3>جستجوی عمیق در پوشه‌ها</h3><p>پوشه‌ها و زیرپوشه‌های بزرگ را یک‌بار ایندکس کنید و بارها جستجو کنید.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M4 7h6l2 2h8v10H4z"/><path d="M4 7V5h6l2 2"/></svg></div><h3>مناسب پژوهش و آرشیو</h3><p>برای کتابخانه‌های شخصی، پرونده‌های پژوهشی و مجموعه‌های حجیم اسناد.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><path d="M5 4h14v16H5z"/><path d="M8 8h8M8 12h8M8 16h5"/></svg></div><h3>بسته‌های محتوایی</h3><p>پشتیبانی از مجموعه‌های محتوایی آماده برای شروع سریع‌تر جستجو.</p></article>
            <article class="feature-card"><div class="feature-icon"><svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8M12 17v4"/></svg></div><h3>کلاینت ویندوز</h3><p>جستجو روی سیستم خودتان انجام می‌شود و فایل‌ها در اختیار شما می‌مانند.</p></article>
        </div>
    </div>
</section>

<section class="section" id="workflow">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">WORKFLOW</span><h2>سه مرحله تا پیدا کردن هر متن</h2></div>
            <p>بعد از ایندکس اولیه، جستجوهای بعدی بسیار سریع انجام می‌شوند و لازم نیست فایل‌ها را یکی‌یکی باز کنید.</p>
        </div>
        <div class="workflow">
            <div class="workflow-card"><h3>پوشه را انتخاب کنید</h3><p>آرشیو، کتابخانه یا پوشه پروژه‌ای که می‌خواهید قابل جستجو شود.</p></div>
            <div class="workflow-card"><h3>ایندکس ساخته می‌شود</h3><p>متن‌یاب محتوای قابل استخراج فایل‌ها را برای جستجوی سریع آماده می‌کند.</p></div>
            <div class="workflow-card"><h3>آن تکه متن را جستجو کنید</h3><p>حتی اگر فقط چند واژه از متن را یادتان مانده، نتیجه مرتبط را پیدا کنید.</p></div>
        </div>
    </div>
</section>

<section class="section" id="download">
    <div class="container">
        <div class="download-panel">
            <div>
                <span class="eyebrow">WINDOWS APP</span>
                <h2>متن‌یاب را روی ویندوز نصب کنید</h2>
                <p>نسخه پایدار فعلی در دسترس است و کلاینت جدید Kotlin برای انتشار مرحله‌ای آماده می‌شود.</p>
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
            <div class="account-card"><h3>کاربر متن‌یاب هستید؟</h3><p>با همان حساب قبلی وارد شوید، وضعیت اشتراک و سریال خود را ببینید.</p><a class="btn btn-primary" href="{{ route('login') }}">ورود به حساب</a></div>
            <div class="account-card"><h3>تازه با متن‌یاب آشنا شدید؟</h3><p>ثبت‌نام جدید پس از فعال‌سازی کامل سرویس پیامک از همین مسیر انجام می‌شود.</p><a class="btn" href="{{ route('register') }}">صفحه ثبت‌نام</a></div>
        </div>
    </div>
</section>
@endsection

@push('scripts')
<script>
    (() => {
        const target = document.getElementById('hero-phrase');
        if (!target) return;

        const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
        const phrases = [
            'متن‌یاب پیدایش می‌کند',
            'در هزار فایل دنبالش می‌گردد',
            'از داخل PDF و Word بیرونش می‌کشد',
            'نتیجه مرتبط را جلوی شما می‌گذارد'
        ];

        if (reduced) {
            target.textContent = phrases[0];
            return;
        }

        let phrase = 0;
        let char = phrases[0].length;
        let deleting = false;

        const tick = () => {
            const text = phrases[phrase];

            if (!deleting) {
                char++;
                target.textContent = text.slice(0, char);
                if (char >= text.length) {
                    deleting = true;
                    setTimeout(tick, 1700);
                    return;
                }
                setTimeout(tick, 52);
                return;
            }

            char--;
            target.textContent = text.slice(0, Math.max(0, char));
            if (char <= 0) {
                deleting = false;
                phrase = (phrase + 1) % phrases.length;
                setTimeout(tick, 320);
                return;
            }
            setTimeout(tick, 24);
        };

        setTimeout(() => {
            deleting = true;
            tick();
        }, 1900);
    })();
</script>
@endpush
