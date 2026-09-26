# Matnyaab

Migration workspace for **matnyaab.ir**.

The project is being migrated from:

- Django/Python web backend → Laravel/PHP on lower-cost shared hosting
- Python/PyQt/Whoosh Windows client → Kotlin/JVM desktop client using Lucene/Tika

## Target repository layout

```text
.
├── web/                 # Laravel application (backend + Blade web frontend)
│   ├── app/
│   ├── resources/views/ # Web frontend
│   ├── routes/
│   └── tests/
├── client/
│   └── windows/         # Kotlin/JVM desktop client
├── legacy/              # Selected source-only migration references
├── docs/                # Architecture, parity and migration notes
├── ops/                 # Deployment manifests/scripts (no secrets)
└── .github/workflows/   # CI/CD
```

Large content packs, production databases, secrets, generated installers and build output are intentionally **not stored in Git**.

## Migration rules

1. Preserve production behavior before redesigning it.
2. Keep the old service available until parity tests pass.
3. Migrate application data explicitly; do not treat MySQL `information_schema` as application data.
4. Validate compatibility for existing user accounts, subscriptions, licenses/serials, payment callbacks and content downloads.
5. Deploy code separately from persistent data/content packs.
6. All production credentials must be supplied through hosting configuration or GitHub Actions secrets.

Operational docs:

- [Repository operating rules](AGENTS.md)
- [Engineering handoff](docs/HANDOFF.md)
- [Migration backlog](docs/BACKLOG.md)
- [Engineering skills / playbooks](docs/SKILLS.md)
- [Product copy standard](docs/PRODUCT_COPY.md)
- [Migration status / go-live gates](docs/MIGRATION_STATUS.md)
