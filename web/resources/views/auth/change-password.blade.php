<!doctype html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>تغییر رمز عبور | متن‌یاب</title>
</head>
<body style="font-family:Tahoma,sans-serif;max-width:520px;margin:4rem auto;padding:1rem">
<h1>تغییر رمز عبور</h1>
@if ($errors->any())
    <div style="color:#a00;margin:1rem 0">{{ $errors->first() }}</div>
@endif
<form method="post" action="{{ route('password.change.submit') }}">
    @csrf
    <p><label>رمز عبور فعلی<br><input type="password" name="current_password" required autofocus></label></p>
    <p><label>رمز عبور جدید<br><input type="password" name="password" minlength="8" required></label></p>
    <p><label>تکرار رمز عبور جدید<br><input type="password" name="password_confirmation" minlength="8" required></label></p>
    <button type="submit">ذخیره رمز جدید</button>
</form>
<p><a href="{{ route('profile') }}">بازگشت به پروفایل</a></p>
</body>
</html>
