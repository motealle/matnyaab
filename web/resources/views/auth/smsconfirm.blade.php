@extends('layouts.app')

@section('title', 'تأیید موبایل | متن‌یاب')

@section('content')
<div class="container auth-shell">
    <section class="panel panel-pad">
        <div class="panel-header">
            <span class="eyebrow">VERIFY MOBILE</span>
            <h1 class="page-title">تأیید شماره موبایل</h1>
            <p class="page-subtitle">کد چهاررقمی ارسال‌شده به <span dir="ltr">{{ auth()->user()->phone_number }}</span> را وارد کنید.</p>
        </div>

        @if ($errors->any())
            <div class="alert alert-error">{{ $errors->first() }}</div>
        @endif

        <form method="post" action="{{ route('smsconfirm.submit') }}">
            @csrf
            <label class="field">
                <span class="field-label">کد تأیید</span>
                <input class="input" style="direction:ltr;text-align:center;font-size:24px;letter-spacing:.5em" name="confirm_code" inputmode="numeric" minlength="4" maxlength="4" autocomplete="one-time-code" required autofocus>
            </label>
            <button class="btn btn-primary btn-block" type="submit">تأیید شماره</button>
        </form>
    </section>
</div>
@endsection
