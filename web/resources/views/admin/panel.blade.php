@extends('layouts.app')

@section('title', 'پنل مدیریت | متن‌یاب')

@php
    $fa = fn ($n) => strtr(number_format((float) $n), ['0'=>'۰','1'=>'۱','2'=>'۲','3'=>'۳','4'=>'۴','5'=>'۵','6'=>'۶','7'=>'۷','8'=>'۸','9'=>'۹',','=>'٬']);
    $stateLabel = ['active' => 'فعال', 'expiring' => 'نزدیک تمدید', 'expired' => 'پایان‌یافته', 'cancelled' => 'لغوشده'];
    $flagLabels = [
        'subscribe_enabled' => 'فرم ثبت اشتراک در سایت فعال باشد',
        'require_tracking_code' => 'ثبت کد پیگیری واریز اجباری باشد',
        'trial_enabled' => 'گزینهٔ «سه روز مهمانی» در برنامه فعال باشد',
        'contact_enabled' => 'فرم پشتیبانی در صفحهٔ اصلی فعال باشد',
        'feature_requests_enabled' => 'فرم پیشنهاد قابلیت فعال باشد',
        'client_messages_enabled' => 'پیام‌های مدیر در برنامه نمایش داده شود',
        'announcement_enabled' => 'نوار اطلاعیه بالای صفحهٔ اصلی نمایش داده شود',
    ];
    $targets = ['all' => 'همهٔ کاربران برنامه', 'expiring' => 'نزدیک موعد تمدید', 'expired' => 'اشتراک پایان‌یافته', 'trial' => 'در حال مهمانی سه‌روزه', 'no_code' => 'بدون کد اشتراک'];
@endphp

@push('styles')
<style>
    .adm{display:grid;gap:16px}
    .adm-head{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}
    .adm-head h1{margin:0;font-size:26px;font-weight:600}
    .adm-tabs{display:flex;gap:6px;overflow-x:auto;padding:4px;border:1px solid var(--line);border-radius:14px;background:var(--surface);scrollbar-width:none;position:sticky;top:76px;z-index:5}
    .adm-tabs a{white-space:nowrap;padding:8px 14px;border-radius:10px;color:var(--muted);text-decoration:none;font-size:14px;font-weight:500}
    .adm-tabs a[aria-current="page"]{background:var(--accent);color:#fff}
    .adm-tabs .dot{display:inline-block;min-width:18px;padding:0 5px;margin-right:4px;border-radius:9px;background:var(--red);color:#fff;font-size:11px;text-align:center}
    .kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
    .kpi{padding:16px;border:1px solid var(--line);border-radius:16px;background:var(--surface)}
    .kpi small{display:block;color:var(--muted);font-size:12px}.kpi b{display:block;font-size:24px;font-weight:700;margin-top:4px}
    .kpi.good b{color:var(--green)}.kpi.warn b{color:var(--amber)}.kpi.bad b{color:var(--red)}
    .card{padding:18px;border:1px solid var(--line);border-radius:16px;background:var(--surface)}
    .card h2{font-size:17px;margin:0 0 12px;font-weight:600}
    .grid2{display:grid;grid-template-columns:1fr 1fr;gap:16px}
    .tbl{width:100%;border-collapse:collapse;font-size:13px}
    .tbl th{color:var(--muted);font-weight:600;text-align:right;padding:8px;border-bottom:1px solid var(--line)}
    .tbl td{padding:9px 8px;border-bottom:1px solid var(--line);vertical-align:top}
    .pill{display:inline-block;padding:2px 9px;border-radius:999px;font-size:11px;font-weight:700}
    .st-active{background:rgba(52,211,153,.13);color:var(--green)}.st-expiring{background:rgba(251,113,133,.14);color:var(--red)}
    .st-expired{background:rgba(251,191,36,.14);color:var(--amber)}.st-cancelled{background:var(--line);color:var(--muted)}
    .acts{display:flex;gap:6px;flex-wrap:wrap}.acts form{margin:0}
    .btn-sm{padding:5px 10px;font-size:12px;border-radius:9px}
    .row{display:flex;gap:10px;flex-wrap:wrap;align-items:end}.row .field{flex:1;min-width:150px;margin:0}
    .switch{display:flex;gap:10px;align-items:center;padding:10px 0;border-bottom:1px solid var(--line);font-size:14px;cursor:pointer}
    .switch input{width:20px;height:20px;accent-color:var(--accent)}
    .muted{color:var(--muted)}.mono{font-family:Consolas,monospace;direction:ltr;display:inline-block}
    .msg{padding:12px;border:1px solid var(--line);border-radius:12px;margin-bottom:10px}
    .msg.new{border-color:var(--accent)}
    .msg p{margin:6px 0;white-space:pre-wrap;font-size:14px}
    @media(max-width:900px){.kpis{grid-template-columns:1fr 1fr}.grid2{grid-template-columns:1fr}}
    @media(max-width:640px){
        .tbl thead{display:none}.tbl tr{display:block;border:1px solid var(--line);border-radius:12px;margin-bottom:10px;padding:6px}
        .tbl td{display:flex;justify-content:space-between;gap:10px;border:0;padding:5px 6px}
        .tbl td:before{content:attr(data-l);color:var(--muted);font-size:12px}
    }
</style>
@endpush

@section('content')
<div class="container page-shell adm">
    <div class="adm-head">
        <div><span class="eyebrow">پنل مدیریت متن‌یاب</span><h1>{{ $tabs[$tab] }}</h1></div>
        <div class="acts">
            <a class="btn btn-sm" href="{{ route('admin.dashboard', ['tab' => 'users']) }}">کاربران حساب‌دار قدیمی</a>
            <form method="post" action="{{ route('admin.logout') }}">@csrf<button class="btn btn-sm" type="submit">خروج</button></form>
        </div>
    </div>

    <nav class="adm-tabs" aria-label="بخش‌های پنل">
        @foreach ($tabs as $k => $label)
            <a href="{{ route('admin.dashboard', ['tab' => $k]) }}" @if ($tab === $k) aria-current="page" @endif>{{ $label }}@if ($k === 'inbox' && $inboxNew)<span class="dot">{{ $fa($inboxNew) }}</span>@endif @if ($k === 'overview' && $stats['need_contact'])<span class="dot">{{ $fa($stats['need_contact']) }}</span>@endif</a>
        @endforeach
    </nav>

    @if (session('status'))<div class="alert alert-success">{{ session('status') }}</div>@endif
    @if ($errors->any())<div class="alert alert-error">{{ $errors->first() }}</div>@endif

    @if ($tab === 'overview')
        <div class="kpis">
            <div class="kpi good"><small>اشتراک‌های فعال</small><b>{{ $fa($stats['active']) }}</b></div>
            <div class="kpi"><small>درآمد اعلام‌شدهٔ ۳۰ روز اخیر (تومان)</small><b>{{ $fa($stats['revenue_30']) }}</b></div>
            <div class="kpi"><small>درآمد ماهانهٔ تکرارشونده (برآورد)</small><b>{{ $fa($stats['mrr']) }}</b></div>
            <div class="kpi bad"><small>نیازمند پیگیری تمدید</small><b>{{ $fa($stats['need_contact']) }}</b></div>
            <div class="kpi"><small>برنامه‌های فعال امروز</small><b>{{ $fa($installs['day']) }}</b></div>
            <div class="kpi"><small>برنامه‌های فعال ۷ روز اخیر</small><b>{{ $fa(max($installs['week'], $legacyActive)) }}</b></div>
            <div class="kpi warn"><small>فعال ۷ روز اخیر بدون اشتراک</small><b>{{ $fa($installs['week_unpaid'] + $installs['week_trial']) }}</b></div>
            <div class="kpi"><small>تمدیدها / کل درآمد اعلام‌شده</small><b>{{ $fa($stats['renewals']) }} · {{ $fa($stats['revenue_total']) }}</b></div>
        </div>

        <section class="card">
            <h2>موعدهای تمدید — به این کاربران پیام بدهید</h2>
            @if (empty($followUp))
                <p class="muted">فعلاً موعد تمدید نزدیکی نیست. 🌿</p>
            @else
                <table class="tbl"><thead><tr><th>کاربر</th><th>راه ارتباطی</th><th>پلن</th><th>پایان</th><th>وضعیت</th><th>اقدام</th></tr></thead><tbody>
                @foreach ($followUp as $v)
                    <tr>
                        <td data-l="کاربر">{{ $v['name'] }} <span class="muted mono">{{ $v['code'] }}</span></td>
                        <td data-l="ارتباط" dir="auto">{{ $v['contact'] }}</td>
                        <td data-l="پلن">{{ $v['plan_title'] }}</td>
                        <td data-l="پایان">{{ $v['end'] }} ({{ $v['state'] === 'expired' ? 'گذشته' : $fa($v['days_left']).' روز' }})</td>
                        <td data-l="وضعیت"><span class="pill st-{{ $v['state'] }}">{{ $stateLabel[$v['state']] }}</span>@if ($v['contacted_at'])<br><small class="muted">پیام داده شد {{ $v['contacted_at'] }}</small>@endif</td>
                        <td data-l="اقدام"><div class="acts">
                            @if (! $v['contacted_at'])<form method="post" action="{{ route('admin.subs.action') }}">@csrf<input type="hidden" name="code" value="{{ $v['code'] }}"><input type="hidden" name="action" value="contacted"><button class="btn btn-sm btn-primary">پیام دادم ✓</button></form>@endif
                            <a class="btn btn-sm" href="{{ route('admin.dashboard', ['tab' => 'notices', 'to' => $v['code']]) }}">پیام در برنامه</a>
                        </div></td>
                    </tr>
                @endforeach
                </tbody></table>
            @endif
        </section>

        <div class="grid2">
            <section class="card"><h2>ترکیب پلن‌های فعال</h2>
                @forelse ($stats['plan_mix'] as $t => $n)<div class="switch" style="cursor:default"><span style="flex:1">{{ $t }}</span><b>{{ $fa($n) }}</b></div>@empty<p class="muted">هنوز اشتراکی ثبت نشده است.</p>@endforelse
            </section>
            <section class="card"><h2>پیشنهادهای رشد درآمد</h2>
                <ul class="muted" style="margin:0;padding-right:18px;line-height:2.1;font-size:13px">
                    <li>پلن یک‌ساله را «پیشنهاد ویژه» نشان دهید؛ ماهانه‌اش حدود ۴۱ هزار تومان است.</li>
                    <li>یک هفته پیش از موعد، به فهرست بالا پیام دهید؛ تمدید به‌موقع مهم‌ترین اهرم درآمد است.</li>
                    <li>برای حوزه‌ها، کتابخانه‌ها و مؤسسه‌ها اشتراک گروهی (مثلاً ۱۰ رایانه) تعریف کنید.</li>
                    <li>بسته‌های محتوایی تخصصی را جداگانه یا همراه پلن سالانه عرضه کنید.</li>
                    <li>به کاربران فعالِ بدون اشتراک (کارت زرد بالا) از بخش «پیام به برنامه» پیامی مهربان بفرستید.</li>
                </ul>
            </section>
        </div>
    @endif

    @if ($tab === 'subscriptions')
        <section class="card">
            <form class="row" method="get" action="{{ route('admin.dashboard') }}">
                <input type="hidden" name="tab" value="subscriptions">
                <label class="field"><span class="field-label">جست‌وجو (نام، تماس، کد)</span><input class="input" name="s" value="{{ request('s') }}"></label>
                <label class="field"><span class="field-label">وضعیت</span><select class="select input" name="state"><option value="">همه</option>@foreach ($stateLabel as $k => $l)<option value="{{ $k }}" @selected(request('state') === $k)>{{ $l }}</option>@endforeach</select></label>
                <button class="btn" type="submit">نمایش</button>
            </form>
        </section>
        <section class="card">
            <h2>اشتراک‌ها ({{ $fa(count($list)) }})</h2>
            <table class="tbl"><thead><tr><th>کد</th><th>کاربر</th><th>پلن و مبلغ</th><th>دوره</th><th>آخرین استفاده</th><th>اقدام</th></tr></thead><tbody>
            @forelse ($list as $v)
                @php $last = end($v['history']); @endphp
                <tr>
                    <td data-l="کد"><span class="mono">{{ $v['code'] }}</span><br><span class="pill st-{{ $v['state'] }}">{{ $stateLabel[$v['state']] }}</span></td>
                    <td data-l="کاربر">{{ $v['name'] }}<br><small class="muted" dir="auto">{{ $v['contact'] }}</small></td>
                    <td data-l="پلن">{{ $v['plan_title'] }} · {{ $fa($v['price']) }}<br><small class="muted">پیگیری: <span class="mono">{{ $last['tracking_code'] ?? '—' }}</span> · کارت: <span class="mono">{{ $last['card_last4'] ?? '—' }}</span></small></td>
                    <td data-l="دوره">{{ $v['period_start'] ?? $v['start'] }} تا {{ $v['end'] }}<br><small class="muted">روز {{ $fa($v['day_number']) }} از {{ $fa($v['days_total']) }} · {{ $fa($v['days_left']) }} روز مانده</small></td>
                    <td data-l="استفاده">{{ $v['last_seen'] ?: 'هنوز در برنامه وارد نشده' }}</td>
                    <td data-l="اقدام"><div class="acts">
                        <form method="post" action="{{ route('admin.subs.action') }}">@csrf<input type="hidden" name="code" value="{{ $v['code'] }}"><input type="hidden" name="action" value="extend"><input type="hidden" name="days" value="30"><button class="btn btn-sm">+۳۰ روز</button></form>
                        <form method="post" action="{{ route('admin.subs.action') }}">@csrf<input type="hidden" name="code" value="{{ $v['code'] }}"><input type="hidden" name="action" value="contacted"><button class="btn btn-sm">پیام دادم</button></form>
                        <a class="btn btn-sm" href="{{ route('admin.dashboard', ['tab' => 'notices', 'to' => $v['code']]) }}">پیام</a>
                        <form method="post" action="{{ route('admin.subs.action') }}">@csrf<input type="hidden" name="code" value="{{ $v['code'] }}"><input type="hidden" name="action" value="{{ $v['state'] === 'cancelled' ? 'reactivate' : 'cancel' }}"><button class="btn btn-sm">{{ $v['state'] === 'cancelled' ? 'فعال‌سازی' : 'لغو' }}</button></form>
                    </div></td>
                </tr>
            @empty
                <tr><td colspan="6" class="muted">موردی پیدا نشد.</td></tr>
            @endforelse
            </tbody></table>
        </section>
        <section class="card"><h2>ثبت دستی اشتراک (مثلاً برای مشتری تلفنی)</h2>
            <form class="row" method="post" action="{{ route('admin.subs.create') }}">@csrf
                <label class="field"><span class="field-label">نام</span><input class="input" name="name" required></label>
                <label class="field"><span class="field-label">تماس</span><input class="input" name="contact" required></label>
                <label class="field"><span class="field-label">پلن</span><select class="select input" name="plan">@foreach ($settings['plans'] as $p)<option value="{{ $p['id'] }}">{{ $p['title'] }}</option>@endforeach</select></label>
                <label class="field"><span class="field-label">شروع (میلادی)</span><input class="input" name="start" type="date"></label>
                <button class="btn btn-primary" type="submit">ثبت و صدور کد</button>
            </form>
        </section>
    @endif

    @if ($tab === 'inbox')
        <section class="card"><h2>پیام‌های پشتیبانی و پیشنهادها</h2>
            @forelse ($inbox as $m)
                <div class="msg {{ $m['status'] === 'new' ? 'new' : '' }}">
                    <div class="adm-head"><b>{{ $m['type'] === 'feature' ? '💡 پیشنهاد قابلیت' : '💬 پشتیبانی' }} · {{ $m['name'] }}</b><small class="muted">{{ $m['at'] }}</small></div>
                    <small class="muted" dir="auto">{{ $m['contact'] }} @if ($m['code']) · کد <span class="mono">{{ $m['code'] }}</span>@endif</small>
                    <p>{{ $m['message'] }}</p>
                    <div class="acts">
                        <form method="post" action="{{ route('admin.inbox.action') }}">@csrf<input type="hidden" name="id" value="{{ $m['id'] }}"><input type="hidden" name="action" value="{{ $m['status'] === 'new' ? 'done' : 'new' }}"><button class="btn btn-sm">{{ $m['status'] === 'new' ? 'رسیدگی شد ✓' : 'علامت نخوانده' }}</button></form>
                        @if ($m['code'])<a class="btn btn-sm" href="{{ route('admin.dashboard', ['tab' => 'notices', 'to' => $m['code']]) }}">پاسخ در برنامه</a>@endif
                        <form method="post" action="{{ route('admin.inbox.action') }}" onsubmit="return confirm('حذف شود؟')">@csrf<input type="hidden" name="id" value="{{ $m['id'] }}"><input type="hidden" name="action" value="delete"><button class="btn btn-sm">حذف</button></form>
                    </div>
                </div>
            @empty
                <p class="muted">پیامی نرسیده است.</p>
            @endforelse
        </section>
        @if (! empty($reports))
            <section class="card"><h2>اعلام‌های واریز پیشین</h2>
                <table class="tbl"><thead><tr><th>زمان</th><th>نام</th><th>تماس</th><th>مبلغ</th></tr></thead><tbody>
                @foreach ($reports as $r)<tr><td data-l="زمان">{{ $r['received_at'] ?? '' }}</td><td data-l="نام">{{ $r['name'] ?? '' }}</td><td data-l="تماس" dir="auto">{{ $r['contact'] ?? '' }}</td><td data-l="مبلغ">{{ $fa($r['amount_toman'] ?? 0) }}</td></tr>@endforeach
                </tbody></table>
            </section>
        @endif
    @endif

    @if ($tab === 'notices')
        <section class="card"><h2>پیام تازه برای برنامهٔ کاربران</h2>
            <form method="post" action="{{ route('admin.notices.create') }}">@csrf
                <div class="row">
                    <label class="field"><span class="field-label">گیرنده</span>
                        <select class="select input" name="target" id="target">
                            @foreach ($targets as $k => $l)<option value="{{ $k }}">{{ $l }}</option>@endforeach
                            @if (request('to'))<option value="{{ request('to') }}" selected>فقط کد {{ request('to') }}</option>@endif
                        </select></label>
                    <label class="field"><span class="field-label">حالت</span><select class="select input" name="level"><option value="info">اطلاع</option><option value="success">خبر خوب</option><option value="warning">یادآوری</option></select></label>
                    <label class="field"><span class="field-label">نمایش تا (اختیاری)</span><input class="input" type="date" name="until"></label>
                </div>
                <label class="field"><span class="field-label">عنوان</span><input class="input" name="title" maxlength="80" required></label>
                <label class="field"><span class="field-label">متن</span><textarea class="input" name="body" rows="3" maxlength="600" required></textarea></label>
                <button class="btn btn-primary" type="submit">ارسال به برنامه‌ها</button>
            </form>
        </section>
        <section class="card"><h2>پیام‌های ثبت‌شده</h2>
            @forelse ($notices as $n)
                <div class="msg {{ $n['active'] ? 'new' : '' }}">
                    <div class="adm-head"><b>{{ $n['title'] }}</b><small class="muted">{{ $targets[$n['target']] ?? 'کد '.$n['target'] }} · {{ $n['created_at'] }}</small></div>
                    <p>{{ $n['body'] }}</p>
                    <div class="acts">
                        <form method="post" action="{{ route('admin.notices.action') }}">@csrf<input type="hidden" name="id" value="{{ $n['id'] }}"><input type="hidden" name="action" value="toggle"><button class="btn btn-sm">{{ $n['active'] ? 'توقف نمایش' : 'نمایش دوباره' }}</button></form>
                        <form method="post" action="{{ route('admin.notices.action') }}">@csrf<input type="hidden" name="id" value="{{ $n['id'] }}"><input type="hidden" name="action" value="delete"><button class="btn btn-sm">حذف</button></form>
                    </div>
                </div>
            @empty
                <p class="muted">هنوز پیامی برای برنامه‌ها ثبت نشده است.</p>
            @endforelse
        </section>
    @endif

    @if ($tab === 'settings')
        <form method="post" action="{{ route('admin.settings.save') }}" class="adm">@csrf
            <section class="card"><h2>کارت‌های بانکی (کارت انتخاب‌شده، کارت مبناست)</h2>
                @foreach ([0, 1, 2] as $i)
                    @php $c = $settings['cards'][$i] ?? ['number' => '', 'holder' => '', 'enabled' => false]; @endphp
                    <div class="row" style="margin-bottom:10px">
                        <label class="switch" style="border:0;padding:0 0 10px"><input type="radio" name="primary_card" value="{{ $i }}" @checked($i === 0)> مبنا</label>
                        <label class="field"><span class="field-label">شماره کارت</span><input class="input" name="cards[{{ $i }}][number]" dir="ltr" value="{{ \App\Services\SiteSettings::cardGroups($c['number']) }}" placeholder="۱۶ رقم"></label>
                        <label class="field"><span class="field-label">به نام</span><input class="input" name="cards[{{ $i }}][holder]" value="{{ $c['holder'] }}"></label>
                        <label class="switch" style="border:0;padding:0 0 10px"><input type="checkbox" name="cards[{{ $i }}][enabled]" value="1" @checked($c['enabled'])> نمایش</label>
                    </div>
                @endforeach
            </section>
            <section class="card"><h2>پلن‌ها و قیمت‌ها (تومان)</h2>
                @foreach ([0, 1, 2, 3] as $i)
                    @php $p = $settings['plans'][$i] ?? ['id' => 'p'.$i, 'title' => '', 'days' => '', 'price' => '', 'enabled' => false]; @endphp
                    <div class="row" style="margin-bottom:10px">
                        <input type="hidden" name="plans[{{ $i }}][id]" value="{{ $p['id'] }}">
                        <label class="field"><span class="field-label">عنوان</span><input class="input" name="plans[{{ $i }}][title]" value="{{ $p['title'] }}"></label>
                        <label class="field"><span class="field-label">مدت (روز)</span><input class="input" name="plans[{{ $i }}][days]" inputmode="numeric" value="{{ $p['days'] }}"></label>
                        <label class="field"><span class="field-label">قیمت</span><input class="input" name="plans[{{ $i }}][price]" inputmode="numeric" value="{{ $p['price'] }}"></label>
                        <label class="switch" style="border:0;padding:0 0 10px"><input type="checkbox" name="plans[{{ $i }}][enabled]" value="1" @checked($p['enabled'])> فعال</label>
                    </div>
                @endforeach
            </section>
            <div class="grid2">
                <section class="card"><h2>فیچرفلگ‌ها</h2>
                    @foreach ($flagLabels as $k => $l)
                        <label class="switch"><input type="checkbox" name="flags[{{ $k }}]" value="1" @checked($settings['flags'][$k] ?? false)> {{ $l }}</label>
                    @endforeach
                    <div class="row" style="margin-top:12px">
                        <label class="field"><span class="field-label">روزهای مهمانی (آزمایشی)</span><input class="input" name="numbers[trial_days]" value="{{ $settings['numbers']['trial_days'] }}"></label>
                        <label class="field"><span class="field-label">یادآوری تمدید از چند روز قبل</span><input class="input" name="numbers[reminder_days]" value="{{ $settings['numbers']['reminder_days'] }}"></label>
                    </div>
                </section>
                <section class="card"><h2>متن‌ها</h2>
                    <label class="field"><span class="field-label">متن نوار اطلاعیهٔ صفحهٔ اصلی</span><textarea class="input" name="texts[announcement]" rows="2">{{ $settings['texts']['announcement'] }}</textarea></label>
                    <label class="field"><span class="field-label">جملهٔ اعتماد (صفحهٔ اشتراک)</span><textarea class="input" name="texts[trust_note]" rows="3">{{ $settings['texts']['trust_note'] }}</textarea></label>
                    <label class="field"><span class="field-label">زمان پاسخ‌گویی پشتیبانی</span><input class="input" name="texts[support_hours]" value="{{ $settings['texts']['support_hours'] }}"></label>
                </section>
            </div>
            <button class="btn btn-primary btn-block" type="submit">ذخیرهٔ همهٔ تنظیمات</button>
        </form>
    @endif

    @if ($tab === 'account')
        <section class="card" style="max-width:520px"><h2>نام کاربری و رمز مدیر</h2>
            <p class="muted" style="font-size:13px">ورود از نشانی <span class="mono">/adminarea/login</span>. نام کاربری فعلی: <span class="mono">{{ $admin }}</span></p>
            <form method="post" action="{{ route('admin.account.save') }}">@csrf
                <label class="field"><span class="field-label">نام کاربری</span><input class="input" name="username" dir="ltr" value="{{ old('username', $admin) }}" required></label>
                <label class="field"><span class="field-label">رمز جدید (دست‌کم ۱۰ نویسه)</span><input class="input" type="password" name="password" dir="ltr" autocomplete="new-password" required></label>
                <label class="field"><span class="field-label">تکرار رمز جدید</span><input class="input" type="password" name="password_confirmation" dir="ltr" autocomplete="new-password" required></label>
                <button class="btn btn-primary btn-block" type="submit">ذخیره</button>
            </form>
        </section>
    @endif
</div>
@endsection
