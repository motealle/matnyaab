<!doctype html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="theme-color" content="#08080b">
    <meta name="color-scheme" content="dark">
    @hasSection('meta_description')
        <meta name="description" content="@yield('meta_description')">
    @endif
    <title>@yield('title', 'متن‌یاب')</title>

    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Vazirmatn:wght@300;400;500;600;700;800;900&display=swap" rel="stylesheet">

    <style>
        :root{
            --bg:#070709;
            --bg-soft:#0b0b0f;
            --surface:#101015;
            --surface-2:#15151c;
            --surface-3:#1b1b24;
            --text:#f7f7fb;
            --muted:#9b9baa;
            --muted-2:#6f6f7d;
            --line:rgba(255,255,255,.09);
            --line-strong:rgba(255,255,255,.16);
            --violet:#8b5cf6;
            --violet-2:#a78bfa;
            --indigo:#6366f1;
            --cyan:#38bdf8;
            --green:#34d399;
            --red:#fb7185;
            --amber:#fbbf24;
            --shadow:0 28px 90px rgba(0,0,0,.45);
            --radius:20px;
            --radius-sm:13px;
            --max:1180px;
        }

        *{box-sizing:border-box}
        html{scroll-behavior:smooth}
        body{
            margin:0;
            min-height:100vh;
            font-family:"Vazirmatn",Tahoma,"Segoe UI",sans-serif;
            color:var(--text);
            background:
                radial-gradient(1000px 560px at 83% -8%, rgba(124,58,237,.18), transparent 65%),
                radial-gradient(760px 500px at 5% 24%, rgba(59,130,246,.08), transparent 68%),
                var(--bg);
            line-height:1.9;
            -webkit-font-smoothing:antialiased;
        }

        body:before{
            content:"";
            position:fixed; inset:0; pointer-events:none; z-index:-1;
            background-image:
                linear-gradient(rgba(255,255,255,.018) 1px, transparent 1px),
                linear-gradient(90deg, rgba(255,255,255,.018) 1px, transparent 1px);
            background-size:52px 52px;
            mask-image:linear-gradient(to bottom, rgba(0,0,0,.8), transparent 80%);
        }

        ::selection{background:rgba(139,92,246,.45);color:#fff}
        a{color:inherit;text-decoration:none}
        button,input,select,textarea{font:inherit}
        button{cursor:pointer}
        .container{width:min(var(--max),calc(100% - 40px));margin-inline:auto}

        .site-header{
            position:sticky;top:0;z-index:50;
            border-bottom:1px solid var(--line);
            background:rgba(7,7,9,.76);
            backdrop-filter:blur(20px) saturate(140%);
        }
        .nav{
            min-height:74px;
            display:flex;align-items:center;justify-content:space-between;gap:24px;
        }
        .brand{display:flex;align-items:center;gap:11px;font-size:20px;font-weight:800;letter-spacing:-.35px}
        .brand-mark{
            width:38px;height:38px;border:1px solid var(--line-strong);border-radius:12px;
            display:grid;place-items:center;
            background:linear-gradient(145deg,rgba(255,255,255,.11),rgba(255,255,255,.025));
            box-shadow:inset 0 1px 0 rgba(255,255,255,.1),0 8px 30px rgba(0,0,0,.25);
        }
        .brand-mark svg{width:21px;height:21px;stroke:#fff;fill:none;stroke-width:1.8}
        .nav-links{display:flex;align-items:center;gap:7px;color:#b8b8c5;font-size:14px}
        .nav-links a{padding:8px 11px;border-radius:9px;transition:.2s ease}
        .nav-links a:hover{color:#fff;background:rgba(255,255,255,.055)}
        .nav-actions{display:flex;align-items:center;gap:8px}

        .btn{
            appearance:none;border:1px solid var(--line-strong);border-radius:11px;
            display:inline-flex;align-items:center;justify-content:center;gap:8px;
            min-height:42px;padding:8px 15px;font-weight:650;font-size:14px;color:#eeeef5;
            background:rgba(255,255,255,.045);transition:.2s ease;
        }
        .btn:hover{transform:translateY(-1px);border-color:rgba(255,255,255,.27);background:rgba(255,255,255,.075)}
        .btn-primary{
            color:#fff;border-color:rgba(167,139,250,.34);
            background:linear-gradient(135deg,#7c3aed,#6366f1);
            box-shadow:0 12px 32px rgba(109,40,217,.25),inset 0 1px 0 rgba(255,255,255,.2);
        }
        .btn-primary:hover{background:linear-gradient(135deg,#8b5cf6,#6d6ff4)}
        .btn-danger{color:#fecdd3;border-color:rgba(251,113,133,.18);background:rgba(251,113,133,.07)}
        .btn-block{width:100%}

        .page-shell{padding:54px 0 80px}
        .auth-shell{max-width:520px;margin:0 auto;padding:52px 0 80px}
        .panel{
            border:1px solid var(--line);
            background:linear-gradient(180deg,rgba(18,18,24,.95),rgba(12,12,16,.95));
            border-radius:var(--radius);
            box-shadow:var(--shadow),inset 0 1px 0 rgba(255,255,255,.04);
        }
        .panel-pad{padding:28px}
        .panel-header{margin-bottom:24px}
        .eyebrow{
            display:inline-flex;align-items:center;gap:7px;
            color:#c4b5fd;font-size:12px;font-weight:700;
            letter-spacing:.02em;text-transform:uppercase;
        }
        .eyebrow:before{content:"";width:6px;height:6px;border-radius:50%;background:#8b5cf6;box-shadow:0 0 18px #8b5cf6}
        .page-title{font-size:31px;line-height:1.45;margin:7px 0 5px;letter-spacing:-.55px}
        .page-subtitle{color:var(--muted);margin:0;font-size:14px}

        .field{display:block;margin:0 0 17px}
        .field-label{display:block;color:#d8d8e2;font-size:13px;font-weight:600;margin-bottom:7px}
        .input,.select{
            width:100%;height:48px;border:1px solid var(--line-strong);border-radius:11px;
            background:#09090d;color:#f6f6fa;padding:0 14px;outline:none;transition:.2s ease;
        }
        .input::placeholder{color:#5f5f6c}
        .input:focus,.select:focus{border-color:rgba(139,92,246,.8);box-shadow:0 0 0 3px rgba(139,92,246,.12)}
        .select{appearance:auto}
        .form-row{display:grid;grid-template-columns:1fr 1fr;gap:12px}
        .help{font-size:12px;color:var(--muted-2);margin-top:6px}

        .alert{border-radius:12px;padding:13px 15px;margin:0 0 18px;border:1px solid var(--line);font-size:13px}
        .alert-error{background:rgba(244,63,94,.07);border-color:rgba(244,63,94,.2);color:#fecdd3}
        .alert-success{background:rgba(16,185,129,.075);border-color:rgba(52,211,153,.2);color:#a7f3d0}
        .alert-warn{background:rgba(245,158,11,.07);border-color:rgba(251,191,36,.2);color:#fde68a}

        .muted{color:var(--muted)}
        .dim{color:var(--muted-2)}
        .text-link{color:#c4b5fd}
        .text-link:hover{color:#ddd6fe}
        .center{text-align:center}
        .divider{height:1px;background:var(--line);margin:22px 0}

        .stat-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}
        .stat{
            padding:17px;border:1px solid var(--line);border-radius:14px;background:rgba(255,255,255,.025)
        }
        .stat-label{color:var(--muted);font-size:12px}
        .stat-value{font-weight:800;font-size:17px;margin-top:2px;overflow-wrap:anywhere}

        .serial{
            direction:ltr;text-align:left;display:block;overflow-wrap:anywhere;
            background:#08080b;border:1px dashed rgba(167,139,250,.3);border-radius:11px;
            padding:12px;color:#d8ccff;font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:12px;
        }

        .table-wrap{overflow:auto;border:1px solid var(--line);border-radius:14px}
        table{width:100%;border-collapse:collapse;min-width:600px}
        th,td{text-align:right;padding:13px 14px;border-bottom:1px solid var(--line);font-size:13px}
        th{color:#c9c9d4;font-weight:650;background:rgba(255,255,255,.025)}
        td{color:#a9a9b7}
        tr:last-child td{border-bottom:0}

        .site-footer{border-top:1px solid var(--line);padding:28px 0 36px;color:var(--muted-2);font-size:12px}
        .footer-row{display:flex;justify-content:space-between;gap:24px;align-items:center}

        @media(max-width:820px){
            .nav-links{display:none}
            .nav-actions .desktop-only{display:none}
            .page-shell{padding-top:34px}
            .stat-grid{grid-template-columns:1fr}
            .form-row{grid-template-columns:1fr}
        }
        @media(max-width:520px){
            .container{width:min(100% - 24px,var(--max))}
            .panel-pad{padding:20px}
            .page-title{font-size:26px}
            .nav{min-height:66px}
            .brand{font-size:18px}
        }
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

<main>
    @yield('content')
</main>

<footer class="site-footer">
    <div class="container footer-row">
        <span>© {{ date('Y') }} متن‌یاب</span>
        <span>جستجوی محلی، سریع و خصوصی در محتوای فایل‌ها</span>
    </div>
</footer>
@stack('scripts')
</body>
</html>
