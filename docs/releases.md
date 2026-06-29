# Releases

Version policy and release history for Scanly.

## Current release

| Field | Value |
| --- | --- |
| Version name | `1.0.9` |
| Version code | `9` |
| Application ID | `in.c1ph3rj.scanly` |
| Room schema | `3` |
| Min SDK | 29 (Android 10) |
| Target / compile SDK | 36 |
| Release date | 2026-06-28 |

Canonical source: `versionCode` and `versionName` in `app/build.gradle.kts`.

The version shown in Settings reads `versionName` via `DefaultSettingsRepository`.

## Versioning policy

- **Version name** — semantic-style `MAJOR.MINOR.PATCH` for user-facing releases.
- **Version code** — monotonically increasing integer required by Play Store and used for in-app update comparison.
- Bump both together on every public release.
- Record user-visible changes in [CHANGELOG.md](../CHANGELOG.md).
- Keep [VERSION.md](../VERSION.md) aligned with Gradle values.

## Recent releases

### 1.0.9 (code 9) — 2026-06-28

- Refined portrait/landscape capture layouts
- Improved capture feedback (lighting, blur, obstruction, framing)
- Retake returns directly to editor after capture
- Renaming no longer replaces first-page thumbnail

### 1.0.8.betaq (code 8) — 2026-06-27

- Removed `REQUEST_INSTALL_PACKAGES`; update opens GitHub release page (superseded by Google Play in-app updates)

### 1.0.7 (code 7) — 2026-06-27

- Update dialog rate-limited to once per 6 hours

### 1.0.4 (code 4) — 2026-06-15

Major feature release:

- Document groups (collections)
- Gallery import (≤10 images)
- Library screen with search and sort
- Storage usage panel and clear-all-data
- Group PDF export (merged and zipped)
- Room schema v3

### 1.0.0 (code 1) — initial

- Offline scanning, editing, persistence, PDF/image export, settings

Full details: [CHANGELOG.md](../CHANGELOG.md)

## Upgrade notes

### From 1.0.0 to 1.0.9

- Room migrates automatically from schema 1 or 2 to 3 (adds document groups).
- Existing documents remain; they appear ungrouped until moved into a collection.
- **Clear all data** (added in 1.0.4) is destructive and cannot be undone.
- No manual migration steps required.

See [VERSION.md](../VERSION.md) for extended upgrade guidance.

## Where version is defined

| Location | Purpose |
| --- | --- |
| `app/build.gradle.kts` | Canonical `versionCode` and `versionName` |
| `DefaultSettingsRepository` | Package version for Settings screen |
| [VERSION.md](../VERSION.md) | Human-readable release metadata |
| [CHANGELOG.md](../CHANGELOG.md) | Per-release notes |

## Related docs

- [development/releasing.md](development/releasing.md) — release checklist
- [data/database.md](data/database.md) — schema migrations