# Domain Models

Model classes and enums under `domain/model/`.

## Document and pages

| Model | File | Purpose |
| --- | --- | --- |
| `ScanDocument` | `ScanDocument.kt` | Document metadata: id, title, pageCount, cover path, groupId, timestamps |
| `ScanPage` | `ScanPage.kt` | Page: paths, crop quad, rotation, filter preset + adjustments, processing state, pageIndex |
| `PageFilterAdjustments` | `PageFilterAdjustments.kt` | Brightness/contrast/saturation/sharpness post-filter tweaks |
| `DocumentGroup` | `DocumentGroup.kt` | Collection: id, title, doc/page counts, cover path, timestamps |
| `PageCaptureDraft` | `PageCaptureDraft.kt` | Transient state during active capture session |
| `PagePreviewPaths` | `PagePreviewPaths.kt` | Extension resolving display paths for preview |
| `PageProcessingState` | `PageProcessingState.kt` | `CAPTURED`, `PROCESSED`, or `NEEDS_REVIEW` |
| `DocumentKind` | `DocumentKind.kt` | On-device document-type classification used in search filters and results |
| `PageText` / `PageTextBlock` / `PageTextLine` | `PageText.kt` | Recognized/corrected page text and normalized layout for reading and overlays |
| `TextIndexProgress` | `PageText.kt` | Total and pending pages for background index progress |
| `LibrarySearch` models | `LibrarySearch.kt` | Search query, result filters, and ranked name/page-text hits |
| `PageFilterPreset` | `PageFilterPreset.kt` | Ten filter presets with `storageValue` strings; legacy values map to replacements |
| `DocumentTitleFormat` | `DocumentTitleFormat.kt` | Four rotatable auto-title formats for new documents |
| `GroupTitleFormat` | `GroupTitleFormat.kt` | Four rotatable auto-title formats for new folders |

### PageFilterPreset values

`ORIGINAL` (`original`), `AUTO` (`auto`), `MAGIC_COLOR` (`magic_color`), `NATURAL_COLOR` (`natural_color`), `VIVID_COLOR` (`vivid_color`), `SCREEN` (`screen`), `GRAYSCALE` (`grayscale`), `BLACK_AND_WHITE` (`black_and_white`), `WHITEBOARD` (`whiteboard`), `FADED` (`faded`)

Legacy storage values are read through `PageFilterPreset.fromStorage` and remapped by the Room migration: `enhanced_color` → `natural_color`, `shadow_reduction` → `magic_color`, `clean` → `grayscale`, `soft_black_and_white` → `faded`, `receipt` → `faded`. Unknown values fall back to `ORIGINAL`.

### DocumentTitleFormat values

`ScanDateTime` (default), `DocumentDateTime`, `ScanDate`, `ScanIsoDate`

### GroupTitleFormat values

`FolderDateTime` (default), `NewFolderDateTime`, `FolderDate`, `FolderIsoDate`

## Export

| Model | File | Purpose |
| --- | --- | --- |
| `PdfExportOptions` | `PdfExportOptions.kt` | Password, page numbers, page size, orientation, margins |
| `ExportArtifact` | `ExportArtifact.kt` | Export result with file path and metadata |
| `ShareArtifact` | `ShareArtifact.kt` | Share-ready artifact with URI info |
| `SavedExport` | `SavedExport.kt` | Final shared-storage name, destination label, and URI |
| `ExportDestination` | `ExportDestination.kt` | `DefaultDownloadsScanly` or `CustomTree(uri, displayName)` |

## Library signing

| Model | File | Purpose |
| --- | --- | --- |
| `SignatureKind` / `SignatureSource` | `SignModels.kt` | Signature vs initials, and drawn vs imported source |
| `SavedSignature` | `SignModels.kt` | Reusable local ink image and dimensions |
| `SignInk` | `SignModels.kt` | Navy, black, blue, or red mark colour |
| `NormRect` | `SignModels.kt` | Page-relative mark placement |
| `SignMark` | `SignModels.kt` | Image signature, text, date, or checkbox mark |
| `PageMarkup` | `SignModels.kt` | Editable marks and clean base image for a Scanly page |
| `PdfSignSession` / `SignPageSize` | `SignModels.kt` | Local PDF session and visual page dimensions |

## Settings and app data

| Model | File | Purpose |
| --- | --- | --- |
| `ThemeMode` | `ThemeMode.kt` | `SYSTEM`, `LIGHT`, `DARK` |
| `DocumentCornerModel` | `DocumentCornerModel.kt` | `LITE`, `STANDARD`, `HIGH`, `ACCURATE` with storage values and display names |
| `SettingsContent` | `SettingsContent.kt` | Aggregated FAQs and licenses for Settings |
| `SettingsFaq` | `SettingsFaq.kt` | Single FAQ entry |
| `LicenseInfo` | `LicenseInfo.kt` | Third-party license entry |
| `AppStorageUsage` | `AppStorageUsage.kt` | Byte counts for library files, temporary export cache, and the database/search index |

Pure black (AMOLED) is a boolean DataStore preference (`pure_black_enabled`), not a `ThemeMode` value.

## App updates

| Model | File | Purpose |
| --- | --- | --- |
| `AppRelease` | `AppRelease.kt` | Remote release: tag, body, URL, optional APK asset |
| `AppReleaseAsset` | `AppRelease.kt` | GitHub release asset metadata |
| `AppUpdateChannel` | `AppRelease.kt` | `GITHUB` or `PLAY_STORE` (from `BuildConfig.UPDATE_CHANNEL`) |
| `AppUpdateCheckResult` | `AppRelease.kt` | Result of update comparison with channel and Play update type |
| `PlayInAppUpdateType` | `PlayInAppUpdate.kt` | `FLEXIBLE` or `IMMEDIATE` |
| `PlayInstallStatus` | `PlayInAppUpdate.kt` | Play download/install lifecycle status |
| `PlayInAppUpdateInstallState` | `PlayInAppUpdate.kt` | Download progress for flexible updates |
| `PlayInAppUpdateAvailability` | `PlayInAppUpdate.kt` | Play Store update availability metadata |

## Related docs

- [../data/database.md](../data/database.md) — Room entity mapping
- [use-cases.md](use-cases.md) — operations on these models
