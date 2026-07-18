# Scanly Version Details

See also [docs/releases.md](docs/releases.md) for release policy and history within the full documentation set.

## Current Release

| Field | Value |
| --- | --- |
| Version name | `1.0.12.1` |
| Version code | `12` |
| Application ID | `in.c1ph3rj.scanly` |
| Min SDK | 29 (Android 10) |
| Target / compile SDK | 36 |
| Room schema version | `4` |
| Release date | 2026-07-18 |
| Branch | `feature/v1.0.12.1` |

The version shown in **Settings** is read from `versionName` in `app/build.gradle.kts`.

**Note:** Room schema **4** adds per-page filter adjustment columns. Existing libraries open through `MIGRATION_3_4`, and older `.scanly` backups restore with identity adjustment defaults.

## Versioning Policy

- **Version name** follows semantic-style `MAJOR.MINOR.PATCH` strings for user-facing releases.
- **Version code** is a monotonically increasing integer required by Google Play; GitHub builds compare `versionName` with the latest release tag.
- Bump both values together whenever you ship a public release.
- Record user-visible changes in `CHANGELOG.md` and keep this file aligned with the Gradle values.

## Release History

### 1.0.12.1 (version code 12)

QR release stability fix for minified builds. The release also keeps the 1.0.11 editor, widget, document-detection, and large-screen improvements. See [CHANGELOG.md](CHANGELOG.md) for the complete release notes.

### 1.0.11 (version code 11)

Current app release metadata (editor tools, home-screen actions, document detection improvements, and large-screen polish). See [CHANGELOG.md](CHANGELOG.md) for the complete release notes.

### 1.0.10 (version code 10)

See [CHANGELOG.md](CHANGELOG.md) for release notes.

### 1.0.9 (version code 9)

See [CHANGELOG.md](CHANGELOG.md) for release notes.

### 1.0.8.betaq (version code 8)

See [CHANGELOG.md](CHANGELOG.md) for release notes.
