@extends('layouts.app')

@section('title', 'نسخه‌های متن‌یاب | دریافت نسخهٔ تازه و نسخه‌های پیشین')
@section('meta_description', 'فهرست همهٔ نسخه‌های متن‌یاب برای ویندوز با تازه‌های هر نسخه؛ نسخهٔ تازه و نسخه‌های پیشین، از جمله نسخهٔ کاتلین و کلاسیک.')

@section('content')
<style>
    .ver-list{display:grid;gap:14px;max-width:880px;margin:0 auto}
    .ver{padding:18px 20px;border-radius:16px;border:1px solid var(--line);background:var(--soft-card)}
    .ver.current{border-color:rgba(73,155,224,.45)}
    .ver-head{display:flex;flex-wrap:wrap;gap:8px 14px;align-items:center;justify-content:space-between}
    .ver h2{font-size:1.15rem;margin:0}
    .ver ul{margin:10px 0 12px;padding-inline-start:20px;color:var(--muted);line-height:1.9;font-size:.95rem}
    .ver-links{display:flex;flex-wrap:wrap;gap:8px}
    .ver-links a.missing{opacity:.45;pointer-events:none;text-decoration:line-through}
    .ver-date{color:var(--muted-2);font-size:.85rem}
</style>
<section class="section">
    <div class="container">
        <div class="section-head">
            <span class="eyebrow">دریافت</span>
            <h1 style="font-size:1.8rem">نسخه‌های متن‌یاب</h1>
            <p>پیشنهاد ما همیشه تازه‌ترین نسخه است؛ اما اگر به نسخه‌ای عادت دارید یا رایانه‌تان با آن سازگارتر است، نسخه‌های پیشین هم این‌جا هستند. همهٔ نسخه‌ها فهرست‌های قبلی شما را می‌خوانند.</p>
        </div>
        <div class="ver-list">
            @foreach ($versions as $v)
                <article class="ver {{ !empty($v['current']) ? 'current' : '' }}">
                    <div class="ver-head">
                        <h2>{{ str_starts_with($v['version'], 'k') || $v['version'] === 'classic' ? $v['fa'] : 'متن‌یاب '.$v['fa'] }} @if (!empty($v['current']))<span class="new-badge">تازه‌ترین</span>@endif</h2>
                        <span class="ver-date">{{ $v['date'] }}</span>
                    </div>
                    <ul>@foreach ($v['notes'] as $n)<li>{{ $n }}</li>@endforeach</ul>
                    <div class="ver-links">
                        @if ($v['setup'])<a class="btn {{ !empty($v['current']) ? 'btn-primary' : '' }}" data-check href="{{ $v['setup'] }}">نصب‌کننده ↓</a>@endif
                        @if ($v['zip'])<a class="btn" data-check href="{{ $v['zip'] }}">نسخهٔ بدون نصب (ZIP) ↓</a>@endif
                    </div>
                </article>
            @endforeach
        </div>
        <p style="text-align:center;margin-top:24px"><a href="{{ route('home') }}#download">بازگشت به صفحهٔ دریافت</a></p>
    </div>
</section>
<script>
    // فایل‌هایی که روی سرور نیستند، کم‌رنگ و غیرفعال نشان داده می‌شوند
    document.querySelectorAll('a[data-check]').forEach(a => {
        fetch(a.getAttribute('href'), { method: 'HEAD', cache: 'no-store' }).then(r => {
            if (!r.ok) { a.classList.add('missing'); a.title = 'این فایل اکنون در دسترس نیست'; a.textContent += ' (در دسترس نیست)'; }
        }).catch(() => {});
    });
</script>
@endsection
