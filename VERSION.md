# Scanly Version Details

See also [docs/releases.md](docs/releases.md) for release policy and history within the full documentation set.

## Current Release

| Field | Value |
| --- | --- |
| Version name | `1.0.10` |
| Version code | `10` |
| Application ID | `in.c1ph3rj.scanly` |
| Min SDK | 29 (Android 10) |
| Target / compile SDK | 36 |
| Room schema version | `4` |
| Release date | 2026-07-13 |
| Branch | `feature/v1.0.10` |

The version shown in **Settings** is read from `versionName` in `app/build.gradle.kts`.

**Note:** Room schema **4** (filter adjustment columns) is on this development branch and is required by the page-editor filter-adjust work under [Unreleased](CHANGELOG.md). Builds on this branch open existing libraries through `MIGRATION_3_4`.

## Versioning Policy

- **Version name** follows semantic-style `MAJOR.MINOR.PATCH` strings for user-facing releases.
- **Version code** is a monotonically increasing integer required by Google Play; GitHub builds compare `versionName` with the latest release tag.
- Bump both values together whenever you ship a public release.
- Record user-visible changes in `CHANGELOG.md` and keep this file aligned with the Gradle values.

## Release History

### 1.0.10 (version code 10)

Current app release metadata (Tools, multi-model detection, large-screen polish). Editor crop/filter-adjust work continues on this branch; see [CHANGELOG.md](CHANGELOG.md) **Unreleased**.

### 1.0.9 (version code 9)

See [CHANGELOG.md](CHANGELOG.md) for release notes.

### 1.0.8.betaq (version code 8)

See [CHANGELOG.md](CHANGELOG.md) for release notes.
