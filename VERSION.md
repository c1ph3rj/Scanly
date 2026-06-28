# Scanly Version Details

## Current Release

| Field | Value |
| --- | --- |
| Version name | `1.0.9` |
| Version code | `9` |
| Application ID | `in.c1ph3rj.scanly` |
| Min SDK | 29 (Android 10) |
| Target / compile SDK | 36 |
| Room schema version | `3` |
| Release date | 2026-06-28 |
| Branch | `v1.0.9` |

The version shown in **Settings** is read from `versionName` in `app/build.gradle.kts`.

## Versioning Policy

- **Version name** follows semantic-style `MAJOR.MINOR.PATCH` strings for user-facing releases.
- **Version code** is a monotonically increasing integer required by Google Play and used for upgrade checks.
- Bump both values together whenever you ship a public release.
- Record user-visible changes in `CHANGELOG.md` and keep this file aligned with the Gradle values.

## Release History

### 1.0.9 (version code 9)

Current app release metadata.

See [CHANGELOG.md](CHANGELOG.md) for release notes.

### 1.0.8.betaq (version code 8)

Current app release metadata.

See [CHANGELOG.md](CHANGELOG.md) for release notes.

### 1.0.7 (version code 7)

See [CHANGELOG.md](CHANGELOG.md) for release notes.

### 1.0.6 (version code 6)

See [CHANGELOG.md](CHANGELOG.md) for release notes.

### 1.0.5 (version code 5)

See [CHANGELOG.md](CHANGELOG.md#104---2026-06-15) for the previous release note.

### 1.0.0 (version code 1)

Initial open-source baseline on `master`:

- offline document scanning workflow
- camera capture, page editing, and local persistence
- PDF and image export/share
- settings, theme persistence, and support content

## Where Version Is Defined

| Location | Purpose |
| --- | --- |
| `app/build.gradle.kts` | Canonical `versionCode` and `versionName` |
| `DefaultSettingsRepository` | Reads package version for the Settings screen |
| `VERSION.md` | Human-readable release metadata (this file) |
| `CHANGELOG.md` | Per-release notes and upgrade guidance |

## Upgrade Notes

### From 1.0.0 to 1.0.9

- Room migrates automatically from schema version 1 or 2 to 3 to add document groups.
- Existing documents remain available; they appear as ungrouped until moved into a collection.
- No manual migration steps are required for local data.
