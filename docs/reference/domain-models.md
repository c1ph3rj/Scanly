# Domain Models

All model classes in `domain/model/` (22 files).

## Document and pages

| Model | File | Purpose |
| --- | --- | --- |
| `ScanDocument` | `ScanDocument.kt` | Document metadata: id, title, pageCount, cover path, groupId, timestamps |
| `ScanPage` | `ScanPage.kt` | Page: paths, crop quad, rotation, filter, processing state, pageIndex |
| `DocumentGroup` | `DocumentGroup.kt` | Collection: id, title, doc/page counts, cover path, timestamps |
| `PageCaptureDraft` | `PageCaptureDraft.kt` | Transient state during active capture session |
| `PagePreviewPaths` | `PagePreviewPaths.kt` | Extension resolving display paths for preview |
| `PageProcessingState` | `PageProcessingState.kt` | `CAPTURED`, `PROCESSED`, or `NEEDS_REVIEW` |
| `PageFilterPreset` | `PageFilterPreset.kt` | Ten filter modes with `storageValue` strings |
| `DocumentTitleFormat` | `DocumentTitleFormat.kt` | Four rotatable auto-title formats for new documents |
| `GroupTitleFormat` | `GroupTitleFormat.kt` | Four rotatable auto-title formats for new folders |

### PageFilterPreset values

`ORIGINAL`, `AUTO`, `ENHANCED_COLOR`, `GRAYSCALE`, `BLACK_AND_WHITE`, `CLEAN`, `SHADOW_REDUCTION`, `MAGIC_COLOR`, `RECEIPT`, `SOFT_BLACK_AND_WHITE`

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

## Library backup and restore

| Model | File | Purpose |
| --- | --- | --- |
| `RestoreMode` | `LibraryArchive.kt` | `REPLACE` or `MERGE` |
| `BackupEstimate` | `LibraryArchive.kt` | Source/required/available bytes and backup eligibility |
| `ArchiveOperation` | `LibraryArchive.kt` | `BACKUP` or `RESTORE` |
| `ArchiveWorkPhase` | `LibraryArchive.kt` | WorkManager phase enum |
| `ArchiveWorkState` | `LibraryArchive.kt` | Progress, message, cancel state |

## Settings and app data

| Model | File | Purpose |
| --- | --- | --- |
| `ThemeMode` | `ThemeMode.kt` | `SYSTEM`, `LIGHT`, `DARK` |
| `SettingsContent` | `SettingsContent.kt` | Aggregated FAQs and licenses for Settings |
| `SettingsFaq` | `SettingsFaq.kt` | Single FAQ entry |
| `LicenseInfo` | `LicenseInfo.kt` | Third-party license entry |
| `AppStorageUsage` | `AppStorageUsage.kt` | Byte counts: documents, export cache, database, archive workspace |

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