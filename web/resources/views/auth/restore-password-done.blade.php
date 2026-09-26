@extends('layouts.app')

@section('title', 'لینک بازیابی ارسال شد | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad center">
        <span class="eyebrow">ایمیل‌تان را بررسی کنید</span>
        <h1 class="page-title" style="margin-top:16px">اگر حسابی با این ایمیل وجود داشته باشد، لینک بازیابی برای آن ارسال شده است.</h1>
        <p class="page-subtitle" style="margin:0 auto 24px;max-width:430px">این لینک تا ۳۰ دقیقه معتبر است. اگر پیام را نمی‌بینید، پوشه هرزنامه را هم بررسی کنید.</p>
        <a class="btn" href="{{ route('login') }}">بازگشت به ورود</a>
    </section>
</div>
@endsection
