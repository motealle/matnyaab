@extends('layouts.app')

@section('title', 'خطای سرویس | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad center">
        <span class="eyebrow">SERVER ERROR</span>
        <h1 class="page-title" style="font-size:42px;margin-top:18px">یک خطای موقت رخ داد.</h1>
        <p class="page-subtitle" style="max-width:440px;margin:0 auto 24px">داده‌های شما تغییری نکرده‌اند. چند لحظه دیگر دوباره تلاش کنید.</p>
        <a class="btn btn-primary" href="{{ route('home') }}">صفحه اصلی</a>
    </section>
</div>
@endsection
