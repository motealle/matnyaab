# Matnyaab — Engineering Handoff

Last updated: 2026-09-27

This file is the operational handoff for matnyaab.ir. Keep it current whenever production architecture, deployment behavior, or migration gates change.

## Current production state

- DNS points to the destination shared host.
- HTTPS/TLS is valid.
- Production PHP is 8.2.
- Production data remains on the original Django SQLite database.
- SQLite integrity checks are passing.
- Laravel 12 is deployed outside the public document root and reads the existing Django schema directly.
- Homepage and account/web routes are progressively served by Laravel.
- Desktop/API compatibility routes still use the PHP recovery bridge until parity is complete.
- The old saved-HTML homepage and its broken local assets are no longer used.
- Homepage, login, registration, SMS confirmation, profile, password-change, and error pages share the Vazirmatn design system.
- Dark/light switching is live, persists in localStorage, and respects OS preference on first visit.
- The homepage hero uses a reduced-motion-aware typewriter/fade treatment tied to search behavior.
- The authentic original product screenshot is restored from `/static/sc1.png`.
- Live homepage audit currently reports zero broken local URLs and verifies the screenshot/theme marker.
- The existing Windows installer URL is still available.

## Production data snapshot

Latest verified snapshot:

- accounts: 153
- distinct purchasers: 94
- subscription history rows: 116
- completed orders: 137
- gateway transaction rows: 66

Production customer data must never be committed to Git.

## Database

Current DB path:

    matnyaab_with_license/db.sqlite3

Laravel maps directly to the legacy tables. Do not run destructive Laravel migrations against production SQLite during this phase.

Important tables include users_accountmodel, users_subscriptionmodel, users_subscriptionhistorymodel, users_ordermodel, orderdiscount_orderdiscountmodel, azbankgateways_bank, matnyaab_contentsmodel, matnyaab_statistics, and news_news.

## Compatibility already implemented

- Django PBKDF2 login compatibility
- profile/subscription display
- password change
- password-recovery controller/UI with expiring single-use HMAC reset links
- superuser migration dashboard with user search and gift-subscription support
- subscription/order/history mapping
- legacy AES/Base32 serial compatibility
- IDPay create/verify logic covered by fake HTTP tests
- registration and four-digit SMS confirmation logic
- desktop content/news/statistics/update bridge
- content download compatibility

## SMS

Legacy SMS settings are confirmed to exist in:

    matnyaab_with_license/Django_Project/private.py

Expected keys are SMS_PANEL_USERNAME, SMS_PANEL_PASSWORD, and SMS_PANEL_NUMBER.

The old provider endpoint is http://tsms.ir/url/tsmshttp.php.

Values must not be committed or printed in CI logs. The legacy values have now been synchronized into `matnyaab-laravel-stage/shared/runtime-services.env` outside the public document root. Production sending is still gated by `SMS_PRODUCTION_ENABLED=false` until one controlled real SMS is verified.

Legacy mail configuration keys were also verified without exposing values: EMAIL_BACKEND, EMAIL_HOST, EMAIL_HOST_USER, EMAIL_HOST_PASSWORD, EMAIL_PORT, EMAIL_USE_TLS. Password recovery is code-complete but production email delivery remains gated on runtime mail configuration.

## Payment

Laravel has an IDPay adapter mapped onto azbankgateways_bank.

Before enabling real paid purchases:

1. configure the real merchant credential outside Git;
2. perform one controlled transaction;
3. verify callback idempotency and amount checks;
4. verify subscription/history/serial updates;
5. keep a fresh DB snapshot before the test.

## CI/CD

Important workflows:

- CI
- Build
- Deploy Laravel Stage
- Deploy Recovery Bridge
- Homepage Asset Audit
- Laravel Legacy SQLite Smoke
- Private Data Backup
- Legacy SMS Config Audit

## Backup / recovery

Daily encrypted backups include the full SQLite DB, customer summary CSV, purchase history CSV, and Excel workbook. Current retention is seven days.

Before any risky production write:

1. verify a fresh backup;
2. verify SQLite integrity;
3. note the workflow run ID;
4. make the change;
5. smoke-test the affected route and unchanged desktop API routes.

## Public routing model

- homepage/account UX → Laravel
- login/profile/password change → Laravel
- registration/SMS → Laravel code exists; production activation remains gated by SMS runtime config
- desktop API compatibility → recovery bridge until parity is finished
- updater/installer → preserved compatibility paths

Do not remove the bridge until the Windows client and Laravel API contracts are fully tested.

## Windows client target

- Kotlin/JVM
- JDK 17
- Gradle 8.7
- JavaFX
- Lucene
- Tika

Before release: preserve source-only Git history, keep generated/IDE output and credentials out of the repository, package an installer, and verify updater plus license/content compatibility.

Verified Kotlin checkpoint:
- PR #6 restored the verified source under `client/windows/` onto current main without merging stale migration history.
- Windows Actions clean-builds with JDK 17 and Gradle 8.7, including the downloader regression tests.
- PR #6 validation produced a real Windows artifact of about 503 MB; missing artifacts now fail the job.
- Failed/cancelled downloads stage into a temporary file and no longer delete or replace an existing good target.
- Do not change the WMI/system-ID algorithm until old-client parity is measured on the same Windows machine or against a trusted vector.

Latest verified backend state:
- Laravel compatibility suite is running on main against an isolated copy of production SQLite.
- Password recovery is implemented with 30-minute stateless HMAC links and tested.
- Superuser migration dashboard and gift-subscription flow are implemented and tested.

## Immediate next work

See BACKLOG.md. Current order:

1. prove old-client/Kotlin system-ID and license parity on Windows;
2. run representative Lucene/Tika indexing/search and content-pack end-to-end tests;
3. package and smoke-test the Windows installer/updater;
4. continue desktop API golden fixtures and one-route-at-a-time Laravel cutover;
5. separately complete controlled SMS, SMTP and real-payment tests when safe test inputs/runtime values are available;
6. remove the recovery bridge only after client/server parity.

## Current blockers

### Runtime secrets
SMS, SMTP and real IDPay values exist only in legacy/private configuration or need to be supplied as runtime secrets. Do not copy them into Git. Until runtime SMS is configured, new registration remains intentionally gated. Until SMTP is configured, password-recovery email remains intentionally gated. Until the merchant value is configured, real paid checkout remains intentionally gated.

### Kotlin compatibility proof
Source location and compilation are no longer blockers. The remaining client release gate is behavioral parity: especially legacy system-ID/license generation, representative indexing/search, content-pack behavior, and installer/updater smoke testing. The system-ID check requires a trusted old-client result from the same Windows machine (or an equivalent known-good vector) before its algorithm is changed.

## Rollback principle

There is no retired source host to fail back to. Rollback means reverting code/routing on the destination host while preserving SQLite and persistent content. Never overwrite production SQLite as part of a code rollback.


## UI and product-copy contract

- `docs/PRODUCT_COPY.md` is the source of truth for production-facing language.
- Customer-facing text must describe the product, task or next action—not the migration, implementation stack, design rationale or internal validation state.
- Remove self-referential phrases such as "real screenshot" and internal labels such as framework/client technology names from public pages.
- Use polished, clear Persian that is confident, respectful, inviting and understandable.
- Vazirmatn is the preferred web typeface; do not commit font binaries.
- Both dark and light themes must be supported.
- Theme preference must persist locally in the browser and respect OS preference on first visit.
- Motion must respect `prefers-reduced-motion`.
- Homepage visual assets must be verified by the live asset audit.
- Keep the original product screenshot/image as authentic visual proof; decorative mock UI should not replace all real product imagery.


## Latest UI checkpoint

- Deploy smoke was corrected to use stable product markers instead of mutable marketing copy.
- The earlier red deploy after the hero rewrite was a stale smoke assertion, not a Laravel/runtime failure.
- Corrected deploy workflow run: 36259974807 — success.
- Homepage Asset Audit run: 36259983113 — success.


## Latest SMS checkpoint

- Sync Legacy SMS Runtime run 36260260729 — success.
- Credential values were read from legacy private.py inside Actions, masked, transferred through a one-time HTTPS synchronizer, stored outside document root, and the public synchronizer was deleted.
- The next deploy preserved the private runtime-service file.
- Latest CI / Build / Laravel Legacy SQLite Smoke / Deploy Laravel Stage for the SMS-gate test fix are all green.


## Production-copy checkpoint

- Full customer-facing copy audit completed across homepage, authentication, recovery, profile, payment, error and admin-facing views.
- Internal migration/framework/build language was removed from production UI.
- Product copy standard: `docs/PRODUCT_COPY.md`.
- Production Copy Guard workflow is active and passing.
- Production deploy for the copy revision: run 36263397737 — success.
- Homepage Asset Audit for the copy revision: run 36263397781 — success.


## Download compatibility correction — 2026-09-27

- Baseline main `8224350`: CI and Build succeeded; legacy SQLite smoke run 36263798906 failed because downloadContent declared only BinaryFileResponse but returned a plain-text Response for missing files. Deploy run 36263798871 nevertheless succeeded; deployment and compatibility workflows are currently independent.
- Both public download methods now accept BinaryFileResponse|Response, preserving the legacy 404 text instead of throwing TypeError/500.
- Added isolated synthetic download tests for both endpoints: invalid/missing IDs, missing files/root, traversal, successful files, partial and unsatisfiable ranges. No customer data or FTP secrets are required for this suite.
- Added a deployment dependency on the complete legacy SQLite smoke suite through workflow_call; a failed compatibility job now prevents deployment. The separate automatic smoke trigger is replaced by this dependency to avoid duplicate downloads/runs; manual smoke remains available.
- VERIFIED 2026-09-27: owner-authorized public PR #4 merged as a7c7f614db202d8b2ef44c789ff9add46e77394b. Download API Contract, CI, Build and Production Copy Guard all succeeded. Deploy Laravel Stage run 36289394537 passed its prerequisite compatibility suite (26 tests, 196 assertions), then deployed successfully and passed live web/API smoke checks. Desktop API routing remains on the recovery bridge; no endpoint cutover occurred.
- Next: finish golden API fixtures, then cut over endpoints individually. SMS needs an explicitly selected test recipient; SMTP and real payments retain their runtime gates.


## News contract preparation — 2026-09-27

- Found a pre-cutover mismatch: recovery compares news expiry in Asia/Tehran, while Laravel used the application UTC clock. Scoped correction to news only; no global timezone or customer-data changes.
- News now preserves explicit legacy field types and no-store caching. Synthetic golden expectations cover expiry before/at/after the Tehran boundary, Persian text, ordering and an empty list.
- Verified: PR #5 merged; Desktop API Contracts, Build, CI and Production Copy Guard passed. Deploy run 36290096487 passed full compatibility, deployed successfully and passed live web/API smoke. Public /news remains on recovery; a separate routing cutover and smoke test is next.


## Dual-client release checkpoint — 2026-09-27

- PR #7 is merged and production Laravel deploy passed its full copied-production-SQLite compatibility suite.
- Classic Windows client is still public at `/update/MATNYAAB_x64_setup.exe` (11,590,002 bytes during release audit).
- Kotlin 1.2 is public at `/downloadFiles/MATNYAAB-Kotlin-1.2-win-x64-portable.zip`. It is a portable ZIP with bundled Java runtime; it passed an executable package smoke that exercises Tika text extraction and Lucene tokenization.
- The earlier ~503 MB figure was a GitHub build artifact containing multiple Gradle outputs. The user-facing portable package is much smaller (about 160 MiB in the validated package run).
- Neither client download is intended to bundle the site's research/content packs. Those packs remain separate and are downloaded from inside the application.
- `/downloadFiles/releases.json` records exact current download size, SHA-256, delivery type and content description for both clients; the homepage consumes this manifest.
- Superusers can enter through `/login` with an existing legacy superuser account and then open `/adminarea`. No admin password is stored in Git or documentation.
- Admin now supports global bypass, per-user bypass, and a separate Kotlin System-ID while preserving the user's legacy System-ID/serial.
- Next: live homepage re-audit after upload completion, Windows same-machine System-ID comparison, representative Persian Lucene/Tika tests, content-pack end-to-end tests, and optional Kotlin installer packaging.


## HERO-01 implementation — 2026-09-27

- User selected latest blue candidates 7 and 8 plus the attached blue cleric image. Implemented three slides with per-image left/right copy placement, thin Vazirmatn headlines, typewriter text, wipe/fade transition and 1.8% subject-centered zoom.
- Added coherent navy header, blue light/dark palette, mobile navigation, manual controls, swipe, pause and reduced-motion handling. Preserved original product screenshot and both client download cards.
- Versioned WebP assets and CSS/JS are published by the existing release deployer before switching the release pointer. Live audit now follows successful deployment and includes srcset assets.
- User explicitly requested stopping tests to conserve tokens. No completed browser/visual validation: local PHP/browser setup was unavailable. JavaScript syntax and diff whitespace checks had passed before that instruction. Do not describe the UI as fully tested.
- TODO — Final visual/mobile verification only when requested; automatic existing deployment gates remain in place.


## Mobile hero correction — 2026-09-28

- User reported that the mobile 2:1 thumbnail left unused copy space and moved captions outside the photo. Replaced the stacked mobile layout with one tall, full-cover photograph, per-slide subject positioning, and copy over a lower dark gradient. Overlapping grid slides grow with their content; controls retain reserved space below the copy.
- Zoom rate increased threefold: scale 1 → 1.054 over 20s (previously 1 → 1.018). Reduced-motion and pause behavior retained. CSS URL cache-busted.
- No tests run, as explicitly requested. Submitted through existing deployment; live outcome not verified.

## Matnyaab 2.0 free release — 2026-11-03

Product decision (owner): the Windows client is now **completely free with no limits**. Payment is voluntary,
trust-based card-to-card, announced on the homepage. Online purchase is no longer offered in the UI.

- Client `client/windows` is version **2.0**, packaged as `Matnyaab.exe` (`Matnyaab-2.0-win-x64.zip`) with a proper icon.
  - 25 MB trial cap and every subscription gate were removed (search, indexing, incremental update).
  - Subscription window replaced by `SupportDialog` (card number + link to `/#support`).
  - Five switchable themes (`Theme.PALETTES`, persisted as `THEME` in settings.ini), applied live via CSS looked-up colours.
  - Redesigned add-folder dialog, scope tree counts/tooltips/empty state, larger caption buttons, Persian help page.
  - The settings directory is still `%APPDATA%\MATNYAAB` so existing indexes keep working.
  - `--screenshots <dir>` renders product screenshots from a temporary sample library (used by CI only).
- Web
  - Homepage: "What's new in 2", screenshot gallery (`/assets/matnyaab-v2/`), one full download + classic link, support, FAQ.
  - Support reports: `POST /support` → JSONL at `SUPPORT_REPORTS_PATH` (deployer points it to `shared/support-reports.jsonl`);
    no SQLite schema change. Listed in `/adminarea`.
  - Card details come from `services.support.*` (env `SUPPORT_CARD_NUMBER`, `SUPPORT_CARD_HOLDER`), public by design.
  - SEO: canonical/OG/Twitter meta, JSON-LD (SoftwareApplication, WebSite, FAQPage), `/robots.txt`, `/sitemap.xml`.
  - Profile no longer shows a purchase form. `buysubscription`/IDPay code is kept dormant for compatibility tests.
- Deployer publishes `public/assets/matnyaab-v2` in addition to the hero assets.

## Trust-based subscriptions, admin panel, installer — 2026-11-04

Owner decision: the app is **not free**. Payment is trust-based card-to-card; prices: monthly 89,000, 6 months 299,000,
yearly 490,000 toman (editable in admin). Primary card 6037-9977-7025-9241 (محمد کاظم رحیمی), secondary 5041-7212-0222-7469.

- Data (no SQLite change): JSON files in `MATNYAAB_DATA_DIR` (deployer → `shared/data`): `settings.json` (feature flags,
  cards, plans, texts), `subscriptions.json`, `inbox.json`, `notices.json`, `installs.json`, `admin.json`.
- `/subscribe` issues a code `MT-XXXX-XXXX` immediately (not verified). Renewal reuses the code and continues from the end date.
- Client API (CSRF-exempt, throttled): `GET /api/client/config`, `POST /api/client/activate`, `POST /api/client/ping`.
- Admin: `/adminarea/login` (independent username/password, bootstrap password must be changed on first login) or any
  existing superuser account via `/login`. Tabs: overview (finance KPIs + renewal follow-up), subscriptions, inbox,
  notices to clients, settings/feature flags, account. Legacy user tools: `/adminarea?tab=users`.
- Spam protection: honeypot + signed time token (3 s – 6 h) + link limit + throttle + 24 h duplicate check.
- Client 2.1: subscription chip with day counter (green / red in last `reminder_days`), code activation, local
  self-declaration, repeatable 3-day "guest" grace, admin messages, recent searches, CSV export of results.
- Packaging: Tika limited to document parsers, jlink runtime, Inno Setup installer `Matnyaab-Setup-2.1.exe` + portable ZIP.

## نسخهٔ ۲٫۳ و افزونهٔ ورد (آبان ۱۴۰۵)
- موتور جست‌وجوی نسل دوم: `client/windows/.../core/search/` — `MatchEngine` (نامزدها با RegexpQuery لوسین، داوری و شمارش دقیق روی متن ذخیره‌شده)، `BoolQuery` (AND/OR/NOT/پرانتز/NEAR‌/n)، `Morph` (هم‌خانواده/ریشه)، `Thesaurus` (مترادف‌ها: `resources/search/synonyms.txt` + `%APPDATA%\MATNYAAB\synonyms.txt`). یکسان‌سازی املا: ی/ک، همزه، آ، ۀ، اعراب، اعداد. فاصله: دقیق / فاصلهٔ اضافه / فاصله≡نیم‌فاصله. پرچم `FF_SEARCH_V2=0` پنل قدیمی را برمی‌گرداند.
- پل محلی `core/bridge/LocalBridge.kt`: HTTP روی 127.0.0.1 با توکن در `bridge.json`؛ در برنامهٔ باز یا حالت پنهان `Matnyaab.exe --bridge` (۳۰ دقیقه بیکاری ← خروج). مسیرها: ping, search, excerpt, note, add-folder, show, shutdown. پرچم `FF_WORD_BRIDGE=0` خاموشش می‌کند. آزمون سرتاسری در CI ویندوز.
- افزونهٔ ورد: `client/word/` (C# net472، COM Add-in بدون PIA با dynamic). نصب‌کنندهٔ Inno برای کاربر جاری + ثبت سیستمی فقط اگر خودبه‌خود مدیر است (mscoreei در فرایندهای مدیر HKCU را نمی‌خواند — با Procmon اثبات شد). پوشه‌های نسخه‌دار؛ آزمون CI: فعال‌سازی COM بومی ۳۲/۶۴ بیتی، نصب دوباره، حذف. تصاویر پنل در CI ساخته می‌شوند (`Tests.exe --shots`).
- سایت: بخش `partials/word-addin.blade.php` (پرچم `word_addin`)، کاتالوگ سرویس‌های هوش مصنوعی در پنل مدیریت (`ai_catalog` ← `/api/client/config`). releases.json کلید `word` را نگه می‌دارد.

## 3.0 «متن‌یاب هوشمند» (۱۴۰۵/۰۸)
- برنامه: نوار ابزار بالا (MainWindow.buildToolbar)، «میز پژوهش» ui/ResearchWindow.kt با زبانه‌های فیش‌ها/دستیار/ساخت کتاب/منبع‌یاب؛ AiSettingsDialog؛ ScenariosDialog («چه کارهایی می‌توانم بکنم؟»). فیش سریع: Ctrl+D یا «＋ فیش» در پیش‌نمایش.
- هسته: core/research (Research.kt=research.json، SourceFinder، DocxWriter/MarkdownWriter/ReportWriter)، core/ai (Ai.kt: AiConfig در ai.json + DPAPI، AiClient؛ Missions.kt: Scenario و MissionRunner با نقل‌قول‌های راستی‌آزمایی‌شده).
- پرچم‌ها (settings.ini، پیش‌فرض روشن، در تنظیمات): FF_RESEARCH، FF_SOURCE، FF_AI؛ FF_SEMANTIC همان قبلی.
- پل: fish/list، fish/add، find-source، check.
- افزونهٔ ورد 1.1.0: زبانه‌های «پژوهش» (UI/SmartViews.cs ResearchView) و «اداری» (OfficeView؛ WordOps/OfficeOps.cs)، گروه‌های ریبون پژوهش/اداری، ShowResearch/ShowOffice. سرویس‌های AI در ai.json مشترک‌اند (ai.json مرجع است).
- سایت: partials/smart.blade.php (#ai، پرچم smart)، فهرست سرویس‌ها با پیوند و سلب مسئولیت، FAQ.

## 3.1 (۱۴۰۵/۰۸) — پیشنهادهای مشتری + کتاب با هوش مصنوعی
- رفع: دکمهٔ «جست‌وجو» در نوار ابزار فقط فوکوس می‌کرد → `SearchPanel.searchOrFocus()`.
- دامنهٔ معنایی: `SearchSpec.semanticLevel` (۰..۱۰۰) ← `Ranking.semanticThreshold` (۰٫۹۰→۰٫۷۶) و `Ranking.semanticScore` (ستون «معنایی»). لغزنده در SearchPanel (SEARCH_OPTS اندیس ۹).
- خروجی با بافت: `core/research/ResultExporter` (بند/چند بند/واژه/نویسه، ادغام بافت‌های هم‌پوشان، صفحه با `\f`، شمارهٔ بند)، `XlsxWriter` (xlsx بی‌وابستگی، RTL، متن غنی)، `ui/ExportDialog` (EXPORT_OPTS). `DocxWriter.marked` و حذف نویسه‌های نامعتبر XML (`BAD_XML`).
- صفحهٔ PDF: `TikaExtractor.pdfWithPages` پایان هر صفحه را `\f` می‌گذارد (فقط اسناد تازه‌فهرست‌شده). پل محلی `\f` را به `\n` تبدیل می‌کند (`noFormFeed`) تا در ورد شکست صفحه نشود.
- کنار ساعت: `ui/TrayProgress` (TrayIcon با حلقهٔ پیشرفت، کارت کوچک، بازگشت خودکار) + `ParkableModal` (حلقهٔ رویداد تودرتو که با پنهان‌شدن پایان نمی‌یابد). در ScanDirectoryDialog، IncrementalIndexingDialog، کشف‌الآیات، کتاب و Whisper.
- کشف‌الآیات (FF_QURAN): `core/quran` — Quran (متن Tanzil simple-plain در resources/quran با اعلان مجوز، نام‌ها و نام‌های دیگر سوره‌ها، ارجاع‌یابی regex)، Matcher (اسکلت بی‌الف/بی‌اعراب، «و» جدا، شاخص سه‌واژه‌ای، اجراها با تحمل یک واژه، نوع/اطمینان/ابهام، حذف بسم‌الله آغاز سند)، Scanner (پیمایش لوسین با حافظهٔ نهان `<savePath>.quran.json`). ترجمه‌ها: فایل Tanzil کاربر در `%APPDATA%\MATNYAAB\quran\*.txt` (شاخص چهارواژه‌ای). اشارهٔ مفهومی فقط در «آیه در کتابخانه» با SemanticService.
- کتاب با هوش مصنوعی: `core/ai/Book.kt` (PromptTemplate/PromptTemplates در book-templates.json، Outline با قالب `#`/`-`، BookRunner با پیش‌نویس هر بخش در `books/<project>.draft.json` برای ادامه پس از توقف؛ حالت BRIDGE: `{{n}}` جای فیش عیناً)، `ui/BookWizard` (۵ گام) و `TemplateEditor`.
- عامل آزمایشی: `core/ai/Agent.kt` (ابزارهای search/read/save_fish/list_fishes/write_file/finish؛ بی‌بازنویسی فایل؛ ۲۴ گام).
- گفتار به متن (FF_WHISPER): `core/whisper/Whisper.kt` (دانلود با ادامه و SHA-256، ffmpeg→wav ۱۶k→whisper-cli -oj، مسیر ASCII در ProgramData اگر پوشهٔ کاربر فارسی باشد)، `ui/WhisperDialog` (تور سه‌گامی). بسته: `.github/workflows/publish-whisper-pack.yml` (فقط اجرای دستی؛ آزمون TTS→mp3→ffmpeg→whisper با `--whisper-selftest`)، کلید `whisper` در releases.json.
- پیشنهاد کاربران: `ui/FeedbackDialog` → `POST /api/client/feedback` (MessageStore، type=feature/support، source=app). گردآوری در مخزن: `.github/workflows/fetch-feature-requests.yml` (هفتگی/دستی؛ اسکریپت یک‌بارمصرف `ops/feedback-export.php` با توکن تصادفی، بی‌نام و تماس، ایمیل/شماره پوشانده) ← artifact «feature-requests» (json + md). خواندن: `gh run download <id> -n feature-requests`.
- سایت: `/versions` (config/versions.php، بررسی وجود فایل در مرورگر)، بخش تازه‌های ۳٫۱، پرسش‌های متداول، راهنمای PDF در `web/public/docs/matnyaab-guide-3.1.pdf` (منبع: `docs/guide/matnyaab-guide.md`، ساخت: `ops/docs/build-guide.mjs`).
