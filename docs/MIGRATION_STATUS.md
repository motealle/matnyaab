# Migration status

Date: 2026-09-26

This document records an **inventory-level assessment**. Source bodies from the supplied RAR archives still require a full code audit and clean builds before the percentages below are treated as verified.

## Current picture

### Legacy web service
- Django/Python
- SQLite application database (`db.sqlite3`)
- Django REST Framework endpoints
- User registration/login/password reset
- SMS confirmation
- Subscriptions/orders/discounts
- Payment gateway flow
- Content/news/statistics management
- License/serial-related logic
- Large downloadable content packs stored outside the application database

### New web service
A Laravel application is present with controllers/models/services/migrations/routes/Blade views covering the major legacy domains:
- authentication/users
- contents/downloads
- news
- statistics
- subscriptions/orders/history
- discount codes
- payment transaction/service
- SMS service
- serial service
- superuser access

Structural completeness estimate: **65–75%**.
This is not a behavioral parity score.

### Legacy Windows client
- Python/PyQt
- Whoosh search/index
- Tika integration
- HTTP requests
- WMI/system identity
- crypto/license logic

### New Windows client
A Kotlin/JVM project is present with Lucene/Tika-based indexing/search, settings, networking, licensing, subscription/content-pack UI and compiled build output.

Structural completeness estimate: **70–80%**.
A clean build is still mandatory because generated classes appear to contain classes not obviously represented in the source inventory.

### Data/infrastructure
This is the least complete area:
- the supplied SQL dump is MySQL `information_schema`, not the application database;
- the legacy application database remains SQLite;
- no verified SQLite → target DB import has been completed;
- production CI/CD is not configured yet;
- GitHub Actions FTP credentials are configured;
- FTP smoke test passed from GitHub Actions on 2026-09-26: authentication, remote listing, temporary upload and cleanup succeeded;
- the exact production deploy path and release/rollback workflow still need to be fixed before automatic deployment is enabled.

Readiness estimate for data/infrastructure: **30–40%**.

## Highest-risk compatibility gates

Before production cutover, all of these must pass:

1. **Database migration**
   - schema mapping from legacy SQLite to Laravel tables
   - row counts and key relationships reconciled
   - media/file paths preserved
   - dates/timezones verified

2. **Existing user accounts**
   - Django password hashes must continue to work or use a controlled rehash/reset strategy
   - registration/login/reset flows must be tested end-to-end

3. **Licenses and serials**
   - old and new AES/serial/system-ID implementations must be compared with golden test vectors
   - existing licenses must remain valid unless a deliberate migration is performed

4. **Payments**
   - callback validation, signature/status checks, duplicate callbacks and idempotency
   - subscription activation only after confirmed payment
   - old gateway behavior versus the new provider/service must be explicitly reconciled

5. **SMS/OTP**
   - expiration, retry limits, rate limiting and provider failures

6. **Desktop API contract**
   - content/news/statistics/subscription/update endpoints
   - error shapes, auth behavior and backward compatibility

7. **Content packs**
   - do not deploy multi-GB content archives from Git
   - preserve server-side content separately from application releases
   - verify authorization, resumable downloads and checksums if supported

8. **Clean reproducible builds**
   - delete `build/`, `.gradle/`, IDE output and compile from source only
   - build/package Windows client in CI
   - install Laravel dependencies from lock files in CI

## Fastest safe migration sequence

### Phase 0 — Freeze and inventory
- preserve a read-only copy of the currently working Django site and SQLite DB
- hash critical legacy binaries/content packs
- document existing public endpoints and payment/SMS behavior

### Phase 1 — Import sanitized source
- import Laravel into `web/`
- import Kotlin client into `client/windows/`
- keep only useful source reference under `legacy/`
- do not import production secrets/databases/content packs/build output

### Phase 2 — Contract and parity tests
- capture legacy API request/response fixtures
- run the same fixtures against Laravel
- create golden vectors for serial/license behavior
- add authentication/payment/SMS tests

### Phase 3 — Data migration
- inspect the actual SQLite schema
- produce an idempotent migration/import tool
- rehearse on a copy
- compare row counts, totals and samples
- repeat immediately before cutover

### Phase 4 — Shared-host deployment
- build a release artifact in GitHub Actions
- include Composer production dependencies when the host cannot run Composer
- deploy application files only
- preserve `.env`, uploads, content packs and runtime storage
- expose a minimal health endpoint and smoke-test after deployment

### Phase 5 — Client release
- point a staging client to the new API
- verify old index/content compatibility
- package/sign installer
- test updater/version behavior
- release only after server parity gates pass

### Phase 6 — Cutover and rollback window
- short maintenance window
- final SQLite export/import
- switch DNS/app API target
- smoke tests
- keep the old stack recoverable until metrics and payments are stable

## Repository policy

The repository is currently public. Never commit:
- production `.env`
- database dumps
- API/payment/SMS credentials
- private signing keys
- real license secrets
- large content packs
- generated installers
- Gradle/Laravel dependency/build directories


## Latest operational checkpoint

- Repository bootstrap branch: `migration/bootstrap-2026-09-26`
- FTP workflow: `.github/workflows/ftp-smoke.yml`
- Required secret names: `FTP_SERVER`, `FTP_USER`, `FTP_PASSWORD_MATNYAAB_IR`
- FTP smoke run: GitHub Actions run `36221102295` — **passed**
- Verified capabilities: authenticate, list remote root, upload a temporary marker, remove the marker
- No secret values are stored in Git.
- Next deployment blocker: determine the correct application document root / remote deploy path, then add release + rollback deployment.


## Production checkpoint — 2026-09-26

- DNS points to the destination host; there is no active dependency on the retired source host.
- Production PHP: 8.2; strict TLS is passing.
- Production database remains the original SQLite database by design for this migration phase.
- SQLite integrity check is passing.
- Laravel 12 is staged outside the public document root and reads the production SQLite schema directly.
- Laravel canary currently serves account/web routes while desktop API traffic still uses the compatibility bridge.
- Django PBKDF2 login compatibility is tested against a copied production database.
- Subscription activation and legacy AES/Base32 serial compatibility are tested.
- IDPay create/verify behavior is covered by mocked HTTP tests; live merchant activation remains a production gate.
- Registration/SMS confirmation code is implemented and tested. Legacy SMS configuration was verified to exist in `matnyaab_with_license/Django_Project/private.py`, but values have not been copied into Laravel runtime configuration yet.
- The broken saved-HTML homepage was replaced by a self-contained Laravel homepage. Live link audit went from 15 broken local URLs to 0.
- The current installer URL responds successfully.
- A daily encrypted backup workflow now verifies SQLite integrity and produces:
  - full `db.sqlite3`
  - customer summary CSV
  - purchase history CSV
  - Excel workbook
- Current data snapshot: 153 accounts, 94 distinct purchasers, 116 subscription-history rows, 137 completed orders, 66 gateway transaction rows.

### Remaining go-live work

1. Move legacy SMS credentials into Laravel runtime configuration without committing them.
2. Validate one controlled real SMS registration flow.
3. Configure/validate the real payment merchant and perform a controlled payment/refund-safe verification.
4. Port remaining legacy web flows (password recovery/admin functions that are still required).
5. Import sanitized Kotlin client source and obtain a reproducible Windows clean build in GitHub Actions.
6. Verify updater/install packaging and publish the Kotlin client only after server parity is stable.
7. Move remaining desktop API routes from the compatibility bridge to Laravel, then remove the bridge.
