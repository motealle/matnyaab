@extends('layouts.app')

@section('title', 'صفحه پیدا نشد | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad center">
        <span class="eyebrow">صفحه پیدا نشد</span>
        <h1 class="page-title" style="font-size:52px;margin-top:18px">صفحه‌ای که می‌خواستید پیدا نشد.</h1>
        <p class="page-subtitle" style="max-width:420px;margin:0 auto 24px">ممکن است نشانی تغییر کرده باشد یا صفحه دیگر در دسترس نباشد.</p>
        <a class="btn btn-primary" href="{{ route('home') }}">بازگشت به صفحه اصلی</a>
    </section>
</div>
@endsection
