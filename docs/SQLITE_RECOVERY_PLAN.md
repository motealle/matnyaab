# SQLite-first recovery plan

Date: 2026-09-26

## Recovery objective

The immediate objective is to restore the Matnyaab service on the destination PHP host **without changing database technology**.

For this phase:

- production data remains SQLite;
- the existing Django `db.sqlite3` is the source of truth;
- MySQL migration is deferred;
- no destructive schema conversion is allowed during recovery;
- GitHub Actions is the source of truth for builds and deployment.

## Verified destination runtime

GitHub Actions runtime probing against the destination host confirmed:

- PHP 8.1.34
- `pdo_sqlite` enabled
- `sqlite3` enabled
- SQLite read/write smoke test passed
- document root is writable by PHP
- parent directory of document root exists and is writable by PHP

The bare `https://matnyaab.ir` endpoint currently has a TLS hostname mismatch. The `www.matnyaab.ir` hostname is the canonical application hostname used by the desktop client and must remain the deployment target until TLS for the bare host is corrected.

## Verified legacy database

The live copied legacy database passed `PRAGMA integrity_check`.

Database size: 1,527,808 bytes.

Important row counts:

| Legacy table | Rows |
|---|---:|
| `users_accountmodel` | 153 |
| `users_ordermodel` | 259 |
| `users_subscriptionhistorymodel` | 116 |
| `users_subscriptionmodel` | 5 |
| `orderdiscount_orderdiscountmodel` | 7 |
| `matnyaab_contentsmodel` | 9 |
| `news_news` | 2 |
| `matnyaab_statistics` | 10,590 |
| `azbankgateways_bank` | 66 |

## Fastest recovery architecture

Do **not** migrate legacy rows into a second schema for the first recovery release.

Instead, map Laravel models directly to the existing Django tables. This keeps the database file unchanged and avoids an unnecessary data-conversion failure mode during recovery.

### Model mapping

| Laravel domain model | Existing SQLite table |
|---|---|
| User | `users_accountmodel` |
| Subscription | `users_subscriptionmodel` |
| Order | `users_ordermodel` |
| SubscriptionHistory | `users_subscriptionhistorymodel` |
| OrderDiscount | `orderdiscount_orderdiscountmodel` |
| Content | `matnyaab_contentsmodel` |
| Statistic | `matnyaab_statistics` |
| News | `news_news` |
| Legacy gateway transaction | `azbankgateways_bank` |

The first recovery release must set `$timestamps = false` for these legacy models unless a table actually has Laravel timestamps.

Where Laravel-friendly property names differ from Django column names, use model accessors/mutators or explicit controller mappings rather than altering the production schema.

## Authentication compatibility

Existing password values are Django password hashes. Standard Laravel `Hash::check()` cannot be assumed to validate them.

Recovery login must:

1. locate the legacy user by `username`;
2. validate the existing Django hash with a dedicated Django-compatible verifier;
3. call Laravel session authentication on success;
4. optionally rehash the password into Laravel format only after successful login.

Do not bulk-reset or bulk-rehash passwords.

## Database location

Initial bring-up may use the database at its current location.

Before public production stabilization, prefer moving the SQLite file outside the document root, for example:

```text
/account-private/matnyaab/db.sqlite3
/public_html/matnyaab.ir/...
```

Laravel receives the absolute path through `DB_DATABASE`.

The database itself must never be committed to GitHub or packaged inside release artifacts.

## Deployment rule

Application releases may replace code, `vendor/`, and public assets.

Deployment must preserve:

- the SQLite database;
- content archives and cover images;
- `.env`;
- persistent storage;
- update installers/version files until their new ownership is explicit.

## Recovery gates

The first production release is allowed only after all of these pass:

1. PHP 8.1 build in GitHub Actions.
2. SQLite read-only connection to a copy of the legacy database.
3. Home page HTTP 200.
4. Existing user login with Django hash.
5. `/news/` contract test.
6. `/get_contents/` contract test.
7. `/download_content/` and cover-image smoke tests.
8. Statistics POST.
9. Subscription/profile read.
10. Payment flow tested separately before enabling new purchases.

## Deferred work

After recovery is stable:

- normalize schema;
- migrate SQLite to MySQL only if operationally useful;
- replace the shared content API password with a stronger client API scheme;
- add signed Windows builds and updater artifacts;
- correct bare-domain TLS and canonical redirect.
