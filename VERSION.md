# Scanly Version Details

See also [docs/releases.md](docs/releases.md) for release policy and history within the full documentation set.

## Current Release

| Field | Value |
| --- | --- |
| Version name | `1.0.18` |
| Version code | `18` |
| Application ID | `in.c1ph3rj.scanly` |
| Min SDK | 29 (Android 10) |
| Target / compile SDK | compile 37 / target 36 |
| Room schema version | `7` |
| Release date | 2026-10-20 |
| Branch | `master` |

The version shown in **Settings** is read from `versionName` in `app/build.gradle.kts`.

**Database:** Room schema **6** adds on-device page text and its FTS4 search index (`MIGRATION_5_6`). Schema **7** adds saved signatures and editable per-page marks (`MIGRATION_6_7`). Existing pages are indexed after upgrade. Migrations run automatically. Library backup and restore are no longer available in v1.0.18.

## Versioning Policy

- **Version name** follows semantic-style `MAJOR.MINOR.PATCH` strings for user-facing releases.
- **Version code** is a monotonically increasing integer required by Google Play; GitHub builds compare `versionName` with the latest release tag.
- Bump both values together whenever you ship a public release.
- Record user-visible changes in `CHANGELOG.md` and keep this file aligned with the Gradle values.

## Release History

### 1.0.18 (version code 18)

On-device OCR and page-text search, document-type labels, searchable extracted text, and saved signatures. Sign PDFs or place editable marks on scans; signing supports signatures, initials, text, dates, and checkboxes. Adds Pip (an optional in-app helper with camera coaching and daily tips), a PDF to images tool, Open in Scanly for PDFs from other apps, a reader menu that hands the open file to any PDF tool, and a camera step in onboarding. The camera, camera permission screen, empty states, new-document covers and Home loading were redesigned. Help & FAQ and Settings storage were refreshed, and library backup and restore were removed. Room schema 7. See [CHANGELOG.md](CHANGELOG.md) for full details.

### 1.0.17 (version code 17)

A rebuilt page filter engine with an Intensity control, a redesigned Library (folder shelf, dated sections, dedicated search) and Home (search bar, matching recents), and a redesigned Tools area: every PDF tool follows choose → options → done with a real result preview and editable file name, a rebuilt watermark engine, password editing restrictions, and a QR flow with typed results, five create types and on-phone history. Also a clearer light theme and a fixed navigation bar. Room schema 5. See [CHANGELOG.md](CHANGELOG.md) for the complete release notes.

### 1.0.16 (version code 16)

Library and Home paper cards with overflow actions, portrait document page tiles, a document-style PDF reader, a quieter Tools workspace (QR, merge, compress, password, watermark), camera permission that asks before Settings, 16 KB-aligned OpenCV, and the professional page filter engine. See [CHANGELOG.md](CHANGELOG.md) for the complete release notes.

### 1.0.14 (version code 14)

Illustrated empty states and multi-select folder organization. See [CHANGELOG.md](CHANGELOG.md) for the complete release notes.

### 1.0.13 (version code 13)

QR release stability fix for minified builds. The release also keeps the 1.0.11 editor, widget, document-detection, and large-screen improvements. See [CHANGELOG.md](CHANGELOG.md) for the complete release notes.

### 1.0.11 (version code 11)

Current app release metadata (editor tools, home-screen actions, document detection improvements, and large-screen polish). See [CHANGELOG.md](CHANGELOG.md) for the complete release notes.

### 1.0.10 (version code 10)

See [CHANGELOG.md](CHANGELOG.md) for release notes.

### 1.0.9 (version code 9)

See [CHANGELOG.md](CHANGELOG.md) for release notes.

### 1.0.8.betaq (version code 8)

See [CHANGELOG.md](CHANGELOG.md) for release notes.
