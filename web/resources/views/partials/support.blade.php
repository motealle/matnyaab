@php
    $cardNumber = preg_replace('/\D/', '', (string) config('services.support.card_number'));
    $cardHolder = (string) config('services.support.card_holder');
    $cardGroups = implode(' ', str_split($cardNumber, 4));
@endphp
<section class="section" id="support">
    <div class="container">
        <div class="section-head">
            <div>
                <span class="eyebrow">رایگان برای همه، با حمایت شما</span>
                <h2>متن‌یاب رایگان است؛ اگر به کارتان آمد، حمایت کنید</h2>
            </div>
            <p>همهٔ امکانات متن‌یاب بدون اشتراک، سریال یا سقف حجمی در دسترس است. حمایت مالی کاملاً داوطلبانه و بر پایهٔ اعتماد است و به ادامهٔ توسعه، پشتیبانی و افزودن امکانات تازه کمک می‌کند.</p>
        </div>

        @if (session('support_status'))
            <div class="alert alert-success" role="status">{{ session('support_status') }}</div>
        @endif
        @if ($errors->has('support'))
            <div class="alert alert-error" role="alert">{{ $errors->first('support') }}</div>
        @endif

        <div class="support-grid">
            <div class="support-side">
                <ol class="support-steps">
                    <li><strong>مبلغ دلخواه</strong> را به کارت زیر واریز کنید.</li>
                    <li>در صورت تمایل، <strong>واریز را اعلام کنید</strong> تا با سپاس ثبت شود.</li>
                    <li>همین! برنامه بدون هیچ محدودیتی در اختیار شماست.</li>
                </ol>

                <div class="bank-card" aria-label="اطلاعات کارت برای حمایت">
                    <span class="bank-card-label">شماره کارت</span>
                    <span class="bank-card-number" dir="ltr" data-card-number="{{ $cardNumber }}">{{ $cardGroups }}</span>
                    <span class="bank-card-holder">به نام {{ $cardHolder }}</span>
                    <button class="btn bank-card-copy" type="button" data-copy-card>کپی شماره کارت</button>
                </div>

                <div class="account-note" id="account">
                    <strong>پیش‌تر اشتراک خریده‌اید؟</strong>
                    <span>سوابق خرید شما محفوظ است و از <a href="{{ route('login') }}">حساب کاربری</a> قابل مشاهده است. نسخهٔ جدید به سریال نیاز ندارد.</span>
                </div>
            </div>

            <form class="support-form panel panel-pad" method="post" action="{{ route('support.store') }}" novalidate>
                @csrf
                <h3>اعلام واریز</h3>
                <p class="muted">اختیاری است؛ فقط برای قدردانی و پیگیری استفاده می‌شود و نمایش عمومی ندارد.</p>
                <div class="form-grid">
                    <label class="field">
                        <span class="field-label">نام</span>
                        <input class="input" name="name" value="{{ old('name', auth()->user()?->first_name) }}" maxlength="80" autocomplete="name" required>
                        @error('name')<span class="field-error">{{ $message }}</span>@enderror
                    </label>
                    <label class="field">
                        <span class="field-label">موبایل یا ایمیل</span>
                        <input class="input" name="contact" dir="auto" value="{{ old('contact', auth()->user()?->username) }}" maxlength="120" autocomplete="email" required>
                        @error('contact')<span class="field-error">{{ $message }}</span>@enderror
                    </label>
                    <label class="field">
                        <span class="field-label">مبلغ (تومان)</span>
                        <input class="input" name="amount" inputmode="numeric" dir="ltr" value="{{ old('amount') }}" maxlength="15" placeholder="مثلاً 200000" required>
                        @error('amount')<span class="field-error">{{ $message }}</span>@enderror
                    </label>
                    <label class="field">
                        <span class="field-label">۴ رقم آخر کارت شما <small>(اختیاری)</small></span>
                        <input class="input" name="card_last4" inputmode="numeric" dir="ltr" value="{{ old('card_last4') }}" maxlength="4">
                        @error('card_last4')<span class="field-error">{{ $message }}</span>@enderror
                    </label>
                    <label class="field">
                        <span class="field-label">تاریخ و ساعت واریز <small>(اختیاری)</small></span>
                        <input class="input" name="paid_at" value="{{ old('paid_at') }}" maxlength="40" placeholder="مثلاً ۱۴۰۵/۰۸/۱۲ ساعت ۱۰">
                    </label>
                    <label class="field form-wide">
                        <span class="field-label">پیام <small>(اختیاری)</small></span>
                        <textarea class="input" name="message" rows="3" maxlength="600" placeholder="پیشنهاد، نظر یا قابلیتی که دوست دارید اضافه شود">{{ old('message') }}</textarea>
                    </label>
                    <label class="hp-field" aria-hidden="true">وب‌سایت<input name="website" tabindex="-1" autocomplete="off"></label>
                </div>
                <button class="btn btn-primary btn-block" type="submit">ثبت اعلام واریز</button>
            </form>
        </div>
    </div>
</section>
