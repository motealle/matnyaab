<!doctype html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>ورود | متن‌یاب</title>
</head>
<body style="font-family:Tahoma,sans-serif;max-width:520px;margin:4rem auto;padding:1rem">
<h1>ورود به متن‌یاب</h1>
@if ($errors->any())
    <div style="color:#a00;margin:1rem 0">{{ $errors->first() }}</div>
@endif
<form method="post" action="{{ route('login.submit') }}">
    @csrf
    <p><label>نام کاربری<br><input name="username" value="{{ old('username') }}" required autofocus></label></p>
    <p><label>رمز عبور<br><input type="password" name="password" required></label></p>
    <button type="submit">ورود</button>
</form>
<p><a href="{{ route('register') }}">حساب ندارید؟ ثبت‌نام</a></p>
</body>
</html>
