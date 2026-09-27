@extends('layouts.app')

@section('title', 'مدیریت | متن‌یاب')

@push('styles')
<style>
    .admin-head{display:flex;justify-content:space-between;gap:24px;align-items:end;margin-bottom:22px}
    .admin-grid{display:grid;grid-template-columns:1fr;gap:14px}
    .user-row{display:grid;grid-template-columns:1.2fr .8fr .8fr auto;gap:12px;align-items:center;padding:14px;border:1px solid var(--line);border-radius:13px;background:var(--surface)}
    .user-name{font-weight:750;font-size:13px}.user-meta{color:var(--muted);font-size:11px;overflow-wrap:anywhere}
    .gift-form{display:flex;gap:8px;align-items:center}.gift-form .select{height:40px;min-width:160px}
    .license-policy-row{display:flex;justify-content:space-between;gap:18px;align-items:center}
    .license-form{grid-column:1/-1;display:grid;grid-template-columns:auto minmax(260px,1fr) auto;gap:10px;align-items:end;padding-top:12px;border-top:1px solid var(--line)}
    .license-check{display:flex;align-items:center;gap:8px;min-height:42px;font-size:12px;color:var(--muted)}
    @media(max-width:900px){.user-row{grid-template-columns:1fr}.gift-form{align-items:stretch;flex-direction:column}.gift-form .select{width:100%}.license-policy-row{align-items:stretch;flex-direction:column}.license-form{grid-template-columns:1fr}}
</style>
@endpush

@section('content')
<div class="container page-shell">
    <div class="admin-head">
        <div>
            <span class="eyebrow">مدیریت متن‌یاب</span>
            <h1 class="page-title" style="margin-bottom:3px">مدیریت کاربران و اشتراک‌ها</h1>
            <p class="page-subtitle">کاربران را پیدا کنید و در صورت نیاز اشتراک موردنظر را برایشان فعال کنید.</p>
        </div>
    </div>

    @if (session('status'))
        <div class="alert alert-success">{{ session('status') }}</div>
    @endif
    @if ($errors->any())
        <div class="alert alert-error">{{ $errors->first() }}</div>
    @endif

    <div class="stat-grid" style="margin-bottom:14px">
        <div class="stat"><div class="stat-label">کل کاربران</div><div class="stat-value">{{ number_format($counts['users']) }}</div></div>
        <div class="stat"><div class="stat-label">موبایل تأییدشده</div><div class="stat-value">{{ number_format($counts['confirmed']) }}</div></div>
        <div class="stat"><div class="stat-label">سوابق خرید و اشتراک</div><div class="stat-value">{{ number_format($counts['histories']) }}</div></div>
    </div>

    <section class="panel panel-pad" style="margin-bottom:14px">
        <div class="license-policy-row">
            <div>
                <div class="section-label" style="margin-bottom:4px">بای‌پس سراسری لایسنس</div>
                <div class="muted" style="font-size:12px">
                    وقتی فعال باشد، کاربران دارای شناسه سیستم می‌توانند سریال مدیریتی بلندمدت دریافت کنند؛
                    بدون تغییر الگوریتم کلاینت قدیمی.
                </div>
            </div>
            <form method="post" action="{{ route('admin.license.global') }}">
                @csrf
                <input type="hidden" name="enabled" value="{{ $licensePolicy['global_bypass'] ? '0' : '1' }}">
                <button class="btn {{ $licensePolicy['global_bypass'] ? 'btn-danger' : 'btn-primary' }}" type="submit">
                    {{ $licensePolicy['global_bypass'] ? 'غیرفعال‌کردن بای‌پس سراسری' : 'فعال‌کردن بای‌پس سراسری' }}
                </button>
            </form>
        </div>
    </section>

    <section class="panel panel-pad">
        <form method="get" action="{{ route('admin.dashboard') }}" style="display:flex;gap:8px;margin-bottom:18px">
            <input class="input" name="q" value="{{ $query }}" placeholder="ایمیل، نام، موبایل یا شناسه سیستم">
            <button class="btn btn-primary" type="submit">جستجو</button>
        </form>

        <div class="admin-grid">
            @forelse ($users as $account)
                <div class="user-row">
                    <div>
                        <div class="user-name">{{ trim($account->first_name.' '.$account->last_name) ?: $account->username }}</div>
                        <div class="user-meta">{{ $account->username }}</div>
                    </div>
                    <div>
                        <div class="user-meta">موبایل</div>
                        <div dir="ltr" style="font-size:12px">{{ $account->phone_number }}</div>
                    </div>
                    <div>
                        <div class="user-meta">اشتراک / انقضا</div>
                        <div style="font-size:12px">{{ $account->subscription?->subscription_title ?? '—' }}</div>
                        <div class="user-meta">{{ $account->license_end_time?->format('Y-m-d') ?? '—' }}</div>
                    </div>
                    <form class="gift-form" method="post" action="{{ route('admin.gift') }}">
                        @csrf
                        <input type="hidden" name="user_id" value="{{ $account->id }}">
                        <select class="select" name="subscription_id" required>
                            @foreach ($subscriptions as $subscription)
                                <option value="{{ $subscription->id }}">{{ $subscription->subscription_title }}</option>
                            @endforeach
                        </select>
                        <button class="btn" type="submit">فعال‌سازی اشتراک</button>
                    </form>

                    @php($policy = $licensePolicy['users'][(string) $account->id] ?? ['bypass' => false, 'kotlin_system_id' => null])
                    <form class="license-form" method="post" action="{{ route('admin.license.user') }}">
                        @csrf
                        <input type="hidden" name="user_id" value="{{ $account->id }}">
                        <label class="license-check">
                            <input type="checkbox" name="bypass" value="1" @checked($policy['bypass'])>
                            بای‌پس لایسنس فقط برای این کاربر
                        </label>
                        <label class="field" style="margin:0">
                            <span class="field-label">System-ID نسخه Kotlin (۳۲ کاراکتر)</span>
                            <input class="input" dir="ltr" name="kotlin_system_id" maxlength="32"
                                   value="{{ $policy['kotlin_system_id'] ?? '' }}"
                                   placeholder="مثال: 0123456789ABCDEF0123456789ABCDEF">
                            <span class="help">System-ID نسخه کلاسیک: <span dir="ltr">{{ $account->user_system_id ?: 'ثبت نشده' }}</span></span>
                        </label>
                        <button class="btn" type="submit">ذخیره تنظیمات لایسنس</button>
                    </form>
                </div>
            @empty
                <div class="muted">کاربری پیدا نشد.</div>
            @endforelse
        </div>
    </section>
</div>
@endsection
