@extends('layouts.app')

@section('title', 'خطای سرویس | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad center">
        <span class="eyebrow">خطای موقت</span>
        <h1 class="page-title" style="font-size:42px;margin-top:18px">در انجام درخواست مشکلی پیش آمد.</h1>
        <p class="page-subtitle" style="max-width:440px;margin:0 auto 24px">لطفاً چند لحظه دیگر دوباره تلاش کنید. اگر مشکل ادامه داشت، از صفحه اصلی مسیر موردنظر را دوباره باز کنید.</p>
        <a class="btn btn-primary" href="{{ route('home') }}">صفحه اصلی</a>
    </section>
</div>
@endsection
