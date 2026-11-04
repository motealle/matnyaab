@extends('layouts.app')

@section('title', 'ورود مدیر | متن‌یاب')

@section('content')
<div class="container page-shell" style="max-width:440px">
    <section class="panel panel-pad" style="margin-top:40px">
        <span class="eyebrow">پنل مدیریت</span>
        <h1 class="page-title" style="font-size:26px;margin:6px 0 4px">ورود مدیر متن‌یاب</h1>
        <p class="page-subtitle">مدیرانی که با حساب کاربری سایت (دسترسی مدیر) وارد می‌شوند، از صفحهٔ «ورود» معمولی هم به پنل دسترسی دارند.</p>
        @if ($errors->any())<div class="alert alert-error">{{ $errors->first() }}</div>@endif
        <form method="post" action="{{ route('admin.login.submit') }}">
            @csrf
            <label class="field"><span class="field-label">نام کاربری</span>
                <input class="input" name="username" dir="ltr" value="{{ old('username') }}" autocomplete="username" required autofocus></label>
            <label class="field"><span class="field-label">رمز عبور</span>
                <input class="input" type="password" name="password" dir="ltr" autocomplete="current-password" required></label>
            <button class="btn btn-primary btn-block" type="submit">ورود به پنل</button>
        </form>
    </section>
</div>
@endsection
