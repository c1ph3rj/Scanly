# Implementation Snapshot

One-page technical summary of Scanly **v1.0.10** (+ unreleased editor tools on this branch). For detail see the full docs index at [../README.md](../README.md).

## Release

| Field | Value |
| --- | --- |
| Version | `1.0.10` (code `10`) |
| Room schema | `4` |
| Min SDK | 29 |
| Target SDK | 36 |
| Module | `:app` only |
| Distribution | `githubRelease` (APK) + `playStoreRelease` (AAB) |

## Architecture

```
ScanlyApplication (WorkManager + Hilt)
  → MainActivity → onboarding gate → ScanlyNavHost (+ widget/shortcut launch actions)
  feature/ (UI + ViewModels)
    → domain/usecase/ (73 use case classes)
      → domain/repository/ (interfaces)
        → data/ (implementations)
          → core/ (ML, OpenCV, utils)
```

## Features (summary)

Home · Library · Tools (scan/import, QR, PDF toolkit) · Camera scan + gate + multi-model overlay · Gallery import · Document detail · Page preview · **Page editor** (live cropped preview; full-screen Filters + Adjust; dedicated Crop with AI Detect) · Groups · PDF/ZIP export with save destination · Library backup/restore (`.scanly`) · Document detection settings + model benchmark · Pure black theme · Onboarding · **Home widgets + launcher quick actions** · GitHub/Play update channels

## Data

- **Room v4:** `documents`, `scan_pages` (+ filter adjustment columns), `document_groups`
- **Files:** `raw/` (immutable), `processed/`, `thumbs/` per document
- **DataStore:** theme, pure black, onboarding, export destination, live/post models, automatic selection, document gate
- **Export cache:** `cache/exports/`
- **Backup workspace:** `cache/library-archive/`, `files/library-archive-journal/`
- **User-visible:** `Downloads/Scanly/` exports + `Downloads/Scanly/backup/` archives (or custom SAF tree)

## Processing

```
Raw JPEG
  → EXIF + user rotation
  → optional semantic gate (capture/import)
  → LiteRT corners (post model; book resolve) OR stored/manual/AI-detect quad
  → perspective warp
  → OpenCV filter preset
  → optional brightness/contrast/saturation/sharpness adjustments
  → processed JPEG (q94, max 2400px) + thumbnail
```

Corner models: Lite (224) · Standard (288) · High (384) · Accurate (YOLO-pose). Gate: MobileNetV3-Small 160 px float16.

## Editor surfaces

| Surface | Route / host | Notes |
| --- | --- | --- |
| Page editor | `editor/page/{pageId}` | Live result preview; retake/delete |
| Filter picker | Editor overlay | Live preview + presets |
| Filter adjust | Editor overlay | Sliders + Compare + scrollbar |
| Crop | `crop/page/{pageId}` | AI Detect, rotate, handles, Reset |

## Background work

`LibraryArchiveWorker` — unique foreground WorkManager job (`scanly-library-archive`) for backup/restore with `dataSync` service type.

## Stack

Kotlin · Compose · Material 3 · Hilt · Navigation Compose · CameraX · Room · DataStore · WorkManager · LiteRT · OpenCV · PdfBox-Android · Play App Update · Coroutines/Flow

## Tests

41 unit-test files · 3 instrumented-test files (onboarding UI, OpenCV filter processor, smoke) · gaps in persistence integration and archive/export E2E

## Principles

Offline-first · preserve raw captures · derived processing · manual fallback · layer boundaries
