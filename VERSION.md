# Scanly Version Details

See also [docs/releases.md](docs/releases.md) for release policy and history within the full documentation set.

## Current Release

| Field | Value |
| --- | --- |
| Version name | `1.0.21` |
| Version code | `21` |
| Application ID | `in.c1ph3rj.scanly` |
| Min SDK | 29 (Android 10) |
| Target / compile SDK | compile 37 / target 36 |
| Room schema version | `12` |
| Release status | Prepared for release (2026-11-11) |
| Branch | `master` |

The version shown in **Settings** is read from `versionName` in `app/build.gradle.kts`.

**Database:** Room schema **12**. Schemas **8–10** add Recently deleted (`MIGRATION_7_8`, `MIGRATION_8_9`, `MIGRATION_9_10`), schema **11** records the OCR engine for each page (`MIGRATION_10_11`), and schema **12** adds derived search text and rebuilds the page-text index (`MIGRATION_11_12`). Migrations run automatically; existing pages are re-indexed in the background without being read again.

## Versioning Policy

- **Version name** follows semantic-style `MAJOR.MINOR.PATCH` strings for user-facing releases, with an optional testing suffix (for example `1.0.19-beta.1`; the current release `1.0.21` has none). The suffix is a display label; Play Store uploads still use a signed, non-debuggable `playStoreRelease` build.
- **Version code** is a monotonically increasing integer required by Google Play; GitHub builds compare `versionName` with the latest release tag.
- Bump both values together whenever you ship a public release.
- Record user-visible changes in `CHANGELOG.md` and keep this file aligned with the Gradle values.

## Release History

### 1.0.21 (version code 21)

Split PDF (custom ranges, every N pages, or extract pages), Split a document into new documents, PDF tools straight from a document's ⋮ menu, illustrated Tools hub tiles, the Paper Atelier onboarding, Send feedback with image attachments, and a Rate Scanly fix. Room schema stays 12. See [CHANGELOG.md](CHANGELOG.md) for full details.

### 1.0.20 (version code 20)

Scanly in 25 languages, the experimental Indra OCR engine (multi-script, on-device, with ML Kit fallback) and a dedicated Text recognition page, Recently deleted with Undo, Add PDF to library, Image to text and My signatures tools, much stronger search (filters, suggestions, exclusions, OR, English spelling for Indic text, close and OCR-misread matches, BM25 ranking), a new first-run introduction, tablet/foldable/landscape layouts, a slim OpenCV build, and opting out of Google Drive auto-backup. Room schema 12. See [CHANGELOG.md](CHANGELOG.md) for full details.

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
