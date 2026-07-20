# Performance notes

Measured-oriented guidance for Scanly optimization work (feature/optimization).

## Hot paths

| Flow | Dominant cost | Code |
| --- | --- | --- |
| Live camera | Gate + corner TFLite + bitmap orient | `DefaultLiveDocumentAnalysisSession` |
| Capture finalize / import | Decode + still corners + OpenCV filter | `DefaultPageImageProcessor`, `OpenCvPageFilterProcessor` |
| Filter picker | `applyAll` shared profile | `OpenCvPageFilterProcessor.applyAll` |
| Cold start | Compose + widgets | `MainActivity` |

## Wins already in tree

1. **Live early-out** — if frame quality is lens-blocked or too dark, skip oriented bitmap + ML (`DefaultLiveDocumentAnalysisSession`).
2. **Gate reject** — skips corner detector when gate is unstable/rejected.
3. **Import downsample** — gallery import longest edge capped at **2400** (aligned with process decode).
4. **Process decode** — `DefaultPageImageProcessor` uses `inSampleSize` to max 2400.
5. **OpenCV analysis downsample** — profile analysis mats capped at 720px longest edge (`OpenCvMatSupport.forAnalysisDownsample`).
6. **Shared filter profile** — `applyAll` analyzes once for all presets.
7. **Widget refresh deferred** — `MainActivity` posts widget rebind after first layout.

## How to measure (device)

| Metric | How |
| --- | --- |
| Live frame p50/p95 | Android Studio CPU/System Trace; optional debug logs around `analyzeFrame` |
| Finalize to thumb | Trace `PageRepository.finalizeCapture` / processor |
| Filter ms | Model benchmark screen + filter apply in editor |
| Cold start | Macrobenchmark or Studio App Startup |

Record before/after on a mid-tier phone when shipping user-visible perf changes.

## Follow-ups (optional)

- Split remaining OpenCV presets into per-filter modules for isolated tuning.
- Move pure camera trackers to `core` so live session can sit fully in `data`.
- Parallelize independent non-OpenCV work only if Mat ownership stays serial.
