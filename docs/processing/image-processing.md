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
  → Decode/downscale to max 2800 px longest edge (processing path)
  → Filter preset + intensity (`OpenCvPageFilterProcessor` → `PageFilterEngine`)
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

## Step 4: Filter engine

`OpenCvPageFilterProcessor` is the public facade. Internals live in `core/processing/filter/`. The paper presets render with one reflectance model, and Screen and Vivid use global color looks instead (see [Global color looks](#global-color-looks-screen-vivid)):

```
Image ≈ Reflectance × Shading
```

Shading (uneven light across the paper) is estimated and divided out, so paper goes to white while ink and colour keep their relative values. The pipeline:

```
Cropped page
  → Analyze at canonical 720 px (PageImageAnalyzer → PageImageProfile, incl. tone stats)
  → Auto routing (AdaptivePageFilterTuning.automatic) when the preset is Auto
  → Look parameters (PageFilterLook.resolve, scaled by intensity)
  → Paper-aware background model at 360 px (PageBackgroundModel)
  → Divide shading out (per RGB channel for colour looks; gray for Grayscale, Faded Text and B&W)
  → One tone curve: black point / white point / gamma (PageFilterOperators.toneCurve)
  → Colour looks: vibrance + paper neutralization
  → Unsharp mask with noise damping   |   B&W: anti-aliased global threshold + despeckle
```

Screen and Vivid skip the background model and division entirely; they go straight to their global pipelines.

### Paper-aware background model

`PageBackgroundModel` estimates the shading the page was lit with:

1. **Fixed working size.** The page is reduced to a 360 px longest edge before estimation, so the same page gets the same shading at chip, preview and save sizes.
2. **Stroke removal.** A morphological close (ellipse) removes ink strokes up to `strokeFraction` of the edge (3 % for most looks, 4.5 % for Whiteboard), followed by a small median blur.
3. **Paper mask.** A pixel counts as paper only if it is bright relative to the page's paper level, close in colour to the paper reference (low chromaticity distance), and smooth (low local texture). Photos, logos and solid fills usually fail one of these tests.
4. **Multi-scale fill.** Background under non-paper pixels is filled from surrounding paper with normalized convolution at four scales (3 %, 8 %, 20 % and 50 % of the edge). Where no paper is nearby it falls back to the paper reference. Photos and logos keep their own content instead of being flattened to white.

The paper reference is the median RGB of the brightest quarter of the page.

### Division and gain floor

Each channel is normalized as `out = src × target / background`. The background is floored at `reference / maxGain` (for example `maxGain` 4.0 for Magic Color) so very dark regions are not amplified without limit.

Where the local gain is above about 1.6× (deep shadow), the source is blended toward a smoothed copy before division. This suppresses the sensor noise that would otherwise be amplified in shadows. The smoothing strength is `denoiseSigma`, which scales with the page size.

### Render modes

| Mode | Presets | Division | After division |
| --- | --- | --- | --- |
| Color | Magic Color, Whiteboard | Per RGB channel; paper → 250 | Tone curve → vibrance + paper neutralization → unsharp mask |
| Natural | Natural | Per RGB channel; paper lifted toward ~238 (never darkened) with part of the cast removed | Tone curve → unsharp mask |
| Screen | Screen | None (global); see [Global color looks](#global-color-looks-screen-vivid) | De-screen and veil removal, then levels → S-curve → vibrance → sharpen |
| Vivid | Vivid | None (global); see [Global color looks](#global-color-looks-screen-vivid) | Levels → CLAHE on lightness → S-curve → vibrance → sharpen |
| Gray | Grayscale, Faded Text | Gray; paper → 250 | Tone curve → unsharp mask |
| Binary | B&W | Gray; paper → 250 | Anti-aliased global threshold → despeckle |

### Tone curve

A single LUT is built from three values: **black point**, **white point** and **gamma**. Input is mapped to `((v − black) / (white − black))^gamma`, clipped to 0…255. Gamma above 1 darkens mid-tones (ink) while paper stays clipped to white.

The points come from **tone stats measured on the normalized page at 720 px** (`PageImageAnalyzer.measureTone`):

| Stat | Measured as | Used for |
| --- | --- | --- |
| Paper level | Histogram peak in the bright range (≥140) | Reference for the white point |
| White point | 12th percentile of paper pixels near the paper peak (clamped 170…250) | Clipping of paper to white |
| Ink level | 0.8th percentile of all pixels (clamped 0…230) | Black point, faint-ink detection |

Because the stats are measured on the normalized page, the curve lands on what the ink and paper will actually look like after shading is removed.

### Intensity

Each page stores an `intensity` value from 0 to 1.5 (UI 0–150 %, default 1):

| Intensity | Effect on colour and grayscale looks |
| --- | --- |
| 0 | Lighting correction only: shading is divided out; the tone curve is an identity mapping, with no vibrance or sharpening |
| 1 | Tuned default |
| 1.5 | Strongest: deeper ink, more paper lift, more vibrance and sharpening |

Intensity scales the tonal parameters (black point, white point, gamma, vibrance, sharpening). For B&W, it moves the threshold instead: a higher intensity raises the threshold, so more faint marks are kept as ink. B&W still binarizes at intensity 0. **Original** ignores intensity. **Screen** and **Vivid** scale differently; see [Global color looks](#global-color-looks-screen-vivid).

### Resolution independence

Tonal parameters do not depend on the output size. They come from the 360 px background model and the 720 px analysis profile, so picker chips (320 px), the live editor preview (~1600 px) and the saved JPEG (up to 2800 px) produce the same look. Only pixel-level kernels scale with the longest edge, against a **1600 px reference edge**: sharpening sigma, shadow-denoise sigma and the despeckle area.

### Global color looks (Screen, Vivid)

The paper presets assume a sheet of paper under uneven light: they find the paper, divide out the shading and push it to white. That model does not fit some captures, so Screen and Vivid skip it and correct the whole image instead (`PageColorLooks`):

- **Emissive light.** A display is its own light source, not paper under a lamp.
- **Dark mode.** On a dark page, the brightest-quarter paper reference is content, so paper detection would flatten the page.
- **Full-screen images.** A photo that fills the screen has no paper to find.
- **Pixel grid and moiré.** The display's pixel lattice beats against the camera sensor and shows as fine stripes and colour bands.
- **Reflection veil.** Room light reflected off the glass adds an uneven, often tinted haze.
- **Screen white point.** A display's white is rarely neutral. It casts its own colour, which camera white balance does not remove.

Both looks measure their statistics on a 360 px copy of the page, the same working size as the background model, so chip, preview and saved JPEG agree. Pixel kernels (blur, sharpen) scale with the render size against the 1600 px reference edge, as they do for paper looks.

**Screen pipeline**

1. **De-screen.** The page is converted to YCrCb. Luma gets a Gaussian blur just wide enough to dissolve the pixel grid (sigma about 0.8 px at a 1600 px render). Chroma is resampled to a quarter resolution, blurred (sigma 2 px there), and resampled back, which removes colour moiré. Text edges ride on luma, so they stay sharp.
2. **Dark-mode veil removal.** Runs only when the page's median luminance is below 100. Each channel's local minimum over about 8 % of the long edge (erode, then blur at 5 % of the edge) estimates the veil. A floor at 35 % of the veil's 5th-percentile luminance is removed from it, and the result is subtracted from the page at full resolution, scaled by intensity. Light-background screens skip this step, because their local minimum is content.
3. **White reference.** Each channel's white is the 80th percentile of the brightest 1 % of low-saturation pixels (saturation below 25 %). If fewer than 30 such pixels exist, the brightest 1 % of all pixels is used. The dark level is the 0.5th luminance percentile.
4. **Levels.** Per channel, the dark level (scaled by black clip) maps to black and the white reference maps to 248. White-balance gains are limited to ±15 % so colourful content cannot be pushed into a false cast.
5. **S-curve.** Mid-tone contrast with 0 and 255 fixed.
6. **Vibrance.** Without paper neutralization, so coloured content keeps its colour.
7. **Sharpen.** Unsharp mask.

**Vivid pipeline**

1. **Bounded white balance and levels.** The same white reference, ±15 % gains and levels as Screen. White balance is 60 % of full at intensity 100 %, and black clip is 80 % × intensity, capped at 100 %.
2. **Local contrast.** CLAHE on Lab lightness with a fixed 8×8 grid (so the look does not depend on render size) and a clip limit of 2. The result is blended with the original lightness at 45 % × intensity, capped at 65 %.
3. **S-curve**, then **vibrance** (without paper neutralization), then **sharpen**. Sharpening is damped on noisy captures.

**Intensity**

- **Screen.** De-screening stays on even at intensity 0, because the moiré grid is never wanted. Veil removal, white balance and black clip scale with intensity and stop rising at 100 %. The S-curve, vibrance and sharpening keep scaling up to 150 %. At 0, only de-screening and a plain brightness stretch remain.
- **Vivid.** At intensity 0, only the brightness stretch remains; local contrast, S-curve, vibrance and sharpening are all off. White balance and black clip stop rising at 100 %.

### Presets

| Preset | Storage value | Render | Typical use |
| --- | --- | --- | --- |
| Original | `original` | No processing | Keep the captured page as-is |
| Auto | `auto` | Routes to Screen, Magic Color, Natural, Faded Text or Grayscale; the concrete choice is persisted | Mixed content, when you do not want to pick |
| Magic Color | `magic_color` | Color; paper whitened, colour ink kept vivid | Forms with colour, stamps, highlighter, illustrations |
| Natural | `natural_color` | Color; lighting evened, paper kept near its own tone, light white balance | Photos and logos, cream paper |
| Vivid | `vivid_color` | Global color; bounded white balance, local contrast, vibrance; no paper model | Posters, covers, photos |
| Screen | `screen` | Global color; de-screened, glare haze and screen color cast removed, dark mode kept | Photos of monitors, laptops and phones |
| Grayscale | `grayscale` | Gray; clean paper, darker text | Plain text documents |
| B&W | `black_and_white` | Two-tone threshold; specks removed | Invoices and strong text output |
| Whiteboard | `whiteboard` | Color; stronger whitening and vibrance | Photos of whiteboards and marker |
| Faded Text | `faded` | Gray; faint ink pulled toward black | Pencil, thermal receipts, faded print |

### Auto routing

`AdaptivePageFilterTuning.automatic` uses the page profile. Checks run in this order:

| Page content | Resolves to |
| --- | --- |
| Photo of a display (`screenLikely` hint set by the capture pipeline) | Screen |
| Mostly picture (high colour ratio, little text) | Natural |
| Receipt-like (long narrow page, text, almost no colour) or faint ink | Faded Text |
| Useful colour (enough chromatic area) | Magic Color |
| Text-like (text or edges present) | Grayscale |
| Anything else, or no profile | Magic Color |

The Screen check reads `PageImageProfile.screenLikely`. `DefaultPageImageProcessor` sets it only when all of these hold: the document gate is enabled in Settings, the page is captured or imported with no crop quad (`processCapture`), and the gate's top class is `DIGITAL_SCREEN` with probability at least 0.6 (`SCREEN_HINT_THRESHOLD`). Editor saves go through `reprocessPage` with a crop quad, so they do not set the hint.

### Legacy presets

Presets removed in the filter overhaul are remapped to their closest replacement. The Room migration (4 → 5) rewrites stored values, and `PageFilterPreset.fromStorage` maps them on read (for example from an older `.scanly` backup). Unknown values fall back to Original.

| Legacy value | Replaced by | Former name |
| --- | --- | --- |
| `enhanced_color` | Natural (`natural_color`) | Enhanced Color |
| `shadow_reduction` | Magic Color (`magic_color`) | Shadow Reduction |
| `clean` | Grayscale (`grayscale`) | Clean |
| `soft_black_and_white` | Faded Text (`faded`) | Soft Black & White (Text Enhance) |
| `receipt` | Faded Text (`faded`) | Receipt |

### Picker chips

The filter picker thumbnails use `OpenCvPageFilterProcessor.applyAll`: one shared profile, every preset rendered at **intensity 1.0** (the designed look). The live preview and the saved JPEG use the page's own intensity.

## Step 5: Post-filter adjustments

After the preset, `PageFilterAdjustmentsApplier` applies per-page tweaks persisted on `ScanPage`:

| Field | Range | Effect |
| --- | --- | --- |
| `intensity` | 0…1.5 (UI 0–150 %, default 1) | Strength of the preset. Consumed by the filter engine, not by the adjustments applier. Shown as the first **Intensity** slider on Adjust. Has no effect on Original. |
| `brightness` | −1…1 | Linear shift on RGB |
| `contrast` | −1…1 | Scale on RGB around mid-gray (128). 0 is identity; −1 flattens to gray |
| `saturation` | −1…1 | HSV saturation scale |
| `sharpness` | 0…1 | Unsharp mask amount |

Brightness and contrast operate on RGB only. The alpha channel is never scaled, so pixels can no longer become semi-transparent. Contrast pivots on mid-gray; earlier builds pivoted on black.

Identity (all tonal fields zero) skips extra work. Editor **Adjust** edits these; bulk “apply filter to all pages” changes only the preset, not other pages’ intensity or adjustments.

## Step 6: Output

| Output | Settings |
| --- | --- |
| Processing size | Longest edge capped at **2800 px** (`DefaultPageImageProcessor.MAX_PROCESSING_DIMENSION`). Decoding samples down by a power of two, then finishes with an exact filtered downscale to the cap |
| Processed JPEG | Quality 94, max dimension 2800 px |
| Thumbnail | Generated for list display |

Paths under `processed/` and `thumbs/` per document. `ThumbnailCache` is invalidated after reprocess. Camera capture resolution is covered in [capture-and-scan.md](capture-and-scan.md).

## Failure handling

| Condition | Behavior |
| --- | --- |
| No quad detected (capture/import) | Mark `NEEDS_REVIEW`; user fixes on crop screen |
| AI Detect finds nothing | Snackbar; existing handles unchanged |
| Processing exception | Fallback thumbnail from raw when possible; raw preserved |
| Filter render exception | Unfiltered copy is saved and the applied preset is recorded as Original |
| Editor reprocess failure | Raw preserved; previous processed path may remain |

## Editor integration

### Page editor (`editor/page/{pageId}`)

- Main surface: **live** cropped + filtered + adjusted preview (same pipeline as reprocess, preview-sized)
- Toolbar: **Crop · Filters · Adjust · Retake · Delete**
- **Filters** → full-screen `FilterPickerScreen` (large live preview + preset chips + apply-to-all)
- **Adjust** → full-screen `FilterCustomizeScreen` (**Intensity** first, then brightness, contrast, saturation, sharpness; hold Compare for filter-only; scrollbar)
- Done on the editor saves filter, intensity and adjustments (with latest crop/rotation from storage)

### Crop screen (`crop/page/{pageId}`)

- Full-frame preview with four-point handles (`CropQuadEditor`)
- **AI Detect · Left · Right · Reset · Done**
- Done → `UpdatePageEditsUseCase` with crop + rotation; keeps current filter, intensity + adjustments

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
| `DefaultPageImageProcessor` | `data/processing/` | Orchestrates full pipeline; 2800 px processing cap |
| `PerspectiveQuadMath` | `core/processing/` | Geometry math |
| `PerspectiveBitmapTransform` | `core/processing/` | Bitmap warp |
| `OpenCvPageFilterProcessor` | `core/processing/` | Filter facade (`apply`, `applyWithResolvedPreset`, `analyze`, `applyAll`) |
| `PageImageProfile` | `core/processing/` | Page profile (content, paper, noise and tone stats, and the `screenLikely` hint) |
| `PageImageAnalyzer` | `core/processing/filter/` | Canonical 720 px profile, including tone stats |
| `PageBackgroundModel` | `core/processing/filter/` | 360 px paper-aware shading model; mask, multi-scale fill, per-channel division |
| `PageFilterLook` | `core/processing/filter/` | Preset → render parameters, scaled by intensity and resolution |
| `PageFilterEngine` | `core/processing/filter/` | Renders the Color, Natural, Gray and Binary modes; hands Screen and Vivid to `PageColorLooks` |
| `PageColorLooks` | `core/processing/filter/` | Global Screen and Vivid looks: de-screening, dark-mode veil removal, white reference, levels, CLAHE |
| `PageFilterOperators` | `core/processing/filter/` | Tone curve LUT, vibrance, unsharp mask, threshold, despeckle |
| `PageFilterAdjustmentsApplier` | `core/processing/` | Brightness / contrast / saturation / sharpness on RGB |
| `AdaptivePageFilterTuning` | `core/processing/` | Auto preset routing (Screen first, from the screen hint) |
| `LiteRtDocumentCornerDetector` | `core/ml/` | Multi-model corner inference |
| `LiteRtDocumentGateDetector` | `core/ml/` | Physical-document gate |
| `AutomaticDocumentModelSelector` | `core/ml/` | Device latency calibration |
| `DocumentQuadPolicy` / `CornerCandidatePolicy` | `core/ml/` | Geometry acceptance and ranking |
| `BookPageQuadAnalyzer` / `BookAwareCornerResolver` | `core/ml/` | Book page isolation |
| `CropQuadEditor` | `core/editing/` | Interactive crop UI logic |

## Related docs

- [capture-and-scan.md](capture-and-scan.md) — camera session, capture resolution and finalize trigger
- [export.md](export.md) — uses processed images for PDF/ZIP
- [../data/file-storage.md](../data/file-storage.md) — output file layout
- [../architecture/screens.md](../architecture/screens.md) — editor / crop ViewModels
