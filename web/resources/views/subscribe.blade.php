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
    .type-tabs{display:flex;gap:8px;margin:0 0 16px}
    .type-tabs a{padding:8px 16px;border-radius:999px;border:1px solid var(--line-strong);text-decoration:none;color:var(--text);font-size:14px}
    .type-tabs a[aria-current]{background:var(--accent);border-color:var(--accent);color:#fff}
    .calc{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;padding:14px;border-radius:14px;background:var(--soft-card);border:1px solid var(--line);margin:4px 0 14px;text-align:center}
    .calc b{display:block;font-size:20px}.calc small{color:var(--muted)}
    .calc .save{color:var(--green)}
    input[type=range]{width:100%;accent-color:var(--accent)}
    @media(max-width:900px){.sub-wrap{grid-template-columns:1fr}}
    @media(max-width:560px){.plan-pick,.form-grid{grid-template-columns:1fr}}
</style>
@endpush

@section('content')
<div class="container page-shell">
    <span class="eyebrow">اشتراک اعتمادمحور</span>
    <h1 class="page-title">{{ $renew ? 'تمدید اشتراک '.$renew['code'] : 'ثبت اشتراک متن‌یاب' }}</h1>
    <p class="page-subtitle">پلن‌تان را انتخاب کنید، مبلغش را کارت‌به‌کارت کنید و فرم کوتاه زیر را بفرستید؛ کد اشتراک همان لحظه تقدیم‌تان می‌شود. 🌷</p>
    @if (! $renew && $settings['flags']['org_enabled'])
        <nav class="type-tabs" aria-label="نوع اشتراک">
            <a href="{{ route('subscribe') }}" @if ($type === 'personal') aria-current="page" @endif>👤 فردی</a>
            <a href="{{ route('subscribe', ['type' => 'org']) }}" @if ($type === 'org') aria-current="page" @endif>🏛 سازمانی (چند کاربر)</a>
        </nav>
    @endif

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
            <input type="hidden" name="type" value="{{ $type }}">

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

            @if ($type === 'org')
                <label class="field"><span class="field-label">تعداد کاربران: <b data-seats-out>{{ old('seats', $renew['seats'] ?? 10) }}</b></span>
                    <input type="range" name="seats" min="{{ $orgTiers['min'] }}" max="200" value="{{ old('seats', $renew['seats'] ?? 10) }}" data-seats></label>
                <div class="calc" aria-live="polite">
                    <div><small>هر کاربر</small><b data-per>—</b><small>تومان در کل دوره</small></div>
                    <div><small>جمع کل</small><b data-total>—</b><small>تومان</small></div>
                    <div class="save"><small>صرفه‌جویی نسبت به فردی</small><b data-save>—</b><small data-pct></small></div>
                </div>
                @unless ($renew)
                    <label class="field"><span class="field-label">نام سازمان یا مجموعه</span><input class="input" name="org" value="{{ old('org') }}" maxlength="100" required></label>
                @endunless
            @endif
            @unless ($renew)
                <div class="form-grid">
                    <label class="field"><span class="field-label">{{ $type === 'org' ? 'نام مسئول اشتراک' : 'نام' }}</span><input class="input" name="name" value="{{ old('name') }}" maxlength="80" autocomplete="name" required></label>
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
            <label class="check"><input type="checkbox" name="trust" value="1" required @checked(old('trust'))> <span>این پلن را برای {{ $type === 'org' ? 'مجموعه‌مان' : 'خودم' }} انتخاب کرده‌ام و پرداختش را، اگر هنوز انجام نداده‌ام، هر وقت برایم میسر شد انجام می‌دهم. 🌷</span></label>
            <button class="btn btn-primary btn-block" type="submit">{{ $renew ? 'ثبت تمدید' : ($type === 'org' ? 'ثبت و دریافت کد گروهی' : 'ثبت و دریافت کد اشتراک') }}</button>
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
(() => {
    const range = document.querySelector('[data-seats]');
    if (!range) return;
    const T = @json($orgTiers), plans = @json(collect($plans)->keyBy('id')->map(fn ($p) => $p['price']));
    const fa = n => new Intl.NumberFormat('fa-IR').format(n);
    const calc = () => {
        const seats = +range.value, plan = document.querySelector('input[name=plan]:checked')?.value;
        const price = plans[plan] || 0, f = seats >= 50 ? T.t3 : seats >= 10 ? T.t2 : T.t1;
        const total = Math.round(price * seats * f / 100 / 10000) * 10000;
        document.querySelector('[data-seats-out]').textContent = fa(seats);
        document.querySelector('[data-per]').textContent = fa(Math.round(total / seats / 1000) * 1000);
        document.querySelector('[data-total]').textContent = fa(total);
        document.querySelector('[data-save]').textContent = fa(price * seats - total);
        document.querySelector('[data-pct]').textContent = fa(100 - f) + '٪ تخفیف';
    };
    range.addEventListener('input', calc);
    document.querySelectorAll('input[name=plan]').forEach(r => r.addEventListener('change', calc));
    calc();
})();
document.querySelectorAll('[data-copy]').forEach(b => b.addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(b.dataset.copy); b.textContent = 'کپی شد ✓'; } catch (_) { b.textContent = b.dataset.copy; }
}));
</script>
@endpush
