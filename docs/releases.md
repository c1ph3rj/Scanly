# Releases

Version policy and release history for Scanly.

## Current release

| Field | Value |
| --- | --- |
| Version name | `1.0.10` |
| Version code | `10` |
| Application ID | `in.c1ph3rj.scanly` |
| Room schema | `4` (this branch; 1.0.10 tag shipped at schema 3) |
| Min SDK | 29 (Android 10) |
| Target / compile SDK | 36 |
| Release date | 2026-07-13 |

Canonical source: `versionCode` and `versionName` in `app/build.gradle.kts`.

The version shown in Settings reads `versionName` via `DefaultSettingsRepository`.

### Release highlights

Version **1.0.10** (relative to `1.0.9` / historical `master` baseline):

- Tools tab with scan/import shortcuts, QR tool, and offline PDF toolkit
- Multi-model corner detection (Lite / Standard / High / Accurate) + semantic document gate
- Automatic model selection (default on), model benchmark, book-aware capture, stable quads
- Pure black OLED theme option and rebuilt document filters
- Large-screen / landscape layout polish for Tools and PDF pickers

**Unreleased on `feature/v1.0.10`** (see [CHANGELOG.md](../CHANGELOG.md)):

- Dedicated crop screen with AI Detect, full-screen Filters + Adjust, filter fine-tuning (Room **4**)
- Live cropped editor preview and large-screen editor tool layouts

The compact GitHub release description used by the in-app update dialog is available in [release-notes/v1.0.10.md](release-notes/v1.0.10.md).

## Versioning policy

- **Version name** — semantic-style `MAJOR.MINOR.PATCH` for user-facing releases.
- **Version code** — monotonically increasing integer required by Play Store.
- **Distribution build types** — `githubRelease` and `playStoreRelease` share the same version and application ID; only their update provider differs.
- Bump both together on every public release.
- Record user-visible changes in [CHANGELOG.md](../CHANGELOG.md).
- Keep [VERSION.md](../VERSION.md) aligned with Gradle values.

## Recent releases

### 1.0.10 (code 10) — 2026-07-13

- Tools hub, QR code tool, and offline PDF toolkit (reader / merge / compress / password / watermark)
- Multi-model detection ladder, semantic gate, automatic selection default on, model benchmark
- Book-page isolation, stable overlays, rebuilt filters, pure black theme
- Tablet and landscape layout improvements for Tools and PDF flows
- Follow-on editor work on this branch: crop/filter full-screen tools, AI Detect, filter adjustments (Room 4) — tracked under **Unreleased**

### 1.0.9 (code 9) — 2026-07-05

- Configurable export destinations and `.scanly` library backup/restore
- Advanced PDF export controls and ten enhancement filters
- Suggested duplicate-safe document and folder names
- Improved page preview, onboarding, capture layouts, and library filters
- Separate GitHub and Google Play update channels
- Refined portrait/landscape capture layouts
- Improved capture feedback (lighting, blur, obstruction, framing)
- Retake returns directly to editor after capture
- Renaming no longer replaces first-page thumbnail

### 1.0.8.betaq (code 8) — 2026-06-27

- Removed `REQUEST_INSTALL_PACKAGES`; update opens GitHub release page (superseded by Google Play in-app updates on `playStoreRelease` builds)

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

### Room schema 3 → 4 (unreleased editor work on this branch)

- Adds per-page filter adjustment columns: `filterBrightness`, `filterContrast`, `filterSaturation`, `filterSharpness` (defaults `0`).
- Applied by `ScanlyDatabase.MIGRATION_3_4` registered in `DatabaseModule`.
- No manual steps; existing pages keep default (identity) adjustments.
- `.scanly` backups written after this change include the four floats; older archives restore with defaults.

### From 1.0.9 to 1.0.10

- Model labels are Lite / Standard / High / Accurate; storage keys keep the same underlying weights.
- Automatic model selection defaults to **on** when unset; existing explicit off preference is preserved.
- The public 1.0.10 tag was still Room schema **3**; this branch advances to schema **4** for filter adjustments.

### From 1.0.0 to current

- Room migrates automatically along `1→2→3→4` (filter preset, document groups, then filter adjustments).
- Existing documents remain; they appear ungrouped until moved into a collection.
- **Clear all data** (added in 1.0.4) is destructive and cannot be undone.
- Completed external `.scanly` backups survive clear-all-data.
- No manual migration steps required.

See [VERSION.md](../VERSION.md) for extended upgrade guidance.

## Where version is defined

| Location | Purpose |
| --- | --- |
| `app/build.gradle.kts` | Canonical `versionCode` and `versionName` |
| `DefaultSettingsRepository` | Package version for Settings screen |
| `VERSION.md` / `docs/releases.md` | Human-readable metadata |
| `docs/release-notes/vX.Y.Z.md` | Compact notes for GitHub / in-app update dialog |
| `CHANGELOG.md` | Full user-facing release notes |
