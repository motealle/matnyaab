<!doctype html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>ثبت‌نام | متن‌یاب</title>
</head>
<body style="font-family:Tahoma,sans-serif;max-width:620px;margin:3rem auto;padding:1rem">
<h1>ثبت‌نام متن‌یاب</h1>
@if ($errors->any())
    <div style="color:#a00;margin:1rem 0">{{ $errors->first() }}</div>
@endif
<form method="post" action="{{ route('register.submit') }}">
    @csrf
    <p><label>ایمیل / نام کاربری<br><input type="email" name="username" value="{{ old('username') }}" required></label></p>
    <p><label>نام و نام خانوادگی<br><input name="first_name" value="{{ old('first_name') }}" required></label></p>
    <p><label>شماره موبایل<br><input name="phone_number" inputmode="numeric" maxlength="11" value="{{ old('phone_number') }}" placeholder="09123456789" required></label></p>
    <p><label>شناسه سیستمی ۳۲ کاراکتری<br><input name="user_system_id" maxlength="32" value="{{ old('user_system_id') }}" required></label></p>
    <p><label>رمز عبور<br><input type="password" name="password" minlength="8" maxlength="20" required></label></p>
    <button type="submit">ثبت‌نام و ارسال کد</button>
</form>
<p><a href="{{ route('login') }}">قبلاً ثبت‌نام کرده‌اید؟ ورود</a></p>
</body>
</html>
