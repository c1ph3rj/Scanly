# Changelog

All notable user-facing changes to Scanly are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses semantic-style version names.

## [Unreleased]

### Added

- **Recoverable shared library** — documents, groups, page manifests, raw captures, processed images, and thumbnails now live in a user-selected shared Scanly folder that remains after uninstall.
- **Database-first startup sync** — splash performs generation-based reconciliation and rebuilds the Room index from shared manifests after reinstall or database loss.
- **Library maintenance controls** — Settings can clear temporary files, rebuild the database index, reconnect or switch folders, and permanently delete the selected shared library.

- **Suggested document names** — new scan and new document dialogs now include a **Suggest name** button with date-based formats. Suggestions avoid duplicate titles; manual creates also auto-suffix when a title is already taken.
- **Suggested folder names** — new folder dialogs and inline folder creation when moving documents use the same **Suggest name** flow, with folder-specific formats and duplicate-safe naming.
- **Dual release channels** — signed builds now expose `githubRelease` and `playStoreRelease` variants. The GitHub build checks GitHub Releases and opens the release page, while the Play Store build uses Google Play in-app updates.
- **Google Play in-app updates** — production installs now check Google Play for updates and can download and install them in-app. Flexible updates show a restart prompt after download; high-priority updates can launch the immediate Play Store flow automatically.
- **Advanced PDF export controls** — PDF save and share flows now support optional open-password protection, page numbers at the lower left/center/right, per-page auto orientation, and A3/A4/A5/B4/B5/Letter/Tabloid/Legal/Executive/Postcard/Foolscap paper sizes.

### Changed

- **Write-through persistence** — successful document, page, edit, retake, reorder, group, and delete operations commit versioned shared recovery data before updating the operational Room index.
- **Immutable retakes** — page retakes create a new raw capture instead of overwriting the existing original.
- The previous app-private document directory and `scanly.db` schema are intentionally not migrated into the new shared library.

- **Library filters** — replaced the underline-style Library tabs with three rounded filter pills whose selected and unselected states match Scanly's Material 3 surfaces.
- **Page preview zoom** — double-tapping a zoomed page now reliably returns it to the fitted scale without the pan gesture consuming the taps, the zoom level stays hidden at 1.0x, and the reset action uses a fit-to-screen icon.
- **Page preview navigation** — swiping between pages now moves only the page image while the preview controls stay fixed. The page title follows the selected page, and Share page/Edit page actions now live in a three-dot menu while Reset zoom remains directly accessible.
- **Top spacing** — fixed double status-bar inset across the app; the activity shell no longer pads the top, so each screen applies it once.
- **Settings layout** — the main settings screen is leaner: FAQs and open-source licenses moved to dedicated sub-screens, redundant version and URL subtitles removed, and storage shows a single total. Settings title now matches Library (`displaySmall`).
- Update messaging now reflects the selected distribution channel instead of always naming Google Play.
- Fixed-size PDF exports now use real print dimensions, while auto-fit keeps each scan's aspect ratio. Numbered exports reserve a footer so the page number does not cover document content.
- **Faster shared-library and capture pipeline** — resolved SAF directories and immutable asset entries are cached, catalog state stays in memory between write-through mutations, raw backup overlaps image processing, processed/thumbnail writes run together, recovery manifests are compact, and thumbnails are generated without re-decoding the processed JPEG. Live camera analysis also reuses ML buffers and passes its detected crop into capture processing instead of running duplicate detection.

## [1.0.9] - 2026-06-28

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
