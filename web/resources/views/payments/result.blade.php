@extends('layouts.app')

@section('title', ($success ? 'پرداخت موفق' : 'پرداخت ناموفق').' | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad center">
        @if ($success)
            <span class="eyebrow">پرداخت تأیید شد</span>
            <div style="width:62px;height:62px;border-radius:50%;margin:20px auto 12px;display:grid;place-items:center;background:rgba(16,185,129,.08);border:1px solid rgba(52,211,153,.2);color:#a7f3d0;font-size:28px">✓</div>
            <h1 class="page-title">پرداخت انجام شد.</h1>
            <p class="page-subtitle" style="max-width:450px;margin:0 auto 22px">اشتراک شما فعال شده است. برای دیدن سریال و تاریخ پایان اشتراک وارد حساب‌تان شوید.</p>
            <a class="btn btn-primary" href="{{ route('login') }}">ورود به حساب</a>
        @else
            <span class="eyebrow">پرداخت تأیید نشد</span>
            <div style="width:62px;height:62px;border-radius:50%;margin:20px auto 12px;display:grid;place-items:center;background:rgba(244,63,94,.08);border:1px solid rgba(251,113,133,.2);color:#fecdd3;font-size:27px">×</div>
            <h1 class="page-title">پرداخت تأیید نشد.</h1>
            <p class="page-subtitle" style="max-width:470px;margin:0 auto 22px">اشتراکی برای این پرداخت فعال نشده است. اگر مبلغی از حساب شما کسر شده باشد، وضعیت بازگشت وجه را از طریق درگاه پرداخت یا بانک خود پیگیری کنید.</p>
            <a class="btn" href="{{ route('home') }}">بازگشت به متن‌یاب</a>
        @endif

        @if (! empty($trackingCode))
            <div class="divider"></div>
            <div class="muted" style="font-size:12px">کد پیگیری پرداخت</div>
            <code class="serial" style="margin-top:8px;text-align:center">{{ $trackingCode }}</code>
        @endif
    </section>
</div>
@endsection
