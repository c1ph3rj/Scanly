# Implementation Snapshot

One-page technical summary of Scanly **v1.0.18**. For detail see the full docs index at [../README.md](../README.md).

## Release

| Field | Value |
| --- | --- |
| Version | `1.0.18` (code `18`) |
| Room schema | `7` (`MIGRATION_5_6` page text; `MIGRATION_6_7` signatures and markups) |
| Release date | `2026-10-20` |
| Min SDK | 29 |
| Target SDK | 36 |
| Module | `:app` only |
| Distribution | `githubRelease` (APK) + `playStoreRelease` (AAB) |

## Architecture

```
ScanlyApplication (WorkManager + Hilt)
  → MainActivity → onboarding gate → ScanlyNavHost (+ widget/shortcut launch actions, Open in Scanly for PDFs)
  feature/ (UI + ViewModels)
    → domain/usecase/
      → domain/repository/ (interfaces)
        → data/ (implementations)
          → core/ (ML, OpenCV, utils)
```

## Features (summary)

Home (shimmer loading) · Pip helper with camera coaching and daily tips · Library and folder management · on-device OCR, extracted-text views, and full-text search · Tools (QR, PDF toolkit incl. PDF to images; reader hands its file to any tool) · Open PDFs from other apps · Sign PDFs and scanned pages, with reusable signatures · Camera scan + document gate + multi-model detection · Gallery import · Page preview/editor/crop/filter/adjust · Groups · PDF/ZIP export with save destination · Storage cleanup (library backup/restore retired) · Document detection settings + model benchmark · Pure black theme · Onboarding · Home widgets + launcher quick actions · GitHub/Play update channels

## Data

- **Room v7:** documents, pages, groups, page text + FTS4, saved signatures, and editable page markups
- **Files:** `raw/` (immutable), `processed/`, optional clean `.base.jpg`, page-owned `markups/`, and `thumbs/` per document; reusable ink under `files/signatures/`
- **DataStore:** theme, pure black, onboarding, export destination, live/post models, automatic selection, document gate, Pip preferences (`pip_*`)
- **Export cache:** `cache/exports/`; PDFs opened from other apps are copied to `cache/incoming_pdf/` (newest few kept)
- **Legacy cleanup:** leftover archive workspace files under `cache/library-archive/` and `files/library-archive-journal/` are cleaned after backup/restore removal
- **User-visible:** `Downloads/Scanly/` exports (or a custom SAF tree); library backup/restore is no longer available

## Processing

```
Raw JPEG
  → EXIF + user rotation
  → optional semantic gate (capture/import)
  → LiteRT corners (post model; book resolve) OR stored/manual/AI-detect quad
  → perspective warp
  → decode, capped at 2800px longest edge
  → OpenCV filter engine (analyze → paper-aware background at 360px → per-channel division → tone curve → vibrance / unsharp, or B&W threshold; strength via intensity)
  → optional brightness/contrast/saturation/sharpness adjustments
  → processed JPEG (q94, max 2800px) + thumbnail
```

Corner models: Lite (224) · Standard (288) · High (384) · Accurate (YOLO-pose). Gate: MobileNetV3-Small 160 px float16.

Page-text recognition uses bundled ML Kit Text Recognition v2 on-device; `TextIndexWorker` fills the FTS4 index in the background.

## Editor surfaces

| Surface | Route / host | Notes |
| --- | --- | --- |
| Page editor | `editor/page/{pageId}` | Live result preview; retake/delete |
| Filter picker | Editor overlay | Live preview + presets |
| Filter adjust | Editor overlay | Sliders + Compare + scrollbar |
| Crop | `crop/page/{pageId}` | AI Detect, rotate, handles, Reset |
| Extracted text | `document/{documentId}/text` | Text / On page views, corrections, copy and share |
| Sign scan | `document/{documentId}/sign` | Editable marks composited onto page images |
| Sign PDF | `tools/pdf/sign?source={source}` | Sign a device or library PDF and save/share a new file |

## Background work

`TextIndexWorker` — WorkManager job that indexes pages with pending or stale OCR text; it requires battery-not-low and uses bundled on-device recognition.

## Stack

Kotlin · Compose · Material 3 · Hilt · Navigation Compose · CameraX · Room · DataStore · WorkManager · ML Kit Text Recognition v2 · LiteRT · OpenCV · PdfBox-Android · Play App Update · Coroutines/Flow

## Tests

83 JVM test source files (including in-memory fakes) · 6 instrumented-test source files · new unit coverage for signature ink, mark geometry/codec, and editor state · remaining gaps: Room round-trips, real PDF/ZIP I/O, live camera

## Principles

Offline-first · preserve raw captures · derived processing · manual fallback · layer boundaries
