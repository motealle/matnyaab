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

Before release: import source only, remove generated/IDE output and hard-coded credentials, clean-build in Windows Actions, package installer, verify updater and license/content compatibility.

Latest verified backend state:
- Laravel compatibility suite is running on main against an isolated copy of production SQLite.
- Password recovery is implemented with 30-minute stateless HMAC links and tested.
- Superuser migration dashboard and gift-subscription flow are implemented and tested.
- Latest Kotlin archive probe completed successfully as a workflow, but found no client source archive in the destination-host FTP root/candidate paths. This is a discovery blocker, not a build failure.

## Immediate next work

See BACKLOG.md. Current order:

1. activate SMS safely and test one controlled registration;
2. configure runtime SMTP safely and test one controlled password-recovery delivery;
3. verify real payment with one controlled transaction;
4. import and clean-build Kotlin client using the sanitized source attachment if the FTP copy remains unavailable;
5. migrate remaining desktop API routes to Laravel;
6. remove the bridge after parity.

## Current blockers

### Runtime secrets
SMS, SMTP and real IDPay values exist only in legacy/private configuration or need to be supplied as runtime secrets. Do not copy them into Git. Until runtime SMS is configured, new registration remains intentionally gated. Until SMTP is configured, password-recovery email remains intentionally gated. Until the merchant value is configured, real paid checkout remains intentionally gated.

### Kotlin source location
The destination host FTP does not currently expose a discoverable Kotlin/Java source archive at the probed names/paths. The project conversation contains the original NEW JAVA PROJECT archive and a sanitized source bundle, so the practical fallback is to import from that bundle into a migration branch and let Windows CI clean-build it.

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
- Public publication was explicitly authorized by the owner on 2026-09-27; branch CI and deployment verification are pending. PHP/Composer are unavailable in this workspace, so no passing test or production deployment is claimed. CI execution remains pending publication. Production routing remains on the recovery bridge; this change does not cut over API routes.
- Next: publish and verify the prepared fix and deployment gate, finish golden API fixtures, then cut over endpoints individually. SMS needs an explicitly selected test recipient; SMTP and real payments retain their runtime gates.
