# Sprint 3

Sprint 3 delivers the first useful manual scanner flow.

Implemented in this sprint:

- CameraX manual capture dependencies and camera permission wiring
- a document-bound scan session route for manual page capture
- raw page file allocation under app-private document storage
- EXIF-aware thumbnail generation for captured pages
- page persistence through the existing Room database
- document metadata updates after capture:
  - page count
  - last updated time
  - cover thumbnail switches from generated art to the latest captured page thumbnail
- a captured-page strip inside the scan session
- document detail now shows persisted captured pages after reopening

Build verification completed:

- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug`

Notes:

- this sprint is intentionally manual-first; there is no live ML overlay or auto-capture yet
- raw captures are preserved in app-private storage for future processing
- Sprint 4 can now focus on live detection and auto-capture without rebuilding the capture persistence path
