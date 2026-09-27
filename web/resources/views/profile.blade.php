@extends('layouts.app')

@section('title', 'پروفایل | متن‌یاب')

@push('styles')
<style>
    .profile-head{display:flex;align-items:flex-start;justify-content:space-between;gap:24px;margin-bottom:22px}
    .profile-grid{display:grid;grid-template-columns:1.12fr .88fr;gap:14px;align-items:start}
    .stack{display:grid;gap:14px}
    .status-pill{display:inline-flex;align-items:center;gap:7px;padding:5px 9px;border-radius:999px;font-size:11px;font-weight:700;border:1px solid var(--line)}
    .status-pill:before{content:"";width:6px;height:6px;border-radius:50%}
    .status-active{color:var(--green);background:rgba(16,185,129,.07);border-color:rgba(52,211,153,.17)}
    .status-active:before{background:var(--green);box-shadow:0 0 13px #34d399}
    .status-off{color:var(--amber);background:rgba(245,158,11,.06);border-color:rgba(251,191,36,.15)}
    .status-off:before{background:var(--amber)}
    .section-label{font-size:13px;color:var(--text);font-weight:700;margin-bottom:13px}
    .subscription-cards{display:grid;gap:8px;margin-bottom:15px}
    .subscription-option{display:flex;justify-content:space-between;gap:15px;align-items:center;padding:13px;border:1px solid var(--line);border-radius:12px;background:var(--surface)}
    .subscription-option strong{font-size:13px}.subscription-option span{font-size:12px;color:var(--muted)}
    @media(max-width:900px){.profile-grid{grid-template-columns:1fr}.profile-head{flex-direction:column}}
</style>
@endpush

@section('content')
<div class="container page-shell">
    <div class="profile-head">
        <div>
            <span class="eyebrow">حساب کاربری</span>
            <h1 class="page-title" style="margin-bottom:3px">حساب شما</h1>
            <p class="page-subtitle">وضعیت اشتراک، سریال و سوابق خریدتان را در یک نگاه ببینید.</p>
        </div>
        <div class="nav-actions">
            <a class="btn" href="{{ route('password.change') }}">تغییر رمز عبور</a>
        </div>
    </div>

    @if (session('status'))
        <div class="alert alert-success">{{ session('status') }}</div>
    @endif
    @if ($errors->any())
        <div class="alert alert-error">{{ $errors->first() }}</div>
    @endif

    <div class="profile-grid">
        <div class="stack">
            <section class="panel panel-pad">
                <div style="display:flex;justify-content:space-between;align-items:center;gap:15px;margin-bottom:18px">
                    <div>
                        <div class="section-label" style="margin:0 0 3px">وضعیت حساب</div>
                        <div class="muted" style="font-size:12px">{{ $user->username }}</div>
                    </div>
                    @if ($isSubscriptionEnded)
                        <span class="status-pill status-off">بدون اشتراک فعال</span>
                    @else
                        <span class="status-pill status-active">اشتراک فعال</span>
                    @endif
                </div>

                <div class="stat-grid">
                    <div class="stat">
                        <div class="stat-label">موبایل</div>
                        <div class="stat-value" dir="ltr">{{ $user->phone_number }}</div>
                    </div>
                    <div class="stat">
                        <div class="stat-label">اشتراک فعلی</div>
                        <div class="stat-value">{{ $user->subscription?->subscription_title ?? '—' }}</div>
                    </div>
                    <div class="stat">
                        <div class="stat-label">انقضای مجوز</div>
                        <div class="stat-value" style="font-size:14px">{{ $user->license_end_time ? $user->license_end_time->format('Y-m-d') : '—' }}</div>
                    </div>
                </div>

                <div class="divider"></div>
                <div class="section-label">مجوزهای کلاینت ویندوز</div>
                @if ($clientLicenses['bypassed'])
                    <div class="alert alert-success">
                        دسترسی ویژه مدیریتی برای این حساب فعال است؛ سریال‌های زیر تا {{ $clientLicenses['expires_at'] }} معتبرند.
                    </div>
                @endif

                @if ($clientLicenses['legacy_system_id'] && $clientLicenses['legacy_serial'])
                    <div class="help" style="margin-bottom:6px">نسخه کلاسیک — System-ID</div>
                    <code class="serial">{{ $clientLicenses['legacy_system_id'] }}</code>
                    <div class="help" style="margin:10px 0 6px">سریال نسخه کلاسیک</div>
                    <code class="serial">{{ $clientLicenses['legacy_serial'] }}</code>
                @endif

                @if ($clientLicenses['kotlin_system_id'])
                    <div class="divider"></div>
                    <div class="help" style="margin-bottom:6px">نسخه جدید Kotlin — System-ID</div>
                    <code class="serial">{{ $clientLicenses['kotlin_system_id'] }}</code>
                    @if ($clientLicenses['kotlin_serial'])
                        <div class="help" style="margin:10px 0 6px">سریال نسخه Kotlin</div>
                        <code class="serial">{{ $clientLicenses['kotlin_serial'] }}</code>
                    @else
                        <div class="help">برای صدور سریال این شناسه، اشتراک فعال لازم است.</div>
                    @endif
                @else
                    <div class="help" style="margin-top:10px">
                        System-ID نسخه Kotlin هنوز برای این حساب ثبت نشده است؛ مدیر سامانه می‌تواند آن را از پنل مدیریت ثبت کند.
                    </div>
                @endif
            </section>

            <section class="panel panel-pad">
                <div class="panel-header" style="margin-bottom:18px">
                    <span class="eyebrow">خریدهای شما</span>
                    <h2 style="font-size:21px;margin:5px 0 0">تاریخچه خرید</h2>
                </div>

                @if ($history->isEmpty())
                    <div class="muted" style="font-size:13px">هنوز سابقه خریدی برای این حساب ثبت نشده است.</div>
                @else
                    <div class="table-wrap">
                        <table>
                            <thead><tr><th>تاریخ</th><th>اشتراک</th><th>مبلغ</th><th>کد پیگیری</th></tr></thead>
                            <tbody>
                            @foreach ($history as $item)
                                <tr>
                                    <td>{{ $item->subscription_buy_time?->format('Y-m-d H:i') ?? $item->subscription_buy_time }}</td>
                                    <td>{{ $item->subscription?->subscription_title ?? 'اشتراک' }}</td>
                                    <td>{{ number_format($item->amount_paid) }}</td>
                                    <td dir="ltr">{{ $item->tracking_code ?: '—' }}</td>
                                </tr>
                            @endforeach
                            </tbody>
                        </table>
                    </div>
                @endif
            </section>
        </div>

        <aside class="stack">
            <section class="panel panel-pad">
                <div class="panel-header" style="margin-bottom:17px">
                    <span class="eyebrow">اشتراک متن‌یاب</span>
                    <h2 style="font-size:21px;margin:5px 0 0">خرید یا تمدید اشتراک</h2>
                    <p class="page-subtitle">طرح مناسب را انتخاب کنید و برای خرید یا تمدید ادامه دهید.</p>
                </div>

                <form method="post" action="{{ route('buysubscription') }}">
                    @csrf
                    <label class="field">
                        <span class="field-label">طرح اشتراک</span>
                        <select class="select" name="subscription_id" required>
                            @foreach ($subscriptions as $subscription)
                                <option value="{{ $subscription->id }}">
                                    {{ $subscription->subscription_title }} — {{ number_format($subscription->subscription_price) }}
                                </option>
                            @endforeach
                        </select>
                    </label>

                    <label class="field">
                        <span class="field-label">کد تخفیف</span>
                        <input class="input" name="coupon_code" autocomplete="off" placeholder="اختیاری">
                    </label>

                    <button class="btn btn-primary btn-block" type="submit">ادامه خرید</button>
                </form>
            </section>

            <section class="panel panel-pad">
                <div class="section-label">سازگاری دو نسخه ویندوز</div>
                <div class="help">
                    نسخه کلاسیک و نسخه Kotlin می‌توانند System-ID متفاوت داشته باشند. پنل مدیریت هر دو شناسه را مستقل نگه می‌دارد
                    و برای هرکدام سریال سازگار با همان کلاینت صادر می‌کند.
                </div>
            </section>
        </aside>
    </div>
</div>
@endsection
