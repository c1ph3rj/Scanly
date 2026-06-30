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

## Architecture

```
MainActivity → onboarding gate → ScanlyNavHost
  feature/ (UI + ViewModels)
    → domain/usecase/
      → domain/repository/ (interfaces)
        → data/ (implementations)
          → core/ (ML, OpenCV, utils)
```

## Features (summary)

Home · Library · Camera scan · Gallery import · Document detail · Page preview · Page editor · Groups · advanced PDF/ZIP export · Settings · Onboarding · GitHub/Google Play update variants

## Data

- **Room v3:** `documents`, `scan_pages`, `document_groups`
- **Files:** `raw/` (immutable), `processed/`, `thumbs/` per document
- **DataStore:** theme, onboarding, update cooldown
- **Export cache:** `cache/exports/`

## Processing

Raw JPEG → EXIF rotation → LiteRT corners → perspective warp → OpenCV filter → processed JPEG + thumbnail

## Stack

Kotlin · Compose · Material 3 · Hilt · Navigation Compose · CameraX · Room · DataStore · LiteRT · OpenCV · Coroutines/Flow

## Tests

24 unit-test files · 2 instrumented-test files · gaps in persistence integration and export E2E

## Principles

Offline-first · preserve raw captures · derived processing · manual fallback · layer boundaries
