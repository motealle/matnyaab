@extends('layouts.app')

@section('title', 'لینک بازیابی ارسال شد | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad center">
        <span class="eyebrow">CHECK YOUR EMAIL</span>
        <h1 class="page-title" style="margin-top:16px">اگر حسابی با این ایمیل وجود داشته باشد، لینک بازیابی ارسال می‌شود.</h1>
        <p class="page-subtitle" style="margin:0 auto 24px;max-width:430px">لینک ۳۰ دقیقه اعتبار دارد. پوشه Spam را هم بررسی کنید.</p>
        <a class="btn" href="{{ route('login') }}">بازگشت به ورود</a>
    </section>
</div>
@endsection
