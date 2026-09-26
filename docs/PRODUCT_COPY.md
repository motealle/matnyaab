# Matnyaab Product Copy Standard

Last updated: 2026-09-26

This document defines the production-facing voice for matnyaab.ir. It applies to homepage copy, authentication, profile, subscription, payment, support, errors, empty states, and client-facing notices.

## Voice

Matnyaab should sound:

- clear, calm and intelligent;
- literary enough to feel considered, but never ornate or vague;
- product-focused rather than presentation-focused;
- respectful and reassuring without sounding defensive;
- inviting without exaggerated claims;
- concise where the user is performing a task;
- confident about what the product does, without explaining internal implementation unless the user needs to know it.

## Do not write for the internal team

Production copy must not explain or justify the design to the customer.

Avoid phrases such as:

- "تصویر واقعی برنامه"
- "نسخه جدید Kotlin در حال آماده‌سازی است"
- "در جریان مهاجرت..."
- "زیرساخت جدید..."
- "حساب‌های قبلی حفظ شده‌اند" when the customer only needs a normal login prompt
- labels such as "FEATURES", "SUPERUSER", "WINDOWS APP", "DASHBOARD" when a natural Persian heading is clearer

These may be useful in engineering documentation, not in customer-facing UI.

## Prefer direct product language

Bad:

> تصویر واقعی برنامه

Better:

> نمای محیط متن‌یاب

Bad:

> داده‌های کاربران و خریدهای قبلی در مهاجرت حفظ شده‌اند.

Better:

> با همان حساب همیشگی وارد شوید و اشتراک و سوابق خریدتان را ببینید.

Bad:

> نسخه پایدار فعلی در دسترس است و کلاینت جدید Kotlin در حال آماده‌سازی است.

Better:

> نسخه ویندوز متن‌یاب را دریافت کنید و جستجو را روی فایل‌های خودتان آغاز کنید.

## Headlines

- Prefer one clear idea.
- Avoid oversized generic startup slogans.
- Connect the promise directly to the problem Matnyaab solves: remembering fragments of text, large archives, and finding content inside files.
- Use thin Vazirmatn weights where the design permits.
- Motion may support the meaning, but copy must still make sense without animation.

## Trust and reassurance

Trust should come from useful facts, not self-certification.

Prefer:

- "جستجو روی فایل‌های رایانه شما انجام می‌شود."
- "با همان حساب خود وارد شوید."
- "لینک بازیابی تا ۳۰ دقیقه معتبر است."

Avoid:

- "این تصویر واقعاً واقعی است."
- "اطلاعات شما کاملاً امن است" unless technically defined and supportable.
- "بهترین / سریع‌ترین / بی‌نظیر" without evidence.

## Service-unavailable copy

Explain the immediate consequence and next action, not engineering details.

Prefer:

> ثبت‌نام آنلاین موقتاً در دسترس نیست. اگر قبلاً حساب دارید، می‌توانید وارد شوید.

Avoid:

> سرویس پیامک روی سرور جدید هنوز پیکربندی نشده است.

## Errors

- Say what happened in plain language.
- Preserve user dignity.
- Give a useful next step.
- Do not expose stack, provider, migration or infrastructure terminology.
- Do not imply data loss unless it actually occurred.

## Persian writing conventions

- Use نیم‌فاصله where natural.
- Prefer "رمز عبور" consistently in UI.
- Prefer "رایانه" or "ویندوز" based on context; avoid needless English technical terms.
- Use Persian prose for visible section labels unless a brand/product term requires English.
- Keep numerals readable in forms and technical identifiers; do not transform IDs/serials.

## Review gate

Before production deploy of user-visible copy:

1. read the page as a customer, not an engineer;
2. remove migration/framework/provider language;
3. remove claims whose only purpose is to reassure the internal reviewer;
4. check that every heading communicates user value or task;
5. check unavailable states include a useful next action;
6. run live smoke/asset tests after deployment.
