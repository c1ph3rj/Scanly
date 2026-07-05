# Implementation Snapshot

One-page technical summary of Scanly **v1.0.9**. For detail see the full docs index at [../README.md](../README.md).

## Release

| Field | Value |
| --- | --- |
| Version | `1.0.9` (code `9`) |
| Room schema | `3` |
| Min SDK | 29 |
| Target SDK | 36 |
| Module | `:app` only |
| Distribution | `githubRelease` (APK) + `playStoreRelease` (AAB) |

## Architecture

```
ScanlyApplication (WorkManager + Hilt)
  → MainActivity → onboarding gate → ScanlyNavHost
  feature/ (UI + ViewModels)
    → domain/usecase/ (51 classes)
      → domain/repository/ (interfaces)
        → data/ (implementations)
          → core/ (ML, OpenCV, utils)
```

## Features (summary)

Home · Library (filter pills) · Camera scan + auto-capture · Gallery import · Document detail · Page preview · Page editor · Groups · Advanced PDF/ZIP export with direct save · Configurable export destination · Library backup/restore (`.scanly`) · Suggested document/folder names · Settings sub-screens · Onboarding · GitHub/Play update channels

## Data

- **Room v3:** `documents`, `scan_pages`, `document_groups`
- **Files:** `raw/` (immutable), `processed/`, `thumbs/` per document
- **DataStore:** theme, onboarding, export destination (`export_tree_uri`, `export_tree_label`)
- **Export cache:** `cache/exports/`
- **Backup workspace:** `cache/library-archive/`, `files/library-archive-journal/`
- **User-visible:** `Downloads/Scanly/` exports + `Downloads/Scanly/backup/` archives (or custom SAF tree)

## Processing

Raw JPEG → EXIF rotation → LiteRT corners → perspective warp → OpenCV filter → processed JPEG (q94, max 2400px) + thumbnail

## Background work

`LibraryArchiveWorker` — unique foreground WorkManager job (`scanly-library-archive`) for backup/restore with `dataSync` service type.

## Stack

Kotlin · Compose · Material 3 · Hilt · Navigation Compose · CameraX · Room · DataStore · WorkManager · LiteRT · OpenCV · PdfBox-Android · Play App Update · Coroutines/Flow

## Tests

31 unit-test files · 2 instrumented-test files · gaps in persistence integration and archive/export E2E

## Principles

Offline-first · preserve raw captures · derived processing · manual fallback · layer boundaries