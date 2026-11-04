@if ($settings['flags']['contact_enabled'] || $settings['flags']['feature_requests_enabled'])
<section class="section" id="contact">
    <div class="container">
        <div class="section-head">
            <div><span class="eyebrow">پشتیبانی و پیشنهاد</span><h2>با ما در ارتباط باشید</h2></div>
            <p>{{ $settings['texts']['support_hours'] }} اگر قابلیتی به ذهن‌تان رسیده که کارتان را آسان‌تر می‌کند، خوشحال می‌شویم بشنویم.</p>
        </div>
        @if (session('contact_status'))<div class="alert alert-success" role="status">{{ session('contact_status') }}</div>@endif
        <form class="support-form panel panel-pad" method="post" action="{{ route('contact.store') }}">
            @csrf {!! \App\Support\SpamGuard::fields() !!}
            <div class="gallery-tabs" role="radiogroup" aria-label="نوع پیام" style="margin-bottom:14px">
                @if ($settings['flags']['contact_enabled'])<label class="type-pick"><input type="radio" name="type" value="support" @checked(old('type', 'support') === 'support')> 💬 پشتیبانی</label>@endif
                @if ($settings['flags']['feature_requests_enabled'])<label class="type-pick"><input type="radio" name="type" value="feature" @checked(old('type') === 'feature' || ! $settings['flags']['contact_enabled'])> 💡 پیشنهاد قابلیت تازه</label>@endif
            </div>
            <div class="form-grid contact-grid">
                <label class="field"><span class="field-label">نام</span><input class="input" name="name" value="{{ old('name') }}" maxlength="80" required>@error('name')<span class="field-error">{{ $message }}</span>@enderror</label>
                <label class="field"><span class="field-label">موبایل یا ایمیل</span><input class="input" name="contact" dir="auto" value="{{ old('contact') }}" maxlength="120" required>@error('contact')<span class="field-error">{{ $message }}</span>@enderror</label>
                <label class="field"><span class="field-label">کد اشتراک <small>(اختیاری؛ تا پاسخ را در برنامه هم ببینید)</small></span><input class="input" name="code" dir="ltr" value="{{ old('code') }}" maxlength="20" placeholder="MT-XXXX-XXXX"></label>
                <label class="field form-wide"><span class="field-label">پیام</span><textarea class="input" name="message" rows="4" maxlength="2000" required>{{ old('message') }}</textarea>@error('message')<span class="field-error">{{ $message }}</span>@enderror</label>
            </div>
            <button class="btn btn-primary btn-block" type="submit">ارسال پیام</button>
        </form>
    </div>
</section>
@endif
