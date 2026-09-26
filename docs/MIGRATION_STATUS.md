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
- DNS/FTP credentials are pending.

Readiness estimate for data/infrastructure: **20–30%**.

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
