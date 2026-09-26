# Matnyaab Engineering Skills / Playbooks

This file describes the practical playbooks needed to continue the migration safely.

## 1. Laravel + legacy SQLite compatibility

Use when changing authentication, subscriptions, payments, content APIs, or admin flows.

Checklist:

- map Eloquent models to existing Django table names;
- avoid schema changes unless explicitly rehearsed on a copy;
- run feature tests against an isolated copy of production SQLite;
- preserve Django password compatibility and serial/license vectors;
- verify row cleanup in FK-safe order;
- smoke the live route after deployment.

## 2. Production deployment on shared hosting

Use for web/backend changes.

Checklist:

- GitHub Actions builds Composer production dependencies;
- package application code only;
- upload temporary release archive/deployer;
- extract Laravel outside document root;
- preserve persistent DB/content;
- publish through the public front controller/rewrite layer;
- remove temporary public deployment files;
- verify PHP version, SQLite access, TLS and route smoke tests.

## 3. Recovery bridge migration

Use while desktop API parity is incomplete.

Strategy:

- leave stable legacy-compatible endpoints on the bridge;
- implement Laravel equivalent;
- create golden request/response fixtures;
- compare behavior;
- route one endpoint at a time to Laravel;
- retain rollback path;
- remove bridge only after client/server parity is proven.

## 4. Production data backup

Use before any risky write or customer-affecting test.

Checklist:

- download SQLite through authenticated FTP;
- run `PRAGMA integrity_check`;
- export customer and purchase reports;
- include the complete DB;
- encrypt the archive;
- retain only the defined short period;
- never commit plaintext customer data.

## 5. SMS / SMTP / payment secrets

Use when enabling real external services.

Rules:

- secrets live only in runtime environment or GitHub Secrets;
- never echo values;
- use mocks/fakes in CI;
- enable real service with one controlled transaction/message first;
- verify DB side effects and idempotency;
- keep backup before payment tests.

## 6. Web UI and homepage

Use for UX changes.

Checklist:

- build on `layouts.app`;
- keep Vazirmatn;
- provide dark/light theme parity;
- store theme locally and honor system preference;
- support `prefers-reduced-motion`;
- keep authentic product screenshot/image accessible;
- run responsive/keyboard/contrast checks;
- run the live homepage asset audit after deploy.

## 7. Kotlin Windows client clean build

Use once sanitized source is available.

Checklist:

- import source to `client/windows/`;
- remove IDE/build/generated output and hard-coded secrets;
- inspect Gradle project structure;
- build on Windows runner with JDK 17 and pinned Gradle;
- fix source/build mismatches rather than relying on stale compiled classes;
- test Lucene/Tika indexing/search, system ID, licensing and content downloads;
- produce installer only after clean build passes;
- verify updater/version contract before controlled rollout.

## 8. Documentation discipline

At the end of each meaningful milestone:

- update `docs/HANDOFF.md`;
- update statuses in `docs/BACKLOG.md`;
- update `docs/MIGRATION_STATUS.md` when architecture/readiness materially changes;
- record blockers with a concrete fallback path;
- keep reported production counts and workflow status dated.
