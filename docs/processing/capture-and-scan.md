# Capture and Scan

How Scanly captures document pages from the camera.

## Overview

The scan session (`ScanSessionScreen` + `ScanSessionViewModel`) uses **CameraX** for preview and capture. After each capture, the page flows through the finalize pipeline described in [image-processing.md](image-processing.md).

Live detection is more than a single corner model: a **physical-document semantic gate**, **configurable corner models**, **quad geometry policy**, **temporal stability**, and optional **book-page isolation** all shape what the overlay draws and when auto-capture may fire.

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

Triggered from page editor **Retake** button (and page preview overflow).

## Live guidance pipeline

Preview frames are analyzed on a background path roughly as:

```
Camera frame
  → optional DocumentGateDetector (physical vs screen/neither)
  → if accepted (or gate disabled): DocumentCornerDetector(live model)
  → DocumentQuadPolicy (convex, area/aspect, edge margin)
  → optional High verification for ambiguous quads
  → BookAwareCornerResolver / BookPageQuadAnalyzer (gutter trim or reject)
  → StableCornerSelector (rank, confirm, smooth outline)
  → CaptureStabilityTracker (auto-capture phases)
  → overlay draw via CameraOverlayMapper
```

### Physical-document semantic gate

| Component | Role |
| --- | --- |
| `DocumentGateDetector` / `LiteRtDocumentGateDetector` | MobileNetV3-Small float16 classifier (~1.14 MB) |
| Asset | `assets/models/scanly_document_gate_float16.tflite` (160 px) |
| Classes | `physical_document`, `digital_screen`, `neither` |
| Live policy | Accept physical document at **0.90** for **two consecutive** analyzed frames (`DocumentGateStabilityTracker`) |
| Post-process policy | Accept physical document at **0.95** (stricter, no multi-frame) |

Settings → **Physical-document gate** enables or bypasses gate inference for both live and post-processing. When the gate rejects a frame, corner inference is skipped (faster rejection of screens and non-documents). Quad geometry and stability checks remain active even when the gate is off.

### ML corner overlay

`DocumentCornerDetector` (LiteRT) runs on accepted frames with the configured **live preview model**:

| Model | Asset | Notes |
| --- | --- | --- |
| Lite | `document_corners_lite.tflite` | 224 px regression + presence |
| Standard | `document_corners_standard.tflite` | 288 px regression + presence |
| High | `document_corners_accurate.tflite` | 384 px regression + presence |
| Accurate | `document_corners_float16.tflite` | YOLO-pose output; manual compatibility default (formerly Legacy) |

Model choice is independent for live preview vs post-processing (DataStore). **Automatic model selection** calibrates Lite/Standard/High on-device and picks the highest-accuracy model under separate latency budgets (live ≈ 35 ms corner budget, post ≈ 120 ms). Accurate is manual-only for compatibility. Manual selectors lock while automatic selection is enabled.

- `CameraOverlayMapper` maps normalized quads to overlay coordinates
- Temporary model/latency/confidence HUD is **not** shown on the live camera; use **Settings → Model benchmark** for measurements

### Quad policy and stability

| Component | Role |
| --- | --- |
| `DocumentQuadPolicy` | Requires a convex, fully in-frame quad with plausible area/aspect and margin on every edge before draw or auto-capture |
| `CornerCandidatePolicy` | Ranks candidates by confidence + opposite-edge / diagonal / corner-angle balance |
| `StableCornerSelector` | Temporally confirms and smooths the visible outline; resists jumping to nearby monitors/keyboard edges (worst-corner motion as well as average) |
| `CaptureStabilityTracker` | Auto-capture phase machine; also tracks worst-moving corner stability |

Ambiguous live (and post-processing) results may be **conditionally verified with the High model** before acceptance.

### Book-page isolation

| Component | Role |
| --- | --- |
| `BookPageQuadAnalyzer` | Sparse boundary/gutter sampler (few thousand pixels, keeps live latency low) |
| `BookAwareCornerResolver` | Applies book-aware trim or rejection on top of corner results |

- Strong **off-centre** gutter → trim the smaller adjacent-page sliver and keep the dominant page
- **Centred** gutter with two plausible pages → suppress outline / ask user to move closer

### Quality feedback (`CaptureFrameQualityAnalyzer`)

Analyzes preview frames and surfaces hints for:

- Insufficient lighting
- Motion blur
- Lens obstruction
- Poor document framing

### Auto-capture phases (`CaptureStabilityTracker`)

| Phase | Meaning |
| --- | --- |
| `OFF` | Auto-capture disabled |
| `SEARCHING` | Looking for a stable document in frame |
| `HOLD_STEADY` | Document detected; waiting for stillness |
| `COUNTDOWN` | Countdown before shutter |
| `CAPTURING` | Shutter firing |
| `COOLDOWN` | Brief pause before next auto-capture attempt |

Capture fires when gate (if enabled), geometry policy, stability, and quality thresholds all pass.

### Camera controls

- **Torch/flash** toggle
- **Alignment grid** overlay toggle
- **Tap-to-focus** on preview
- **Auto-capture** enable/disable

## Capture finalize

After CameraX writes the raw JPEG:

```
FinalizeCapturedPageUseCase
  → DefaultPageRepository.finalizeCapture()
    → PageImageProcessor.processCapture()
      → optional document gate (post-processing threshold)
      → corner model (post-processing selection)
      → book-aware resolve + quad policy
      → perspective warp + OpenCV filter
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
3. Same processing pipeline as camera capture (including gate + post-processing model).

Available on Home (new document) and Document detail (add pages).

## Orientation

v1.0.9 refined portrait and landscape capture layouts with theme-aligned controls. `AdaptiveLayout` detects form factor; scan session adapts overlay and control placement.

## Related docs

- [image-processing.md](image-processing.md) — ML detection, warp, filters, model assets
- [../data/settings-and-updates.md](../data/settings-and-updates.md) — model and gate preferences
- [../architecture/navigation.md](../architecture/navigation.md) — scan session routes
- [../data/file-storage.md](../data/file-storage.md) — where raw captures are stored
