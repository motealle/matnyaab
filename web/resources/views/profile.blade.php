<!doctype html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>پروفایل | متن‌یاب</title>
</head>
<body style="font-family:Tahoma,sans-serif;max-width:900px;margin:3rem auto;padding:1rem">
<h1>پروفایل</h1>

@if (session('status'))
    <p style="color:#075">{{ session('status') }}</p>
@endif
@if ($errors->any())
    <p style="color:#a00">{{ $errors->first() }}</p>
@endif

<p>کاربر: <strong>{{ $user->username }}</strong></p>
<p>موبایل: {{ $user->phone_number }}</p>
<p>شناسه سیستم: {{ $user->user_system_id }}</p>
<p>وضعیت اشتراک: {{ $isSubscriptionEnded ? 'پایان‌یافته / بدون اشتراک' : 'فعال' }}</p>
@if ($user->subscription)
    <p>اشتراک: {{ $user->subscription->subscription_title }}</p>
@endif
@if ($user->user_serial_number)
    <p>سریال فعلی: <code dir="ltr">{{ $user->user_serial_number }}</code></p>
@endif

<h2>خرید اشتراک</h2>
<form method="post" action="{{ route('buysubscription') }}">
    @csrf
    <p>
        <label>طرح اشتراک
            <select name="subscription_id" required>
            @foreach ($subscriptions as $subscription)
                <option value="{{ $subscription->id }}">
                    {{ $subscription->subscription_title }} —
                    {{ number_format($subscription->subscription_price) }}
                </option>
            @endforeach
            </select>
        </label>
    </p>
    <p><label>کد تخفیف <input name="coupon_code"></label></p>
    <button type="submit">ادامه خرید</button>
</form>

<h2>تاریخچه خرید</h2>
<ul>
@forelse ($history as $item)
    <li>{{ $item->subscription_buy_time }} — {{ number_format($item->amount_paid) }}</li>
@empty
    <li>تاریخچه‌ای ثبت نشده است.</li>
@endforelse
</ul>

<p style="margin-top:18px"><a href="{{ route('password.change') }}">تغییر رمز عبور</a></p>

<form method="post" action="{{ route('logout') }}">
    @csrf
    <button type="submit">خروج</button>
</form>
</body>
</html>
