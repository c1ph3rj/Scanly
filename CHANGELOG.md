# Changelog

All notable user-facing changes to Scanly are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses semantic-style version names.

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

[1.0.8.betaq]: https://github.com/c1ph3rj/Scanly/compare/v1.0.7...v1.0.8.betaq
[1.0.7]: https://github.com/c1ph3rj/Scanly/compare/v1.0.4...v1.0.7
[1.0.4]: https://github.com/c1ph3rj/Scanly/compare/v1.0.0...v1.0.4
[1.0.0]: https://github.com/c1ph3rj/Scanly/releases/tag/v1.0.0
