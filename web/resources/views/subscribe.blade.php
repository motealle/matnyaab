@extends('layouts.app')

@section('title', ($renew ? 'تمدید اشتراک' : 'ثبت اشتراک').' | متن‌یاب')
@section('meta_description', 'ثبت یا تمدید اشتراک اعتمادمحور متن‌یاب: ماهانه، شش‌ماهه و یک‌ساله؛ پرداخت کارت‌به‌کارت و دریافت فوری کد اشتراک.')

@php $fa = fn ($n) => strtr(number_format((float) $n), ['0'=>'۰','1'=>'۱','2'=>'۲','3'=>'۳','4'=>'۴','5'=>'۵','6'=>'۶','7'=>'۷','8'=>'۸','9'=>'۹',','=>'٬']); @endphp

@push('styles')
<style>
    .sub-wrap{display:grid;grid-template-columns:1.1fr .9fr;gap:20px;align-items:start}
    .plan-pick{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-bottom:16px}
    .plan-pick label{position:relative;cursor:pointer;padding:16px;border:1.5px solid var(--line-strong);border-radius:16px;background:var(--surface);display:grid;gap:2px;text-align:center}
    .plan-pick input{position:absolute;opacity:0}
    .plan-pick label:has(input:checked){border-color:var(--accent);box-shadow:0 0 0 3px rgba(88,176,237,.18)}
    .plan-pick b{font-size:16px}.plan-pick strong{font-size:22px}.plan-pick small{color:var(--muted)}
    .plan-pick .best{position:absolute;top:-10px;right:12px;background:var(--green);color:#06172b;font-size:11px;font-weight:700;padding:1px 8px;border-radius:999px}
    .pay-card{padding:16px;border-radius:16px;color:#fff;background:linear-gradient(135deg,#0b4f8a,#1d7fd1);margin-bottom:10px}
    .pay-card .num{font-size:22px;font-weight:700;letter-spacing:.06em;direction:ltr;text-align:left}
    .pay-card.alt{background:linear-gradient(135deg,#334155,#475569)}
    .trust{padding:12px 14px;border:1px dashed var(--line-strong);border-radius:14px;color:var(--muted);font-size:13px;line-height:2}
    .form-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px 12px}
    .check{display:flex;gap:8px;align-items:flex-start;font-size:13px;color:var(--muted);margin:12px 0}
    .check input{margin-top:5px;width:18px;height:18px;accent-color:var(--accent)}
    .hp-field{position:absolute!important;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}
    @media(max-width:900px){.sub-wrap{grid-template-columns:1fr}}
    @media(max-width:560px){.plan-pick,.form-grid{grid-template-columns:1fr}}
</style>
@endpush

@section('content')
<div class="container page-shell">
    <span class="eyebrow">اشتراک اعتمادمحور</span>
    <h1 class="page-title">{{ $renew ? 'تمدید اشتراک '.$renew['code'] : 'ثبت اشتراک متن‌یاب' }}</h1>
    <p class="page-subtitle">پلن را انتخاب کنید، مبلغ را کارت‌به‌کارت بپردازید و فرم را بفرستید؛ کد اشتراک همان لحظه به شما داده می‌شود.</p>

    @if ($renew)
        <div class="alert {{ $renew['state'] === 'active' ? 'alert-success' : 'alert-error' }}">
            {{ $renew['name'] }} عزیز، دورهٔ فعلی شما {{ $renew['state'] === 'expired' ? 'به پایان رسیده است' : 'تا '.$renew['end'].' ('.$fa($renew['days_left']).' روز دیگر) ادامه دارد' }}. دورهٔ تازه {{ $renew['state'] === 'expired' ? 'از امروز' : 'بلافاصله پس از پایان دورهٔ فعلی' }} آغاز می‌شود و کدتان تغییر نمی‌کند.
        </div>
    @endif
    @if ($errors->any())<div class="alert alert-error">{{ $errors->first() }}</div>@endif

    <div class="sub-wrap">
        <form class="panel panel-pad" method="post" action="{{ route('subscribe.store') }}">
            @csrf {!! \App\Support\SpamGuard::fields() !!}
            @if ($renew)<input type="hidden" name="renew_code" value="{{ $renew['code'] }}">@endif

            <div class="plan-pick" role="radiogroup" aria-label="انتخاب پلن">
                @foreach ($plans as $i => $p)
                    <label>
                        <input type="radio" name="plan" value="{{ $p['id'] }}" @checked(old('plan', $selected ?: ($plans[count($plans) - 1]['id'] ?? '')) === $p['id']) required>
                        @if ($i === count($plans) - 1 && count($plans) > 1)<span class="best">به‌صرفه‌ترین</span>@endif
                        <b>{{ $p['title'] }}</b>
                        <strong>{{ $fa($p['price']) }}</strong>
                        <small>تومان · {{ $fa($p['days']) }} روز</small>
                        @if ($p['days'] > 31)<small>ماهی حدود {{ $fa(round($p['price'] / ($p['days'] / 30) / 1000) * 1000) }}</small>@endif
                    </label>
                @endforeach
            </div>

            @unless ($renew)
                <div class="form-grid">
                    <label class="field"><span class="field-label">نام</span><input class="input" name="name" value="{{ old('name') }}" maxlength="80" autocomplete="name" required></label>
                    <label class="field"><span class="field-label">موبایل یا ایمیل</span><input class="input" name="contact" dir="auto" value="{{ old('contact') }}" maxlength="120" required></label>
                </div>
            @endunless
            <div class="form-grid">
                <label class="field"><span class="field-label">کد پیگیری واریز @unless ($settings['flags']['require_tracking_code'])<small class="muted">(اختیاری)</small>@endunless</span><input class="input" name="tracking_code" dir="ltr" value="{{ old('tracking_code') }}" maxlength="40" @if ($settings['flags']['require_tracking_code']) required @endif></label>
                <label class="field"><span class="field-label">۴ رقم آخر کارت پرداخت‌کننده <small class="muted">(اختیاری)</small></span><input class="input" name="card_last4" dir="ltr" inputmode="numeric" maxlength="4" value="{{ old('card_last4') }}"></label>
            </div>
            @unless ($renew)
                <label class="field"><span class="field-label">روز شروع استفاده <small class="muted">(خالی = امروز)</small></span><input class="input" type="date" name="start" value="{{ old('start') }}"></label>
            @endunless
            <label class="check"><input type="checkbox" name="trust" value="1" required @checked(old('trust'))> <span>اعلام می‌کنم مبلغ این پلن را پرداخت کرده‌ام یا به‌زودی پرداخت می‌کنم. می‌دانم این ثبت بررسی نمی‌شود و بر اعتماد بنا شده است.</span></label>
            <button class="btn btn-primary btn-block" type="submit">{{ $renew ? 'ثبت تمدید' : 'ثبت و دریافت کد اشتراک' }}</button>
        </form>

        <aside>
            @foreach ($cards as $i => $c)
                <div class="pay-card {{ $i ? 'alt' : '' }}">
                    <small>{{ $i ? 'کارت جایگزین' : 'شماره کارت برای پرداخت' }}</small>
                    <div class="num" data-card-number="{{ preg_replace('/\D/', '', $c['number']) }}">{{ \App\Services\SiteSettings::cardGroups($c['number']) }}</div>
                    <div>به نام {{ $c['holder'] }}</div>
                    <button class="btn btn-sm" type="button" data-copy="{{ preg_replace('/\D/', '', $c['number']) }}" style="margin-top:8px;background:rgba(255,255,255,.16);color:#fff;border-color:rgba(255,255,255,.3)">کپی شماره کارت</button>
                </div>
            @endforeach
            <div class="trust">🤝 {{ $settings['texts']['trust_note'] }}</div>
        </aside>
    </div>
</div>
@endsection

@push('scripts')
<script>
document.querySelectorAll('[data-copy]').forEach(b => b.addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(b.dataset.copy); b.textContent = 'کپی شد ✓'; } catch (_) { b.textContent = b.dataset.copy; }
}));
</script>
@endpush
