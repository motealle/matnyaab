# Matnyaab Migration Backlog

Last updated: 2026-09-27

Status legend: DONE, IN PROGRESS, BLOCKED, TODO, DEFERRED.

## P0 — Production health

- DONE — Destination DNS cutover.
- DONE — TLS/HTTPS validation.
- DONE — PHP 8.2 runtime verification.
- DONE — SQLite read/write and integrity verification.
- DONE — Protect SQLite/private files from direct HTTP access.
- DONE — Recovery bridge for desktop API continuity.
- DONE — Laravel deployment outside public document root.
- DONE — Daily encrypted DB/customer backup with seven-day retention.
- DONE — Replace broken saved-HTML homepage.
- DONE — Live homepage link audit; current broken local URLs: 0.
- DONE — Unified professional dark UI for homepage and account pages.
- DONE — Style 404/500/maintenance states.
- IN PROGRESS — Add broader production health checks to deploy gate.

## P0 — Authentication and customer continuity

- DONE — Map Laravel User model to Django account table.
- DONE — Django PBKDF2 password verification.
- DONE — Login/profile compatibility.
- DONE — Password change.
- DONE — Registration validation and duplicate handling.
- DONE — SMS confirmation logic and retry/delete behavior in tests.
- DONE — Verify legacy SMS config keys exist on the destination host copy.
- DONE — Legacy SMS values are synchronized into private runtime configuration outside the public document root; values never enter Git.
- BLOCKED — Send one controlled real SMS from Laravel; production sending remains explicitly gated until a safe test recipient is chosen.
- TODO — Enable `SMS_PRODUCTION_ENABLED` and production registration only after the controlled SMS succeeds.
- DONE — Password-recovery backend and UI; stateless 30-minute HMAC links tested on copied production SQLite.
- BLOCKED — Configure runtime SMTP values and perform one controlled delivery; `MAIL_PRODUCTION_ENABLED` remains false.
- DONE — Superuser migration dashboard, user search and gift-subscription flow; tested on copied production SQLite.

## P0 — Subscription, license and payment

- DONE — Map subscriptions/orders/history/discounts to legacy SQLite.
- DONE — Legacy AES/Base32 serial compatibility vector.
- DONE — 100% discount activation flow.
- DONE — IDPay adapter and fake create/verify tests.
- TODO — Configure real IDPay merchant value outside Git.
- TODO — Controlled real payment transaction.
- TODO — Verify duplicate callback/idempotency on live flow.
- TODO — Verify amount/unit conversion and user-visible payment result.
- TODO — Verify renewal/extension semantics against legacy behavior.
- TODO — Add safe operational payment logging.

## P1 — Desktop API migration

- DONE — Bridge health.
- DONE — Bridge news.
- DONE — Bridge get_contents.
- DONE — Bridge statistics.
- DONE — Bridge content package download.
- DONE — Bridge cover/image download.
- DONE — Preserve updater route.
- IN PROGRESS — Laravel equivalents for core content/news/statistics.
- TODO — Golden request/response fixtures for every desktop endpoint.
- TODO — Move endpoints one at a time from bridge to Laravel.
- TODO — Remove bridge after Kotlin and legacy-client parity passes.

## P1 — Kotlin / Windows client

- DONE — Probe destination-host FTP for likely Kotlin/Java archives; no candidate archive found.
- IN PROGRESS — Sanitized Kotlin source bundle is materialized and inspected; import branch exists and source import/clean build is next.
- TODO — Import Kotlin source under client/windows/.
- TODO — Exclude .idea/, .gradle/ and old build/ output.
- TODO — Remove/harden hard-coded server credentials.
- TODO — Windows clean build with JDK 17 + Gradle 8.7.
- TODO — Resolve missing-source versus stale-built-class discrepancies.
- TODO — Test Lucene/Tika indexing and search.
- TODO — Verify Whoosh import/migration if still required.
- TODO — Verify system-ID and license checks.
- TODO — Verify content pack download/resume behavior.
- TODO — Package installer with jpackage or chosen packager.
- TODO — Verify updater/version contract.
- TODO — Publish versioned GitHub artifact/release.
- TODO — Controlled client rollout.

## P1 — Web UX

- DONE — Full production-copy audit across homepage, account, recovery, payment/error and admin-facing pages.
- DONE — Add product-copy standard and repository review gate.
- DONE — Add Production Copy Guard CI workflow to reject internal/template language.
- DONE — Remove dependency on broken matnyaab.ir_files snapshot.
- DONE — Restore old navigation aliases.
- DONE — Dark professional design system with Vazirmatn.
- DONE — Add persistent dark/light theme toggle with OS-preference fallback.
- DONE — Replace static hero headline treatment with lightweight search-oriented motion/type effect.
- DONE — Restore the authentic original homepage/product screenshot image.
- TODO — Responsive QA on mobile/tablet/desktop.
- TODO — Accessibility contrast/focus/keyboard pass.
- DONE — Improve profile/subscription/history presentation.
- TODO — Add polished payment success/failure pages.
- DONE — Refine password recovery copy and unavailable-state guidance for production.
- TODO — Replace empty favicon with branded icon strategy.

## P1 — Operations and security

- DONE — Git safety checks for DBs, archives, secrets and build output.
- DONE — Staged Laravel release extraction outside docroot.
- DONE — Persistent Laravel app key across releases.
- DONE — Encrypted production DB backup/export workflow.
- TODO — Move SQLite one directory above web root when convenient.
- TODO — Verify permissions after DB relocation.
- TODO — Rotate any legacy credentials that were historically hard-coded/shared.
- TODO — Add release manifest with commit SHA/deployed-at.
- TODO — Add explicit rollback workflow for previous Laravel release.
- TODO — Clean up old Laravel releases automatically.
- TODO — Add minimal uptime monitoring.

## P2 — Data/platform modernization

- DEFERRED — SQLite to MySQL migration; SQLite remains intentionally in production until the PHP/Kotlin transformation is stable.
- TODO — Design idempotent SQLite to MySQL migration after stabilization.
- TODO — Rehearse migration on a copy and reconcile row counts.
- TODO — Normalize persistent content-pack storage.
- TODO — Add checksums/version manifest for content packs.

## Definition of complete

Migration is complete only when Laravel serves all required web/API behavior, existing users/subscriptions/licenses remain valid, real SMS/payment flows are verified, Kotlin client is reproducibly built and packaged in Actions, updater works, backups remain independent of deploys, the bridge is removed, production smoke tests pass, and HANDOFF/BACKLOG/MIGRATION_STATUS match reality.


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
