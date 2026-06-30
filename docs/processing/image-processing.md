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
| Model asset | `assets/models/document_corners_float16.tflite` |

Model config:

- Float16 TFLite interpreter via LiteRT
- Gradle `noCompress += "tflite"` prevents APK compression
- NDK ABI filters: `arm64-v8a`, `armeabi-v7a`

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
| Enhanced Color | `enhanced_color` | Vivid documents |
| Grayscale | `grayscale` | Text documents |
| Black & White | `black_and_white` | High contrast |
| Clean | `clean` | Noise reduction |
| Shadow Reduction | `shadow_reduction` | Uneven lighting |
| Magic Color | `magic_color` | Faded print |
| Receipt | `receipt` | Thermal receipts |
| Soft Black & White | `soft_black_and_white` | Gentler B&W |

`AdaptivePageFilterTuning` and `PageImageProfile` analyze image characteristics for auto-tuning.

## Step 5: Output

| Output | Settings |
| --- | --- |
| Processed JPEG | Quality 94, max dimension 2400 px |
| Thumbnail | Generated for list display |

Paths written to `processed/` and `thumbs/` under the document directory.

## Failure handling

| Condition | Behavior |
| --- | --- |
| No quad detected | Mark `NEEDS_REVIEW`; user must set manual crop |
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