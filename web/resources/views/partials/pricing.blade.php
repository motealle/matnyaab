@php $fa = fn ($n) => strtr(number_format((float) $n), ['0'=>'۰','1'=>'۱','2'=>'۲','3'=>'۳','4'=>'۴','5'=>'۵','6'=>'۶','7'=>'۷','8'=>'۸','9'=>'۹',','=>'٬']); @endphp
<section class="section" id="pricing">
    <span id="support"></span><span id="account"></span>
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">اشتراک اعتمادمحور</span><h2>پلن خود را انتخاب کنید</h2></div>
            <p>{{ $settings['texts']['trust_note'] }}</p>
        </div>
        <div class="price-grid">
            @foreach ($plans as $i => $p)
                @php $best = $i === count($plans) - 1 && count($plans) > 1; @endphp
                <article class="price-card {{ $best ? 'is-best' : '' }}">
                    @if ($best)<span class="new-badge">به‌صرفه‌ترین</span>@endif
                    <h3>{{ $p['title'] }}</h3>
                    <div class="price"><b>{{ $fa($p['price']) }}</b> <span>تومان</span></div>
                    <p>{{ $fa($p['days']) }} روز استفاده از همهٔ امکانات@if ($p['days'] > 31)<br>ماهی حدود {{ $fa(round($p['price'] / ($p['days'] / 30) / 1000) * 1000) }} تومان@endif</p>
                    <a class="btn {{ $best ? 'btn-primary' : '' }} btn-block" href="{{ route('subscribe', ['plan' => $p['id']]) }}">ثبت اشتراک {{ $p['title'] }}</a>
                </article>
            @endforeach
        </div>

        <div class="support-grid" style="margin-top:22px">
            <div>
                <ol class="support-steps">
                    <li>مبلغ پلن را به یکی از کارت‌های زیر <strong>کارت‌به‌کارت</strong> کنید.</li>
                    <li>در <a href="{{ route('subscribe') }}">صفحهٔ ثبت اشتراک</a> فرم کوتاه را بفرستید و <strong>کد اشتراک</strong> را همان لحظه بگیرید.</li>
                    <li>کد را در برنامه وارد کنید؛ روزشمار برنامه و سایت هماهنگ می‌شوند و موعد تمدید را مهربانانه یادآوری می‌کنند.</li>
                </ol>
                <div class="account-note">
                    <strong>پیش‌تر اشتراک خریده‌اید؟</strong>
                    <span>سوابق خرید شما محفوظ است و از <a href="{{ route('login') }}">حساب کاربری</a> قابل مشاهده است.</span>
                </div>
            </div>
            <div style="display:grid;gap:12px">
                @foreach ($cards as $i => $c)
                    <div class="bank-card" @if ($i) style="background:linear-gradient(135deg,#334155,#475569)" @endif>
                        <span class="bank-card-label">{{ $i ? 'کارت جایگزین' : 'شماره کارت' }}</span>
                        <span class="bank-card-number" dir="ltr">{{ \App\Services\SiteSettings::cardGroups($c['number']) }}</span>
                        <span class="bank-card-holder">به نام {{ $c['holder'] }}</span>
                        <button class="btn bank-card-copy" type="button" data-copy-card="{{ preg_replace('/\D/', '', $c['number']) }}">کپی شماره کارت</button>
                    </div>
                @endforeach
            </div>
        </div>
    </div>
</section>
