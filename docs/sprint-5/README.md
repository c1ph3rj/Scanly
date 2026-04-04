# Sprint 5

Sprint 5 turns raw captures into scan-ready page assets while preserving the original photo for future reprocessing.

Implemented in this sprint:

- still-image page processing immediately after capture finalization
- non-destructive storage flow:
  - raw capture remains in `documents/{documentId}/raw`
  - processed page is written to `documents/{documentId}/processed`
  - thumbnail is refreshed from the processed output when available
- full-still document detection reused through the production LiteRT detector
- 4-point perspective correction using the detected corner quad
- a first document enhancement pass that gently increases contrast and whitens neutral paper backgrounds
- page metadata now persists:
  - processed image path
  - crop quad
  - filter preset
  - processing state
- document detail UI now shows whether each page is fully processed or still needs later review

Build verification completed:

- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug`

Testing guidance for this sprint:

- capture a clear page and confirm the saved thumbnail looks flatter and cleaner than the raw photo
- capture a skewed page and confirm perspective correction is visibly applied
- capture a hard page with partial visibility and confirm the page still saves without losing the raw original
- reopen the document after killing the app and confirm processed thumbnails persist

Notes:

- Sprint 5 always preserves the raw original even when crop detection is weak
- if still-image detection fails, the page falls back to a captured-state entry instead of blocking the workflow
- manual crop adjustment and per-page editing remain Sprint 6 work
