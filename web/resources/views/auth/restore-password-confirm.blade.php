@extends('layouts.app')

@section('title', 'تنظیم رمز جدید | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad">
        <div class="panel-header">
            <span class="eyebrow">انتخاب رمز تازه</span>
            <h1 class="page-title">رمز عبور جدید</h1>
            <p class="page-subtitle">برای حساب {{ $account->username }} رمز عبور تازه‌ای انتخاب کنید.</p>
        </div>

        @if ($errors->any())
            <div class="alert alert-error">{{ $errors->first() }}</div>
        @endif

        <form method="post" action="{{ route('password.restore.confirm.submit', ['user' => $account->id, 'expires' => $expires, 'token' => $token]) }}">
            @csrf
            <label class="field">
                <span class="field-label">رمز جدید</span>
                <input class="input" type="password" name="password" minlength="8" maxlength="72" autocomplete="new-password" required autofocus>
            </label>
            <label class="field">
                <span class="field-label">تکرار رمز جدید</span>
                <input class="input" type="password" name="password_confirmation" minlength="8" maxlength="72" autocomplete="new-password" required>
            </label>
            <button class="btn btn-primary btn-block" type="submit">ذخیره رمز جدید</button>
        </form>
    </section>
</div>
@endsection
