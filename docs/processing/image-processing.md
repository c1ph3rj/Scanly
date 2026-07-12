# Image Processing

How Scanly transforms raw captures into corrected, filtered document pages.

## Pipeline overview

```
Raw JPEG (camera or import)
  → EXIF rotation correction
  → Optional user rotation
  → Corner detection (LiteRT) OR manual crop quad
  → Perspective warp
  → Filter preset (OpenCV)
  → Processed JPEG + thumbnail
  → Room record update
```

Implemented via `PageImageProcessor` interface (`domain/processing/`) with implementation in `data/processing/`.

## Two entry points

| Method | When |
| --- | --- |
| `processCapture()` | After camera capture or gallery import |
| `reprocessPage()` | After editor saves crop/rotate/filter changes |

Both read from the **raw** file. `reprocessPage` never modifies the raw capture.

## Step 1: Rotation

1. Read EXIF orientation from raw JPEG (`ExifInterface`).
2. Apply EXIF correction.
3. Apply user `rotationDegrees` from editor (90° increments).

## Step 2: Corner detection

When no manual crop quad is set:

| Component | Role |
| --- | --- |
| `LiteRtDocumentCornerDetector` | Runs TFLite model inference |
| `DocumentCornerQuad` | Four normalized corner points |
| Model assets | Legacy, Lite (224), Standard (288), Accurate (384) TFLite variants |

Model config:

- Legacy uses its existing YOLO-pose output; the new models use TL/TR/BR/BL regression plus a presence output
- Live-preview and post-processing model choices are independent, persisted settings; both default to Legacy for safe upgrades
- The new models use RGB `[-1, 1]` input and RGB-114 letterboxing
- Gradle `noCompress += "tflite"` prevents APK compression
- NDK ABI filters: `arm64-v8a`, `armeabi-v7a`

The camera overlay shows the active model, inference latency, and confidence. Settings also exposes a temporary benchmark screen that warms each runtime once, processes selected images sequentially with all four models, and reports preprocessing, inference, postprocessing, total latency, detection status, confidence, averages, P50, and P95.

When user sets manual corners in editor, the stored quad is used instead of ML detection.

## Step 3: Perspective warp

| Component | Role |
| --- | --- |
| `PerspectiveQuadMath` | Computes output dimensions and point arrays |
| Android `Matrix.setPolyToPoly` | Applies perspective transform via Canvas draw |

Maps the detected (or manual) quad to a flat rectangular output image.

## Step 4: Filter presets

`OpenCvPageFilterProcessor` applies the selected `PageFilterPreset`:

| Preset | Storage value | Typical use |
| --- | --- | --- |
| Original | `original` | No filter |
| Auto | `auto` | Adaptive tuning |
| Enhanced Color | `enhanced_color` | Cleans paper while retaining logos, highlights, and other useful color |
| Grayscale | `grayscale` | General text documents with gentle contrast correction |
| Black & White | `black_and_white` | Strong, discrete text and invoice output |
| Clean | `clean` | Uneven monochrome pages that need a cleaner paper background |
| Shadow Reduction | `shadow_reduction` | Color documents captured under uneven or warm lighting |
| Magic Color | `magic_color` | Faded print, illustrations, and photo-like documents |
| Receipt | `receipt` | Thermal receipts and high-contrast slips |
| Soft Black & White | `soft_black_and_white` | Faint handwriting, carbon copies, and gentler text enhancement |

`AdaptivePageFilterTuning` and `PageImageProfile` analyze image characteristics for Auto. Auto follows the same practical split used by leading scanner apps: preserve meaningful color, use a gentle grayscale fallback for ordinary black-and-white documents, route monochrome long slips to Receipt, and avoid text-focused processing when a page has very little detail.

The OpenCV pipeline corrects broad illumination before applying restrained local contrast. It does not paste the original dark pixels back over the corrected page, which prevents shadow halos and mottled paper. Color modes additionally estimate likely paper pixels in Lab space to reduce warm/cool casts without desaturating colored content.

## Step 5: Output

| Output | Settings |
| --- | --- |
| Processed JPEG | Quality 94, max dimension 2400 px |
| Thumbnail | Generated for list display |

Paths written to `processed/` and `thumbs/` under the document directory.

## Failure handling

| Condition | Behavior |
| --- | --- |
| No quad detected | Mark `NEEDS_REVIEW`; user must set manual crop in editor |
| Capture in progress | `CAPTURED` state before processing completes |
| Processing exception | Fallback thumbnail from raw; page still saved |
| Editor reprocess failure | Raw preserved; previous processed path may remain |

## Editor integration

`PageEditorScreen` uses `CropQuadEditor` (`core/editing/`) for interactive four-point crop:

- Drag handles at each corner
- Constrained quad geometry
- On save: `UpdatePageEditsUseCase` → `reprocessPage`

`ThumbnailCache` is invalidated after reprocess.

## Core processing files

| File | Package | Role |
| --- | --- | --- |
| `PageImageProcessor` | `domain/processing/` | Interface |
| Implementation | `data/processing/` | Orchestrates full pipeline |
| `PerspectiveQuadMath` | `core/processing/` | Geometry math |
| `OpenCvPageFilterProcessor` | `core/processing/` | Filter application |
| `AdaptivePageFilterTuning` | `core/processing/` | Per-image tuning |
| `LiteRtDocumentCornerDetector` | `core/ml/` | ML inference |
| `CropQuadEditor` | `core/editing/` | Interactive crop UI logic |

## Related docs

- [capture-and-scan.md](capture-and-scan.md) — camera session and finalize trigger
- [export.md](export.md) — uses processed images for PDF/ZIP
- [../data/file-storage.md](../data/file-storage.md) — output file layout
