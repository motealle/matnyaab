@extends('layouts.app')

@section('title', 'تغییر رمز عبور | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad">
        <div class="panel-header">
            <span class="eyebrow">SECURITY</span>
            <h1 class="page-title">تغییر رمز عبور</h1>
            <p class="page-subtitle">پس از تأیید رمز فعلی، رمز جدید حساب شما ذخیره می‌شود.</p>
        </div>

        @if ($errors->any())
            <div class="alert alert-error">{{ $errors->first() }}</div>
        @endif
        @if (session('status'))
            <div class="alert alert-success">{{ session('status') }}</div>
        @endif

        <form method="post" action="{{ route('password.change.submit') }}">
            @csrf
            <label class="field">
                <span class="field-label">رمز عبور فعلی</span>
                <input class="input" type="password" name="current_password" autocomplete="current-password" required autofocus>
            </label>
            <label class="field">
                <span class="field-label">رمز عبور جدید</span>
                <input class="input" type="password" name="password" minlength="8" autocomplete="new-password" required>
            </label>
            <label class="field">
                <span class="field-label">تکرار رمز عبور جدید</span>
                <input class="input" type="password" name="password_confirmation" minlength="8" autocomplete="new-password" required>
            </label>
            <button class="btn btn-primary btn-block" type="submit">ذخیره رمز جدید</button>
        </form>

        <div class="divider"></div>
        <p class="center" style="margin:0"><a class="text-link" href="{{ route('profile') }}">بازگشت به پروفایل</a></p>
    </section>
</div>
@endsection
