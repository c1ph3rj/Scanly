# Image Processing

How Scanly transforms raw captures into corrected, filtered document pages.

## Pipeline overview

```
Raw JPEG (camera or import)
  → EXIF rotation correction
  → Optional user rotation
  → Optional physical-document semantic gate (capture/import only)
  → Corner detection (LiteRT post model + book-aware resolve) OR stored/manual crop quad
  → Perspective warp (`PerspectiveBitmapTransform`)
  → Filter preset (`OpenCvPageFilterProcessor`)
  → Optional post-filter adjustments (`PageFilterAdjustmentsApplier`)
  → Processed JPEG + thumbnail
  → Room record update
```

Implemented via `PageImageProcessor` (`domain/processing/`) with implementation in `data/processing/DefaultPageImageProcessor`.

## Entry points

| Method | When |
| --- | --- |
| `processCapture()` | After camera capture or gallery import |
| `reprocessPage()` | After editor / crop saves edits |
| `detectDocumentCorners()` | Crop screen **AI Detect** — returns a quad without writing files |

All methods that read images start from the **raw** capture. `reprocessPage` never modifies the raw file.

## Step 1: Rotation

1. Read EXIF orientation from raw JPEG (`ExifInterface`).
2. Apply EXIF correction.
3. Apply user `rotationDegrees` (90° increments) from crop/editor state.

## Step 2: Semantic gate and corner detection

When no crop quad is supplied (or detect-when-missing is requested):

| Component | Role |
| --- | --- |
| `LiteRtDocumentGateDetector` | Classifies physical document vs digital screen vs neither |
| `LiteRtDocumentCornerDetector` | Runs the selected TFLite corner model |
| `DocumentCornerQuad` | Four normalized corner points |
| `DocumentQuadPolicy` | Accepts only capture-ready convex quads |
| `BookAwareCornerResolver` | Book gutter trim / ambiguous-spread reject |
| `AutomaticDocumentModelSelector` | On-device calibration for automatic model pick |

### Model assets (`assets/models/`)

| Asset | Role |
| --- | --- |
| `document_corners_lite.tflite` | Lite — 224 px corner regression + presence |
| `document_corners_standard.tflite` | Standard — 288 px corner regression + presence |
| `document_corners_accurate.tflite` | High — 384 px corner regression + presence |
| `document_corners_float16.tflite` | Accurate — YOLO-pose corner model (formerly Legacy) |
| `scanly_document_gate_float16.tflite` | 160 px physical-document / screen / neither gate |

Keep `noCompress += "tflite"` in Gradle. NDK ABI filters: `arm64-v8a`, `armeabi-v7a`.

### Model config

- Accurate uses YOLO-pose output; Lite/Standard/High use TL/TR/BR/BL regression plus a presence score, RGB `[-1, 1]` input, and RGB-114 letterboxing
- Live-preview and post-processing model choices are independent DataStore settings
- **Automatic selection** benchmarks Lite, Standard, and High on-device, then chooses under separate latency budgets for live vs post
- Semantic gate: live and post thresholds differ; rejected frames skip corner inference on the camera path
- Book captures use gutter analysis via `BookAwareCornerResolver`

### Crop screen AI Detect

`DetectDocumentCornersUseCase` → `PageImageProcessor.detectDocumentCorners(raw, rotation)`:

1. Decode raw, apply EXIF + current user rotation
2. Run the same still-image detection path as finalize (`BookAwareCornerResolver` + raw-model fallback)
3. Return a normalized `DocumentCornerQuad` into crop UI state (user still taps Done to reprocess)

### Model benchmark (`settings/model-benchmark`)

Settings exposes a temporary benchmark screen for gate + all corner models on selected local images.

## Step 3: Perspective warp

| Component | Role |
| --- | --- |
| `PerspectiveQuadMath` | Output dimensions and point arrays |
| `PerspectiveBitmapTransform` | Shared Android `Matrix.setPolyToPoly` warp (reprocess + live editor preview) |

Maps the detected (or manual) quad to a flat rectangular output image.

## Step 4: Filter presets

`OpenCvPageFilterProcessor` applies the selected `PageFilterPreset`:

| Preset | Storage value | Typical use |
| --- | --- | --- |
| Original | `original` | No filter |
| Auto | `auto` | Adaptive tuning (persists the concrete preset Auto chose) |
| Enhanced Color | `enhanced_color` | Cleans paper while retaining logos and color marks |
| Grayscale | `grayscale` | General text documents |
| Black & White | `black_and_white` | Strong text / invoice output |
| Clean | `clean` | Uneven monochrome paper |
| Shadow Reduction | `shadow_reduction` | Color pages under uneven lighting |
| Magic Color | `magic_color` | Faded print / illustrations |
| Receipt | `receipt` | Thermal receipts and long slips |
| Soft Black & White | `soft_black_and_white` | Faint handwriting / gentler text |

`AdaptivePageFilterTuning` and `PageImageProfile` drive Auto routing.

## Step 5: Post-filter adjustments

After the preset, `PageFilterAdjustmentsApplier` applies per-page tweaks persisted on `ScanPage`:

| Field | Range (normalized) | Effect |
| --- | --- | --- |
| `brightness` | −1…1 | Linear shift |
| `contrast` | −1…1 | Scale around mid-tones |
| `saturation` | −1…1 | HSV saturation scale |
| `sharpness` | 0…1 | Unsharp mask amount |

Identity (all zero) skips extra work. Editor **Adjust** edits these; bulk “apply filter to all pages” changes only the preset, not other pages’ adjustments.

## Step 6: Output

| Output | Settings |
| --- | --- |
| Processed JPEG | Quality 94, max dimension 2400 px |
| Thumbnail | Generated for list display |

Paths under `processed/` and `thumbs/` per document. `ThumbnailCache` is invalidated after reprocess.

## Failure handling

| Condition | Behavior |
| --- | --- |
| No quad detected (capture/import) | Mark `NEEDS_REVIEW`; user fixes on crop screen |
| AI Detect finds nothing | Snackbar; existing handles unchanged |
| Processing exception | Fallback thumbnail from raw when possible; raw preserved |
| Editor reprocess failure | Raw preserved; previous processed path may remain |

## Editor integration

### Page editor (`editor/page/{pageId}`)

- Main surface: **live** cropped + filtered + adjusted preview (same pipeline as reprocess, preview-sized)
- Toolbar: **Crop · Filters · Adjust · Retake · Delete**
- **Filters** → full-screen `FilterPickerScreen` (large live preview + preset chips + apply-to-all)
- **Adjust** → full-screen `FilterCustomizeScreen` (sliders, hold Compare for filter-only, scrollbar)
- Done on the editor saves filter + adjustments (with latest crop/rotation from storage)

### Crop screen (`crop/page/{pageId}`)

- Full-frame preview with four-point handles (`CropQuadEditor`)
- **AI Detect · Left · Right · Reset · Done**
- Done → `UpdatePageEditsUseCase` with crop + rotation; keeps current filter + adjustments

### Shared support

| File | Role |
| --- | --- |
| `EditorImageSupport.kt` | Live preview decode / crop / filter / adjust |
| `EditorScrollbars.kt` | Material vertical scrollbar for control panes |
| `PerspectiveBitmapTransform` | Shared warp for reprocess and previews |

## Core processing files

| File | Package | Role |
| --- | --- | --- |
| `PageImageProcessor` | `domain/processing/` | Interface |
| `DefaultPageImageProcessor` | `data/processing/` | Orchestrates full pipeline |
| `PerspectiveQuadMath` | `core/processing/` | Geometry math |
| `PerspectiveBitmapTransform` | `core/processing/` | Bitmap warp |
| `OpenCvPageFilterProcessor` | `core/processing/` | Filter application |
| `PageFilterAdjustmentsApplier` | `core/processing/` | Brightness/contrast/saturation/sharpness |
| `AdaptivePageFilterTuning` | `core/processing/` | Per-image Auto routing and tuning |
| `LiteRtDocumentCornerDetector` | `core/ml/` | Multi-model corner inference |
| `LiteRtDocumentGateDetector` | `core/ml/` | Physical-document gate |
| `AutomaticDocumentModelSelector` | `core/ml/` | Device latency calibration |
| `DocumentQuadPolicy` / `CornerCandidatePolicy` | `core/ml/` | Geometry acceptance and ranking |
| `BookPageQuadAnalyzer` / `BookAwareCornerResolver` | `core/ml/` | Book page isolation |
| `CropQuadEditor` | `core/editing/` | Interactive crop UI logic |

## Related docs

- [capture-and-scan.md](capture-and-scan.md) — camera session and finalize trigger
- [export.md](export.md) — uses processed images for PDF/ZIP
- [../data/file-storage.md](../data/file-storage.md) — output file layout
- [../architecture/screens.md](../architecture/screens.md) — editor / crop ViewModels
