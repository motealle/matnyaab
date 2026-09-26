@extends('layouts.app')

@section('title', 'ثبت‌نام | متن‌یاب')

@section('content')
<div class="container auth-shell" style="max-width:620px">
    <section class="panel panel-pad">
        <div class="panel-header">
            <span class="eyebrow">عضویت در متن‌یاب</span>
            <h1 class="page-title">ساخت حساب متن‌یاب</h1>
            <p class="page-subtitle">برای خرید و مدیریت اشتراک، حساب خود را بسازید.</p>
        </div>

        @if ($errors->any())
            <div class="alert alert-error">{{ $errors->first() }}</div>
        @endif

        @php
            $smsReady = (bool) config('services.sms.production_enabled')
                && filled(config('services.sms.username'))
                && filled(config('services.sms.password'))
                && filled(config('services.sms.number'));
        @endphp

        @if (! $smsReady)
            <div class="alert alert-warn">
                <strong>ثبت‌نام آنلاین موقتاً در دسترس نیست.</strong>
                <div style="margin-top:5px">اگر پیش‌تر حساب ساخته‌اید، می‌توانید همین حالا وارد شوید.</div>
            </div>
            <a class="btn btn-primary btn-block" href="{{ route('login') }}">ورود به حساب</a>
        @else
            <form method="post" action="{{ route('register.submit') }}">
                @csrf
                <div class="form-row">
                    <label class="field">
                        <span class="field-label">ایمیل / نام کاربری</span>
                        <input class="input" type="email" name="username" value="{{ old('username') }}" autocomplete="email" required>
                    </label>
                    <label class="field">
                        <span class="field-label">نام و نام خانوادگی</span>
                        <input class="input" name="first_name" value="{{ old('first_name') }}" autocomplete="name" required>
                    </label>
                </div>

                <label class="field">
                    <span class="field-label">شماره موبایل</span>
                    <input class="input" name="phone_number" inputmode="numeric" maxlength="11" value="{{ old('phone_number') }}" placeholder="09123456789" autocomplete="tel" required>
                    <div class="help">کد تأیید چهاررقمی به این شماره ارسال می‌شود.</div>
                </label>

                <label class="field">
                    <span class="field-label">شناسه سیستمی ۳۲ کاراکتری</span>
                    <input class="input" name="user_system_id" maxlength="32" value="{{ old('user_system_id') }}" required>
                </label>

                <label class="field">
                    <span class="field-label">رمز عبور</span>
                    <input class="input" type="password" name="password" minlength="8" maxlength="20" autocomplete="new-password" required>
                </label>

                <button class="btn btn-primary btn-block" type="submit">ثبت‌نام و ارسال کد تأیید</button>
            </form>

            <div class="divider"></div>
            <p class="center muted" style="margin:0">قبلاً عضو شده‌اید؟ <a class="text-link" href="{{ route('login') }}">ورود</a></p>
        @endif
    </section>
</div>
@endsection
