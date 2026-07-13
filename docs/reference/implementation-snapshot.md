# Implementation Snapshot

One-page technical summary of Scanly **v1.0.9** (including unreleased work on `feature/scanly-model`). For detail see the full docs index at [../README.md](../README.md).

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
    → domain/usecase/ (61 use case classes)
      → domain/repository/ (interfaces)
        → data/ (implementations)
          → core/ (ML, OpenCV, utils)
```

## Features (summary)

Home · Library (filter pills) · Camera scan + gate + multi-model overlay + auto-capture · Gallery import · Document detail · Page preview · Page editor · Groups · Advanced PDF/ZIP export with direct save · Configurable export destination · Library backup/restore (`.scanly`) · Suggested document/folder names · Look & feel (theme + pure black) · Document detection settings + model benchmark · Onboarding · GitHub/Play update channels

## Data

- **Room v3:** `documents`, `scan_pages`, `document_groups`
- **Files:** `raw/` (immutable), `processed/`, `thumbs/` per document
- **DataStore:** theme, pure black, onboarding, export destination, live/post models, automatic selection, document gate
- **Export cache:** `cache/exports/`
- **Backup workspace:** `cache/library-archive/`, `files/library-archive-journal/`
- **User-visible:** `Downloads/Scanly/` exports + `Downloads/Scanly/backup/` archives (or custom SAF tree)

## Processing

```
Raw JPEG
  → EXIF rotation
  → optional semantic gate
  → LiteRT corners (selected post model; optional Accurate verify + book resolve)
  → perspective warp
  → OpenCV filter
  → processed JPEG (q94, max 2400px) + thumbnail
```

Corner models: Legacy · Lite (224) · Standard (288) · Accurate (384). Gate: MobileNetV3-Small 160 px float16.

## Background work

`LibraryArchiveWorker` — unique foreground WorkManager job (`scanly-library-archive`) for backup/restore with `dataSync` service type.

## Stack

Kotlin · Compose · Material 3 · Hilt · Navigation Compose · CameraX · Room · DataStore · WorkManager · LiteRT · OpenCV · PdfBox-Android · Play App Update · Coroutines/Flow

## Tests

39 unit-test files · 3 instrumented-test files (onboarding UI, OpenCV filter processor, smoke) · gaps in persistence integration and archive/export E2E

## Principles

Offline-first · preserve raw captures · derived processing · manual fallback · layer boundaries
