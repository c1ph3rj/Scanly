# Changelog

All notable user-facing changes to Scanly are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses semantic-style version names.

## [Unreleased]

### Added

- **Tools tab** — new bottom navigation page (Home · Library · Tools · Settings) with a grid of capture and PDF utilities.
- **Tools → Scan / Import** — start a camera scan or gallery import from the Tools hub (same limits and flows as Home).
- **QR Code tool** — scan QR/barcodes with the camera (copy/open) or generate a QR image from text/URL and save/share it.
- **PDF toolkit** — offline utilities that accept device PDFs or Scanly library documents:
  - **Reader** — page-by-page or continuous-scroll reading, pinch-zoom, and password unlock
  - **Merge** — combine multiple PDFs into one, then preview, save, share, or return directly to Tools from a focused completion view
  - **Compress** — re-encode pages at High / Balanced / Smallest with before/after sizes
  - **Password** — protect with an open password or remove an existing one
  - **Watermark** — export-accurate previewed stamps with page-relative sizing, dense tiled or large single layouts, Small/Medium/Large scale, first-page/all-page coverage, diagonal/horizontal orientation, and opacity control

### Fixed

- **PDF reader** — Scanly library documents are copied to a stable reader file before rendering, preventing concurrent page loads from deleting the PDF mid-open.
- **PDF result previews** — Merge, Compress, Password, and Watermark results now open directly in Scanly's built-in reader instead of depending on another installed PDF app; returning from the reader restores the completed Save/Share view.
- **Watermark editor accuracy** — the first-page editor proof now uses the same PDF stamping engine, font metrics, crop bounds, opacity, angle, and repeat positions as the exported file instead of a separate UI approximation; rapid edits are debounced and render only one page.
- **Watermark usability** — stamps now scale to the page instead of a fixed 42pt size; tiled mode fills the page with a dense security field (including rotated corners) instead of a few sparse marks; single mode draws one large tracked stamp; defaults prefer tiled Medium diagonal coverage.
- **Watermark horizontal tiling** — horizontal orientation no longer overlaps; tile spacing and row stagger adapt to angle so flat text keeps full width/height gaps while diagonal still packs tightly.
- **PDF merge** — source PDF documents now remain open until the merged file is written, preventing closed-stream failures when merging library documents.
- **PDF compress** — page-by-page streaming avoids OOM; quality presets scale long-edge resolution correctly and preserve original page geometry.

- **Pure black (AMOLED) theme** — Settings → Look & feel can enable true black Material 3 surfaces in dark mode to reduce power draw on OLED displays. Preference is stored in DataStore and takes effect whenever dark theme is active.
- **Physical-document semantic gate** — a 1.14 MB float16 MobileNetV3-Small model now screens physical documents from digital screens and other rectangular objects before corner detection. Live preview requires two consecutive accepted frames; post-processing uses a stricter threshold.
- **Configurable document-detection models** — Settings can independently configure Lite, Standard, High, or Accurate for live preview and post-processing, or calibrate Lite/Standard/High on the current device and automatically choose the highest-accuracy options within separate latency budgets. Manual selectors are locked while automatic selection is enabled, and the semantic gate can be enabled or bypassed independently.
- **Model benchmark screen** — selected local images can be run sequentially through all four models, with per-image timing/detection data and aggregate average, P50, P95, detection, and failure statistics.

### Changed

- **Document model labels** — the corner-model ladder is now **Lite · Standard · High · Accurate**. Accurate is the former Legacy YOLO-pose model; High is the former Accurate 384 px regression model. Existing preferences keep the same underlying weights.
- **Gallery import processing** — imported images are normalized to JPEG and always run the post-processing corner pipeline with still-image quad readiness (near-full-frame scans, ID/RC cards, etc.); book-page gutter analysis and model-disagreement nulling no longer drop good offline detections that Model Benchmark already shows; raw detector fallback if the resolver still returns no quad.
- **Import progress UI** — Home, Tools, and Document detail show a blocking loader with live “Image X of Y · detecting document” status while gallery imports run.
- **Auto filter** — smarter preset selection (Clean paper / Soft B&W / Enhanced color / Shadow reduce) and the concrete filter Auto chose is persisted instead of the Auto label alone.
- **Model benchmark** — results are grouped by image with a 2×2 preview grid per model; each preview draws the detected document polygon (or dims when none) alongside confidence, timing, and pipeline stats.
- **PDF compress success** — uses the same focused completion screen as Merge (check mark, file card, Preview / Save / Share, Back to tools). Source picker, quality options, and the before/after card no longer linger after success; size savings stay as a single result line under the file name.
- **PDF compress setup** — shows a first-page preview of the selected PDF at the top of the quality screen so the document is visible before compressing.
- **PDF password** — redesigned to match the other toolkit tools: first-page preview, Protect/Remove option cards, clearer password form hierarchy, optional current-password for re-protect, and a focused completion screen (Preview / Save / Share / Back to tools).
- **Tools workspace** — rebuilt the Tools hub, QR workspace, and PDF toolkit screens into a consistent on-device workflow with visual Scanly-library PDF previews, grid/list source selection, clear working states, and explicit completed-file actions.
- **Settings Look & feel** — the Appearance section is now titled Look & feel and groups theme mode with the pure black OLED option.
- **Safer camera overlay and faster rejection path** — corner inference is skipped when the semantic gate rejects a frame, and only convex, fully in-frame, plausible document quads can be drawn or auto-captured. The benchmark screen now reports gate classification, gate latency, and combined pipeline results.
- **Stable best-quad selection** — geometrically ambiguous corner results are conditionally verified with the High model, ranked by confidence and shape quality, smoothed across nearby frames, and prevented from replacing the visible outline until the new location is consistently detected. Stability now checks the worst-moving corner as well as the average, preventing one corner from jumping to a nearby monitor or keyboard edge.
- **Book-page isolation** — live and post-processing detection now samples visual edge support around proposed page boundaries and searches for a continuous book gutter. An off-centre gutter trims away a small adjacent-page sliver and keeps the dominant page; a centred, genuinely ambiguous two-page spread suppresses the outline and asks the user to move closer.
- **Cleaner scan preview** — removed the temporary model/latency/confidence diagnostics pill from the camera while retaining the dedicated benchmark screen for detailed measurements.
- **Document filters** — rebuilt the enhancement pipeline to flatten uneven paper lighting without creating mottled backgrounds or halos. Color filters now neutralize paper casts while retaining logos and marks; Auto avoids aggressive text enhancement for low-detail pages and keeps long color documents out of Receipt mode.

## [1.0.9] - 2026-07-05

### Added

- **Configurable export storage** — Save actions now write directly to `Downloads/Scanly` by default, with a persisted custom base-folder option in Settings. Existing files are never overwritten.
- **Library backup and restore** — exact, compressed `.scanly` snapshots are stored under the destination's lowercase `backup` child. Backups are gated by a conservative free-space check and run through foreground WorkManager jobs. Restore validates and stages archives before offering Replace or Merge-as-copies behavior.
- **Storage & backup screen** — Settings now shows storage breakdowns, export and backup paths, required and available backup space, live operation progress, cancellation, and destructive data controls.
- **Suggested document names** — new scan and new document dialogs now include a **Suggest name** button with date-based formats. Suggestions avoid duplicate titles; manual creates also auto-suffix when a title is already taken.
- **Suggested folder names** — new folder dialogs and inline folder creation when moving documents use the same **Suggest name** flow, with folder-specific formats and duplicate-safe naming.
- **Dual release channels** — signed builds now expose `githubRelease` and `playStoreRelease` variants. The GitHub build checks GitHub Releases and opens the release page, while the Play Store build uses Google Play in-app updates.
- **Google Play in-app updates** — production installs now check Google Play for updates and can download and install them in-app. Flexible updates show a restart prompt after download; high-priority updates can launch the immediate Play Store flow automatically.
- **Advanced PDF export controls** — PDF save and share flows now support optional open-password protection, page numbers at the lower left/center/right, per-page auto orientation, and A3/A4/A5/B4/B5/Letter/Tabloid/Legal/Executive/Postcard/Foolscap paper sizes.

### Changed

- **Page preview actions** — the overflow menu now exposes Retake and Delete directly alongside Share and Edit. Delete keeps the existing confirmation and asset cleanup behavior, while retakes return to preview when launched there; both actions remain available in the page editor.
- **Storage & backup reliability** — local Storage Access Framework folders now fall back to mounted-volume capacity when their document provider omits free-space metadata. Providers that cannot report capacity remain usable and fail safely if a backup exhausts storage; the screen now explains that state, hides the transient backup workspace while it is empty, integrates the space refresh control, opens Restore in the backup folder, and rejects non-`.scanly` files before restore begins.
- **Export save flow** — document and group saves no longer open a file creator for every export; sharing remains unchanged.
- **Library filters** — replaced the underline-style Library tabs with three rounded filter pills whose selected and unselected states match Scanly's Material 3 surfaces.
- **Page preview zoom** — double-tapping a zoomed page now reliably returns it to the fitted scale without the pan gesture consuming the taps, the zoom level stays hidden at 1.0x, and the reset action uses a fit-to-screen icon.
- **Page preview navigation** — swiping between pages now moves only the page image while the preview controls stay fixed. The page title follows the selected page, and Share page/Edit page actions now live in a three-dot menu while Reset zoom remains directly accessible.
- **Top spacing** — fixed double status-bar inset across the app; the activity shell no longer pads the top, so each screen applies it once.
- **Settings layout** — the main settings screen is leaner: FAQs and open-source licenses moved to dedicated sub-screens, redundant version and URL subtitles removed, and storage shows a single total. Settings title now matches Library (`displaySmall`).
- Update messaging now reflects the selected distribution channel instead of always naming Google Play.
- Fixed-size PDF exports now use real print dimensions, while auto-fit keeps each scan's aspect ratio. Numbered exports reserve a footer so the page number does not cover document content.

### Changed

- Refined portrait and landscape document capture layouts and aligned camera/editor controls with the app theme.
- Improved capture feedback for lighting, blur, lens obstruction, and document framing.

### Fixed

- Retaking a page now returns directly to its editor after capture completes.
- Renaming a document no longer replaces its first-page preview thumbnail.

## [1.0.8.betaq] - 2026-06-27

### Changed

- Removed `REQUEST_INSTALL_PACKAGES` permission. The in-app update flow now redirects to the GitHub release page instead of downloading and installing the APK.

## [1.0.7] - 2026-06-27

### Changed

- The update dialog is now rate-limited to once every 6 hours after it is shown, instead of appearing on every app launch.

## [1.0.4] - 2026-06-15

### Added

- **Document groups (collections)** — create, rename, delete, and browse grouped documents from the new Library flow.
- **Group detail screen** — view all documents in a group, manage membership, and export or share group PDFs (single merged PDF or zipped PDF set).
- **Gallery import** — import up to 10 images at a time from the device photo picker on Home and in document detail to start or extend a document without scanning.
- **Storage usage panel** in Settings — shows on-device usage for documents, export cache, and the local database.
- **Clear all data** action in Settings — wipes library records, document files, export cache, and thumbnail cache after confirmation.
- **Shared UI building blocks** — reusable cards, thumbnails, FAB menus, and export/share sheets used across Home, Library, and document screens.
- **Thumbnail cache** and **preview image sizing** utilities for faster, more consistent list and detail rendering.

### Changed

- **Home screen** redesigned around recent documents, recent groups, quick scan/create actions, and a shortcut into the full Library.
- **Library screen** added as the primary place to search, organize, and manage documents and groups.
- **Document detail** refreshed with improved page review, import-from-gallery support, and updated export/share controls.
- **Settings screen** reorganized with refreshed appearance controls, project links, and the new storage/data section.
- **Material 3 theme** updated with revised color roles and surface styling across major screens.
- **Navigation** extended for Library, group detail, and streamlined flows between Home, scan session, and document review.
- **Export pipeline** refactored to support group-level PDF generation and sharing.

### Fixed

- More reliable preview/thumbnail loading when scrolling large libraries.
- Improved presentation formatting for document metadata shown in lists and detail headers.

### Technical

- Room database bumped to schema version `3` with migrations for document `groupId` and group tables.
- New domain/data layers for groups, app storage usage, and bulk data clearing.
- New use cases for group CRUD, group export/share, image import, storage inspection, and clear-all-data.
- Unit tests added for `StorageFormatter`, `PreviewImageSizer`, and document presentation formatting.

### Upgrade notes

- Upgrading from `1.0.0` keeps existing documents; they remain ungrouped until you move them into a collection.
- **Clear all data** is destructive and cannot be undone.

## [1.0.0] - initial release

### Added

- Offline-first document scanning with CameraX capture and page guidance.
- Page crop, rotate, and filter editing.
- Multi-page document review, reorder, delete, and add-more-pages flows.
- Local document library with Room persistence and app-private file storage.
- PDF export and image archive export/share.
- Settings with theme mode, FAQs, licenses, and support links.

[1.0.9]: https://github.com/c1ph3rj/Scanly/compare/v1.0.8.betaq...v1.0.9
[1.0.8.betaq]: https://github.com/c1ph3rj/Scanly/compare/v1.0.7...v1.0.8.betaq
[1.0.7]: https://github.com/c1ph3rj/Scanly/compare/v1.0.4...v1.0.7
[1.0.4]: https://github.com/c1ph3rj/Scanly/compare/v1.0.0...v1.0.4
[1.0.0]: https://github.com/c1ph3rj/Scanly/releases/tag/v1.0.0
