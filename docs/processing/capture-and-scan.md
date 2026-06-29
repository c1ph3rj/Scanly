# Capture and Scan

How Scanly captures document pages from the camera.

## Overview

The scan session (`ScanSessionScreen` + `ScanSessionViewModel`) uses **CameraX** for preview and capture. After each capture, the page flows through the finalize pipeline described in [image-processing.md](image-processing.md).

## Camera stack

| Dependency | Role |
| --- | --- |
| `camera-camera2` | Camera2 interop |
| `camera-lifecycle` | Lifecycle-aware binding |
| `camera-view` | `PreviewView` for Compose integration |

Camera permission (`CAMERA`) is required. Hardware camera is optional at manifest level but needed for scanning.

## Scan session modes

### Add pages (default)

Route: `camera/session/{documentId}`

1. `PreparePageCaptureUseCase` allocates next raw file path and draft record.
2. User captures one or more pages.
3. Each capture: CameraX writes JPEG → `FinalizeCapturedPageUseCase`.
4. On session complete → navigate to `document/{documentId}`.

### Replace page (retake)

Route: `camera/session/{documentId}?replacePageId={pageId}`

1. `PrepareReplacementCaptureUseCase` allocates raw path for the existing page slot.
2. Single capture replaces the page content.
3. On complete → navigate to `editor/page/{pageId}` (v1.0.9 behavior).

Triggered from page editor **Retake** button.

## Live guidance

### ML corner overlay

`DocumentCornerDetector` (LiteRT) runs on preview frames:

- Detects four document corners
- `CameraOverlayMapper` maps normalized quad to overlay coordinates
- Drawn on camera preview to guide framing

### Quality feedback (`CaptureFrameQualityAnalyzer`)

Analyzes preview frames and surfaces hints for:

- Insufficient lighting
- Motion blur
- Lens obstruction
- Poor document framing

### Stability tracking (`CaptureStabilityTracker`)

Monitors frame stability to gate **auto-capture** — capture fires when the document is steady and quality thresholds pass.

## Capture finalize

After CameraX writes the raw JPEG:

```
FinalizeCapturedPageUseCase
  → DefaultPageRepository.finalizeCapture()
    → PageImageProcessor.processCapture()
      → (see image-processing.md)
    → ScanPageDao.insert/update
    → DocumentDao.update snapshot (pageCount, coverThumbnailPath)
```

On processing failure:

- Fallback thumbnail generated from raw image
- Page marked `NEEDS_REVIEW` if no crop quad available
- Raw capture is still preserved

## Gallery import (same finalize path)

`ImportImagesUseCase`:

1. User picks ≤10 images via photo picker (`ImageImportSupport`).
2. For each image: `prepareCapture` → copy URI to raw path → `finalizeCapture`.
3. Same processing pipeline as camera capture.

Available on Home (new document) and Document detail (add pages).

## Orientation

v1.0.9 refined portrait and landscape capture layouts with theme-aligned controls. `AdaptiveLayout` detects form factor; scan session adapts overlay and control placement.

## Related docs

- [image-processing.md](image-processing.md) — ML detection, warp, filters
- [../architecture/navigation.md](../architecture/navigation.md) — scan session routes
- [../data/file-storage.md](../data/file-storage.md) — where raw captures are stored