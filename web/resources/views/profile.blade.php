<!doctype html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>پروفایل | متن‌یاب</title>
</head>
<body style="font-family:Tahoma,sans-serif;max-width:900px;margin:3rem auto;padding:1rem">
<h1>پروفایل</h1>
<p>کاربر: <strong>{{ $user->username }}</strong></p>
<p>موبایل: {{ $user->phone_number }}</p>
<p>شناسه سیستم: {{ $user->user_system_id }}</p>
<p>وضعیت اشتراک: {{ $isSubscriptionEnded ? 'پایان‌یافته / بدون اشتراک' : 'فعال' }}</p>
@if ($user->subscription)
    <p>اشتراک: {{ $user->subscription->subscription_title }}</p>
@endif

<h2>اشتراک‌های فعال</h2>
<ul>
@foreach ($subscriptions as $subscription)
    <li>{{ $subscription->subscription_title }} — {{ number_format($subscription->subscription_price) }}</li>
@endforeach
</ul>

<h2>تاریخچه خرید</h2>
<ul>
@forelse ($history as $item)
    <li>{{ $item->subscription_buy_time }} — {{ number_format($item->amount_paid) }}</li>
@empty
    <li>تاریخچه‌ای ثبت نشده است.</li>
@endforelse
</ul>

<form method="post" action="{{ route('logout') }}">
    @csrf
    <button type="submit">خروج</button>
</form>
</body>
</html>
