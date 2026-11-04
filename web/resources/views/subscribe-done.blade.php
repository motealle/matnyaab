@extends('layouts.app')

@section('title', 'کد اشتراک شما | متن‌یاب')

@section('content')
<div class="container page-shell" style="max-width:680px">
    <section class="panel panel-pad" style="text-align:center">
        <div style="font-size:42px">🌿</div>
        <h1 class="page-title" style="font-size:26px">{{ $kind === 'renew' ? 'تمدیدتان ثبت شد، ممنون! 🌷' : 'خوش آمدید؛ اشتراک‌تان ثبت شد! 🌷' }}</h1>
        <p class="page-subtitle">{{ $s['name'] }} عزیز، خیلی ممنون که همراه ما هستید. اشتراک {{ $s['plan_title'] }} تا {{ $s['end'] }} برقرار است.@if (! empty($s['org'])) این کد را به همهٔ همکاران‌تان در «{{ $s['org'] }}» بدهید؛ همه با همین یک کد وارد می‌شوند.@endif</p>
        <div style="margin:18px auto;padding:18px;border:2px dashed var(--accent);border-radius:16px;max-width:360px">
            <small class="muted">{{ empty($s['org']) ? 'کد اشتراک' : 'کد گروهی ('.$s['seats'].' کاربر)' }}</small>
            <div id="code" style="font-size:30px;font-weight:800;letter-spacing:.08em;direction:ltr;font-family:Consolas,monospace">{{ $s['code'] }}</div>
            <button class="btn btn-primary" type="button" id="copy" style="margin-top:8px">کپی کد</button>
        </div>
        <ol style="text-align:right;line-height:2.2;color:var(--muted);max-width:460px;margin:0 auto">
            <li>متن‌یاب را باز کنید و روی نشان اشتراک در پایین پنجره بزنید.</li>
            <li>کد را در کادر «کد اشتراک دارم» بچسبانید و «فعال‌سازی» را بزنید.</li>
            <li>روزشمار برنامه با همین صفحه هماهنگ می‌شود. {{ $kind === 'renew' ? 'اگر کد را قبلاً وارد کرده‌اید، کافی است برنامه را یک بار باز کنید.' : '' }}</li>
        </ol>
        <p class="muted" style="font-size:13px;margin-top:16px">این کد را نزد خود نگه دارید؛ برای تمدید هم از همین کد استفاده می‌شود.</p>
        <a class="btn" href="{{ route('home') }}">بازگشت به صفحهٔ اصلی</a>
    </section>
</div>
@endsection

@push('scripts')
<script>
document.getElementById('copy').addEventListener('click', async e => {
    try { await navigator.clipboard.writeText(document.getElementById('code').textContent.trim()); e.target.textContent = 'کپی شد ✓'; } catch (_) {}
});
</script>
@endpush
