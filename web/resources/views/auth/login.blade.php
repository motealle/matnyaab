@extends('layouts.app')

@section('title', 'ورود | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad">
        <div class="panel-header">
            <span class="eyebrow">WELCOME BACK</span>
            <h1 class="page-title">ورود به متن‌یاب</h1>
            <p class="page-subtitle">با همان حساب قبلی وارد شوید. اطلاعات و اشتراک‌های شما حفظ شده‌اند.</p>
        </div>

        @if ($errors->any())
            <div class="alert alert-error">{{ $errors->first() }}</div>
        @endif

        <form method="post" action="{{ route('login.submit') }}">
            @csrf
            <label class="field">
                <span class="field-label">نام کاربری یا ایمیل</span>
                <input class="input" name="username" value="{{ old('username') }}" autocomplete="username" required autofocus>
            </label>

            <label class="field">
                <span class="field-label">رمز عبور</span>
                <input class="input" type="password" name="password" autocomplete="current-password" required>
            </label>

            <div style="display:flex;justify-content:flex-end;margin:-5px 0 14px">
                <a class="text-link" style="font-size:12px" href="{{ route('password.restore') }}">رمز عبور را فراموش کرده‌اید؟</a>
            </div>
            <button class="btn btn-primary btn-block" type="submit">ورود به حساب</button>
        </form>

        <div class="divider"></div>
        <p class="center muted" style="margin:0">حساب ندارید؟ <a class="text-link" href="{{ route('register') }}">ثبت‌نام</a></p>
    </section>
</div>
@endsection
