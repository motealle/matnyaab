# AGENTS.md — Matnyaab Repository Operating Rules

These rules apply to humans and coding agents working in this repository.

## Source of truth

Before making changes, read:

1. `docs/HANDOFF.md`
2. `docs/BACKLOG.md`
3. `docs/MIGRATION_STATUS.md`
4. `docs/SKILLS.md`

After any meaningful production, architecture, deployment, compatibility, or blocker change, update HANDOFF and BACKLOG in the same work session.

## Production safety

- Production data is the existing SQLite database. Never commit it.
- Never run destructive schema migrations against production SQLite during the migration phase.
- Code rollback must not roll back or overwrite persistent customer data.
- Before risky production writes, verify a fresh encrypted backup and SQLite integrity.
- Keep the recovery bridge until Laravel and the Windows client prove API parity.
- Do not expose secrets, tokens, payment credentials, SMS credentials, SMTP credentials, serial secrets, or real customer data in Git or CI logs.

## Deployment

- Laravel releases are built in GitHub Actions.
- Production Laravel lives outside the public document root and is reached through the public front controller.
- Persistent data/content must not be packaged inside application releases.
- Every production-facing route change must be followed by smoke tests.
- Homepage/local asset integrity must remain covered by the live audit workflow.
- Prefer small, reversible route cutovers over one large switch.

## Legacy compatibility

Preserve these behaviors unless a deliberate migration is documented and tested:

- Django PBKDF2 passwords
- existing users and system IDs
- subscription/history/order semantics
- AES/Base32 license serial format
- payment callback idempotency
- updater/version contract
- desktop API response shapes
- content-pack paths and authorization

## Web UI rules

- Use the shared Blade layout/design system.
- Vazirmatn is the preferred typeface; do not commit font binaries.
- Support dark and light themes.
- Theme selection must persist in local storage and respect OS preference on first load.
- Motion must respect `prefers-reduced-motion`.
- Do not add fragile local asset paths without adding them to the live asset audit.
- Keep real product imagery/screenshots where useful; mock UI may supplement, not replace, authentic visuals.
- Maintain RTL, responsive behavior, keyboard focus states, and readable contrast.

## Kotlin client rules

- Target JDK 17 and the pinned Gradle version in CI.
- Import source only; no `.idea/`, `.gradle/`, old `build/`, generated installers, or font binaries.
- Remove hard-coded credentials before merge.
- A clean Windows CI build from source is mandatory before release.
- Package/update work happens only after server API parity is stable.

## Git / workflow rules

- Prefer focused commits with descriptive messages.
- Use migration branches for risky client/import work.
- Keep `main` deployable.
- Do not bypass failing compatibility tests to force a release.
- Generated private backups must remain encrypted and short-lived.
