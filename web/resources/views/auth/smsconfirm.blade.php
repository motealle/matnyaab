<!doctype html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>تأیید موبایل | متن‌یاب</title>
</head>
<body style="font-family:Tahoma,sans-serif;max-width:520px;margin:4rem auto;padding:1rem">
<h1>تأیید شماره موبایل</h1>
<p>کد چهاررقمی ارسال‌شده به شماره {{ auth()->user()->phone_number }} را وارد کنید.</p>
@if ($errors->any())
    <div style="color:#a00;margin:1rem 0">{{ $errors->first() }}</div>
@endif
<form method="post" action="{{ route('smsconfirm.submit') }}">
    @csrf
    <p><input name="confirm_code" inputmode="numeric" minlength="4" maxlength="4" required autofocus></p>
    <button type="submit">تأیید</button>
</form>
</body>
</html>
