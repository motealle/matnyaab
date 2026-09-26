# Matnyaab Migration Backlog

Last updated: 2026-09-26

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
- TODO — Add broader production health checks to deploy gate.

## P0 — Authentication and customer continuity

- DONE — Map Laravel User model to Django account table.
- DONE — Django PBKDF2 password verification.
- DONE — Login/profile compatibility.
- DONE — Password change.
- DONE — Registration validation and duplicate handling.
- DONE — SMS confirmation logic and retry/delete behavior in tests.
- DONE — Verify legacy SMS config keys exist on the destination host copy.
- BLOCKED — Move SMS values into Laravel runtime secret configuration.
- TODO — Send one controlled real SMS from Laravel.
- TODO — Enable production registration after real SMS success.
- TODO — Port password recovery still required from Django.
- TODO — Review/administer required superuser/admin workflow.

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

- IN PROGRESS — Prepare sanitized source import.
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

- DONE — Remove dependency on broken matnyaab.ir_files snapshot.
- DONE — Restore old navigation aliases.
- DONE — Dark professional design system with Vazirmatn.
- TODO — Responsive QA on mobile/tablet/desktop.
- TODO — Accessibility contrast/focus/keyboard pass.
- DONE — Improve profile/subscription/history presentation.
- TODO — Add polished payment success/failure pages.
- TODO — Improve password recovery UX after backend parity.
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
