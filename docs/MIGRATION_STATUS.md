# Migration status

Date: 2026-09-27

This document is the current operational migration status for matnyaab.ir. For execution details see HANDOFF.md; for remaining work see BACKLOG.md.

## Production architecture

### Web/backend

- DNS points to the destination shared host.
- TLS is valid.
- PHP 8.2 is active.
- Laravel 12 is deployed outside the public document root by GitHub Actions.
- The original Django SQLite database remains the production application database by design.
- SQLite integrity checks are passing.
- Public Laravel routes are exposed through the destination-host compatibility/front-controller layer.
- Desktop API routes that have not completed parity remain on the PHP recovery bridge.

### Data

Latest verified production snapshot:

- 153 accounts
- 94 distinct purchasers
- 116 subscription-history rows
- 137 completed orders
- 66 gateway transaction rows

Daily encrypted backups include the full SQLite DB plus customer/purchase CSV and Excel reports.

## Completed compatibility work

### Users/authentication

- Django PBKDF2 password verification in Laravel
- login/profile compatibility
- password change
- registration validation
- SMS confirmation/retry behavior in tests
- stateless 30-minute password-recovery links
- superuser migration dashboard and gift-subscription flow

### Subscription/license/payment

- legacy subscription/order/history/discount table mapping
- AES/Base32 serial compatibility
- free/100% discount activation
- IDPay create/verify logic with fake HTTP tests
- legacy gateway table reuse without requiring a production schema migration

### Desktop/API continuity

Recovery bridge currently covers:

- health
- news
- get_contents
- statistics
- content package download
- content image/cover download
- updater/version path compatibility

### Web UX

- broken saved-HTML homepage removed
- old navigation aliases restored
- unified Vazirmatn design system for homepage/account/error pages
- persistent light/dark theme switching is live
- original product screenshot is restored and covered by live asset checks
- homepage motion respects reduced-motion preference
- live homepage audit reports zero broken local URLs
- full customer-facing copy audit is complete; engineering/migration/template language has been removed from production UI and guarded in CI

## Current production gates

### SMS

Legacy SMS keys exist in the destination-host copy of Django_Project/private.py. Values are intentionally not stored in Git and have already been synchronized to private Laravel runtime configuration. New registration stays gated until one controlled real SMS succeeds.

### Email/password recovery

Password-recovery code is complete. Legacy email configuration keys have been identified, but production mail delivery remains gated until runtime SMTP configuration is supplied and a controlled delivery succeeds.

### Real payments

IDPay logic is implemented/tested with fake HTTP. Paid checkout remains gated until the real merchant configuration is supplied and a controlled payment verifies amount, callback idempotency, subscription activation and serial generation.

### Kotlin/Windows client

Target stack:

- Kotlin/JVM
- JDK 17
- Gradle 8.7
- JavaFX
- Lucene
- Tika

The Kotlin source is now restored under `client/windows/` on main. PR #6 passed a Windows clean build on JDK 17 + Gradle 8.7, ran downloader regression tests, and produced a real Windows artifact of about 503 MB. Download writes are staged and only replace the target after complete success. The principal remaining release gate is behavioral parity, especially the legacy system-ID/license result on the same Windows machine, followed by representative indexing/search, content-pack and installer/updater smoke tests.

## CI/CD

Configured workflows include:

- CI
- Build
- Deploy Laravel Stage
- Deploy Recovery Bridge
- Laravel Legacy SQLite Smoke
- Homepage Asset Audit
- Private Data Backup
- Legacy SMS Config Audit
- Client Source Discovery
- Probe Kotlin Archive

GitHub Actions FTP authentication and production deploys are operational.

## Remaining sequence

1. prove legacy system-ID/license parity for the Kotlin Windows client;
2. verify Lucene/Tika indexing/search and content-pack behavior with representative inputs;
3. package and smoke-test the Windows installer/updater;
4. create remaining golden desktop API fixtures and move endpoints from bridge to Laravel one at a time;
5. verify controlled real SMS, SMTP and IDPay flows when safe runtime test inputs are available;
6. remove the bridge only after client/server parity is proven;
7. defer SQLite-to-MySQL until the transformed stack is stable.

## Repository policy

The repository is public. Never commit production databases, .env files, SMS/payment/SMTP credentials, private signing keys, real customer exports, content packs, installers, Gradle build output, IDE metadata or font binaries.


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
