# Sprint 8

Sprint 8 completes the first production-grade export/share pass and turns Settings into a clean product-facing screen.

Implemented in this sprint:

- snap-fast cross-screen navigation with transition blink removed
- dedicated Settings route for:
  - theme mode selection
  - app version display
  - FAQ content loaded from JSON assets
  - license information loaded from JSON assets
  - developer website link to `c1ph3rj.in`
- diagnostics tooling removed from the product flow
- persisted app theme mode using DataStore preferences
- document review export/share sheet with:
  - save PDF
  - share PDF
  - save images ZIP
  - share processed pages
- PDF options sheet with:
  - portrait / landscape
  - fit / A4 / US Letter
  - no / small / big margins
  - single clean PDF output
- export progress overlay so PDF generation shows a visible loading state
- FileProvider-based external sharing so exported files can be opened safely in other apps
- review-screen top bar updated with export/share access while keeping the scanner workflow compact
- OpenCV-backed document filters for both saved processing and editor preview rendering

Build verification completed:

- `./gradlew.bat testDebugUnitTest --no-daemon`
- `./gradlew.bat assembleDebug --no-daemon`

Testing guidance for this sprint:

- switch among System, Light, and Dark in Settings and confirm the app theme updates immediately and persists after relaunch
- open FAQs and license items and confirm JSON-backed content renders correctly
- tap the developer website row and confirm it opens `https://c1ph3rj.in`
- from document review, open the export/share sheet and test:
  - save PDF with fit / A4 / US Letter
  - share PDF with portrait / landscape and different margin options
  - save images ZIP
  - share pages
- navigate repeatedly among Home, Settings, Review, Camera, and Editor and confirm the old blink is gone

Notes:

- export files are generated into app cache first, then either saved to a user-selected destination or shared through FileProvider URIs
- FAQs and license data are intentionally asset-backed so future edits can be made without touching screen code
- filter processing now uses OpenCV instead of the earlier lightweight color-matrix/pixel approximation path
