@extends('layouts.app')

@section('title', 'صفحه پیدا نشد | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad center">
        <span class="eyebrow">404 / NOT FOUND</span>
        <h1 class="page-title" style="font-size:52px;margin-top:18px">اینجا چیزی نیست.</h1>
        <p class="page-subtitle" style="max-width:420px;margin:0 auto 24px">آدرسی که باز کرده‌اید وجود ندارد یا در جریان انتقال سرویس تغییر کرده است.</p>
        <a class="btn btn-primary" href="{{ route('home') }}">بازگشت به صفحه اصلی</a>
    </section>
</div>
@endsection
