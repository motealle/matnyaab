<!doctype html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="theme-color" content="#08080b" id="theme-color-meta">
    <meta name="color-scheme" content="dark light">
    @hasSection('meta_description')
        <meta name="description" content="@yield('meta_description')">
    @endif
    <title>@yield('title', 'متن‌یاب')</title>

    <script>
        (() => {
            const saved = localStorage.getItem('matnyaab-theme');
            const theme = saved || (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
            document.documentElement.dataset.theme = theme;
        })();
    </script>

    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Vazirmatn:wght@200;300;400;500;600;700;800&display=swap" rel="stylesheet">

    <style>
        :root{
            --bg:#070709;--bg-soft:#0b0b0f;--surface:#101015;--surface-2:#15151c;--surface-3:#1b1b24;
            --text:#f7f7fb;--muted:#9b9baa;--muted-2:#6f6f7d;--line:rgba(255,255,255,.09);--line-strong:rgba(255,255,255,.16);
            --header-bg:rgba(7,7,9,.76);--button-bg:rgba(255,255,255,.045);--button-hover:rgba(255,255,255,.075);
            --input-bg:#09090d;--panel-a:rgba(18,18,24,.95);--panel-b:rgba(12,12,16,.95);--soft-card:rgba(255,255,255,.025);
            --code-bg:#08080b;--table-head:rgba(255,255,255,.025);--grid-line:rgba(255,255,255,.018);
            --violet:#8b5cf6;--violet-2:#a78bfa;--indigo:#6366f1;--cyan:#38bdf8;--green:#34d399;--red:#fb7185;--amber:#fbbf24;
            --shadow:0 28px 90px rgba(0,0,0,.45);--radius:20px;--radius-sm:13px;--max:1180px;
        }

        html[data-theme="light"]{
            --bg:#f5f5f8;--bg-soft:#ececf2;--surface:#ffffff;--surface-2:#f8f8fb;--surface-3:#eeeeF5;
            --text:#18181e;--muted:#626270;--muted-2:#858592;--line:rgba(20,20,32,.09);--line-strong:rgba(20,20,32,.16);
            --header-bg:rgba(248,248,251,.82);--button-bg:rgba(255,255,255,.8);--button-hover:#ffffff;
            --input-bg:#ffffff;--panel-a:rgba(255,255,255,.98);--panel-b:rgba(248,248,252,.98);--soft-card:rgba(20,20,32,.025);
            --code-bg:#f2effc;--table-head:rgba(20,20,32,.035);--grid-line:rgba(20,20,32,.035);
            --shadow:0 24px 70px rgba(32,30,48,.12);
        }

        *{box-sizing:border-box}
        html{scroll-behavior:smooth}
        body{
            margin:0;min-height:100vh;font-family:"Vazirmatn",Tahoma,"Segoe UI",sans-serif;color:var(--text);
            background:
                radial-gradient(1000px 560px at 83% -8%,rgba(124,58,237,.14),transparent 65%),
                radial-gradient(760px 500px at 5% 24%,rgba(59,130,246,.06),transparent 68%),
                var(--bg);
            line-height:1.9;-webkit-font-smoothing:antialiased;transition:background-color .3s ease,color .25s ease;
        }
        body:before{
            content:"";position:fixed;inset:0;pointer-events:none;z-index:-1;
            background-image:linear-gradient(var(--grid-line) 1px,transparent 1px),linear-gradient(90deg,var(--grid-line) 1px,transparent 1px);
            background-size:52px 52px;mask-image:linear-gradient(to bottom,rgba(0,0,0,.8),transparent 80%);
        }
        ::selection{background:rgba(139,92,246,.35);color:var(--text)}
        a{color:inherit;text-decoration:none} button,input,select,textarea{font:inherit} button{cursor:pointer}
        .container{width:min(var(--max),calc(100% - 40px));margin-inline:auto}

        .site-header{position:sticky;top:0;z-index:50;border-bottom:1px solid var(--line);background:var(--header-bg);backdrop-filter:blur(20px) saturate(140%)}
        .nav{min-height:74px;display:flex;align-items:center;justify-content:space-between;gap:24px}
        .brand{display:flex;align-items:center;gap:11px;font-size:20px;font-weight:750;letter-spacing:-.35px}
        .brand-mark{
            width:38px;height:38px;border:1px solid var(--line-strong);border-radius:12px;display:grid;place-items:center;
            background:linear-gradient(145deg,var(--button-bg),transparent);box-shadow:inset 0 1px 0 rgba(255,255,255,.08),0 8px 30px rgba(0,0,0,.12)
        }
        .brand-mark svg{width:21px;height:21px;stroke:var(--text);fill:none;stroke-width:1.8}
        .nav-links{display:flex;align-items:center;gap:7px;color:var(--muted);font-size:14px}
        .nav-links a{padding:8px 11px;border-radius:9px;transition:.2s ease}
        .nav-links a:hover{color:var(--text);background:var(--soft-card)}
        .nav-actions{display:flex;align-items:center;gap:8px}

        .btn{
            appearance:none;border:1px solid var(--line-strong);border-radius:11px;display:inline-flex;align-items:center;justify-content:center;gap:8px;
            min-height:42px;padding:8px 15px;font-weight:600;font-size:14px;color:var(--text);background:var(--button-bg);transition:.2s ease
        }
        .btn:hover{transform:translateY(-1px);border-color:rgba(139,92,246,.28);background:var(--button-hover)}
        .btn-primary{color:#fff;border-color:rgba(167,139,250,.34);background:linear-gradient(135deg,#7c3aed,#6366f1);box-shadow:0 12px 32px rgba(109,40,217,.22),inset 0 1px 0 rgba(255,255,255,.2)}
        .btn-primary:hover{background:linear-gradient(135deg,#8b5cf6,#6d6ff4)}
        .btn-danger{color:#fb7185;border-color:rgba(251,113,133,.18);background:rgba(251,113,133,.07)}
        .btn-block{width:100%}
        .theme-toggle{width:42px;padding:0;position:relative;overflow:hidden}
        .theme-toggle svg{position:absolute;width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:1.8;transition:transform .35s ease,opacity .25s ease}
        .theme-toggle .sun{opacity:0;transform:translateY(18px) rotate(-30deg)}
        .theme-toggle .moon{opacity:1;transform:translateY(0)}
        html[data-theme="light"] .theme-toggle .sun{opacity:1;transform:translateY(0) rotate(0)}
        html[data-theme="light"] .theme-toggle .moon{opacity:0;transform:translateY(-18px) rotate(25deg)}

        .page-shell{padding:54px 0 80px}.auth-shell{max-width:520px;margin:0 auto;padding:52px 0 80px}
        .panel{border:1px solid var(--line);background:linear-gradient(180deg,var(--panel-a),var(--panel-b));border-radius:var(--radius);box-shadow:var(--shadow),inset 0 1px 0 rgba(255,255,255,.04)}
        .panel-pad{padding:28px}.panel-header{margin-bottom:24px}
        .eyebrow{display:inline-flex;align-items:center;gap:7px;color:var(--violet-2);font-size:12px;font-weight:700;letter-spacing:.02em;text-transform:uppercase}
        .eyebrow:before{content:"";width:6px;height:6px;border-radius:50%;background:var(--violet);box-shadow:0 0 18px var(--violet)}
        .page-title{font-size:31px;line-height:1.45;margin:7px 0 5px;letter-spacing:-.55px}.page-subtitle{color:var(--muted);margin:0;font-size:14px}

        .field{display:block;margin:0 0 17px}.field-label{display:block;color:var(--text);font-size:13px;font-weight:600;margin-bottom:7px}
        .input,.select{width:100%;height:48px;border:1px solid var(--line-strong);border-radius:11px;background:var(--input-bg);color:var(--text);padding:0 14px;outline:none;transition:.2s ease}
        .input::placeholder{color:var(--muted-2)}.input:focus,.select:focus{border-color:rgba(139,92,246,.8);box-shadow:0 0 0 3px rgba(139,92,246,.12)}
        .select{appearance:auto}.form-row{display:grid;grid-template-columns:1fr 1fr;gap:12px}.help{font-size:12px;color:var(--muted-2);margin-top:6px}

        .alert{border-radius:12px;padding:13px 15px;margin:0 0 18px;border:1px solid var(--line);font-size:13px}
        .alert-error{background:rgba(244,63,94,.07);border-color:rgba(244,63,94,.2);color:var(--red)}
        .alert-success{background:rgba(16,185,129,.075);border-color:rgba(52,211,153,.2);color:var(--green)}
        .alert-warn{background:rgba(245,158,11,.07);border-color:rgba(251,191,36,.2);color:var(--amber)}
        .muted{color:var(--muted)}.dim{color:var(--muted-2)}.text-link{color:var(--violet-2)}.text-link:hover{color:var(--violet)}
        .center{text-align:center}.divider{height:1px;background:var(--line);margin:22px 0}

        .stat-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}
        .stat{padding:17px;border:1px solid var(--line);border-radius:14px;background:var(--soft-card)}
        .stat-label{color:var(--muted);font-size:12px}.stat-value{font-weight:750;font-size:17px;margin-top:2px;overflow-wrap:anywhere}
        .serial{direction:ltr;text-align:left;display:block;overflow-wrap:anywhere;background:var(--code-bg);border:1px dashed rgba(139,92,246,.3);border-radius:11px;padding:12px;color:var(--violet-2);font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:12px}
        .table-wrap{overflow:auto;border:1px solid var(--line);border-radius:14px}
        table{width:100%;border-collapse:collapse;min-width:600px}th,td{text-align:right;padding:13px 14px;border-bottom:1px solid var(--line);font-size:13px}
        th{color:var(--text);font-weight:650;background:var(--table-head)}td{color:var(--muted)}tr:last-child td{border-bottom:0}

        .site-footer{border-top:1px solid var(--line);padding:28px 0 36px;color:var(--muted-2);font-size:12px}
        .footer-row{display:flex;justify-content:space-between;gap:24px;align-items:center}

        @media(max-width:820px){.nav-links{display:none}.nav-actions .desktop-only{display:none}.page-shell{padding-top:34px}.stat-grid{grid-template-columns:1fr}.form-row{grid-template-columns:1fr}}
        @media(max-width:520px){.container{width:min(100% - 24px,var(--max))}.panel-pad{padding:20px}.page-title{font-size:26px}.nav{min-height:66px}.brand{font-size:18px}}
        @media(prefers-reduced-motion:reduce){*,*:before,*:after{scroll-behavior:auto!important;animation-duration:.001ms!important;animation-iteration-count:1!important;transition-duration:.001ms!important}}
    </style>
    @stack('styles')
</head>
<body>
<header class="site-header">
    <div class="container nav">
        <a class="brand" href="{{ route('home') }}">
            <span class="brand-mark" aria-hidden="true">
                <svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 21 21"/><path d="M7.5 10.5h6"/></svg>
            </span>
            <span>متن‌یاب</span>
        </a>

        <nav class="nav-links" aria-label="ناوبری اصلی">
            <a href="{{ route('home') }}#features">امکانات</a>
            <a href="{{ route('home') }}#workflow">نحوه کار</a>
            <a href="{{ route('home') }}#download">دانلود</a>
            <a href="{{ route('home') }}#account">حساب کاربری</a>
        </nav>

        <div class="nav-actions">
            <button class="btn theme-toggle" type="button" data-theme-toggle aria-label="تغییر حالت روشن و تاریک" title="تغییر تم">
                <svg class="sun" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>
                <svg class="moon" viewBox="0 0 24 24"><path d="M20 15.5A8 8 0 0 1 8.5 4 8.5 8.5 0 1 0 20 15.5z"/></svg>
            </button>
            @auth
                @if (auth()->user()->is_superuser)
                    <a class="btn desktop-only" href="{{ route('admin.dashboard') }}">مدیریت</a>
                @endif
                <a class="btn desktop-only" href="{{ route('profile') }}">پروفایل</a>
                <form method="post" action="{{ route('logout') }}">
                    @csrf
                    <button class="btn" type="submit">خروج</button>
                </form>
            @else
                <a class="btn desktop-only" href="{{ route('login') }}">ورود</a>
                <a class="btn btn-primary" href="{{ route('register') }}">شروع کنید</a>
            @endauth
        </div>
    </div>
</header>

<main>@yield('content')</main>

<footer class="site-footer">
    <div class="container footer-row">
        <span>© {{ date('Y') }} متن‌یاب</span>
        <span>جستجوی محلی، سریع و خصوصی در محتوای فایل‌ها</span>
    </div>
</footer>

<script>
    (() => {
        const root = document.documentElement;
        const meta = document.getElementById('theme-color-meta');
        const apply = theme => {
            root.dataset.theme = theme;
            localStorage.setItem('matnyaab-theme', theme);
            if (meta) meta.setAttribute('content', theme === 'light' ? '#f5f5f8' : '#08080b');
        };
        const current = () => root.dataset.theme || 'dark';
        document.querySelectorAll('[data-theme-toggle]').forEach(button => {
            button.addEventListener('click', () => apply(current() === 'dark' ? 'light' : 'dark'));
        });
        if (meta) meta.setAttribute('content', current() === 'light' ? '#f5f5f8' : '#08080b');
    })();
</script>
@stack('scripts')
</body>
</html>
