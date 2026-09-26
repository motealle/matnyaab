@extends('layouts.app')

@section('title', 'بازیابی رمز عبور | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad">
        <div class="panel-header">
            <span class="eyebrow">RECOVERY</span>
            <h1 class="page-title">بازیابی رمز عبور</h1>
            <p class="page-subtitle">ایمیلی که با آن در متن‌یاب ثبت‌نام کرده‌اید وارد کنید.</p>
        </div>

        @if ($errors->any())
            <div class="alert alert-error">{{ $errors->first() }}</div>
        @endif

        @if (! $mailReady)
            <div class="alert alert-warn">
                بازیابی ایمیلی هنوز روی سرور جدید در حال فعال‌سازی است. اگر به حساب دسترسی دارید، از داخل پروفایل می‌توانید رمز را تغییر دهید.
            </div>
        @endif

        <form method="post" action="{{ route('password.restore.send') }}">
            @csrf
            <label class="field">
                <span class="field-label">ایمیل حساب</span>
                <input class="input" type="email" name="email" value="{{ old('email') }}" autocomplete="email" required autofocus>
            </label>
            <button class="btn btn-primary btn-block" type="submit" @disabled(! $mailReady)>ارسال لینک بازیابی</button>
        </form>

        <div class="divider"></div>
        <p class="center" style="margin:0"><a class="text-link" href="{{ route('login') }}">بازگشت به ورود</a></p>
    </section>
</div>
@endsection
