# Domain Models

All model classes in `domain/model/` (16 files).

## Document and pages

| Model | File | Purpose |
| --- | --- | --- |
| `ScanDocument` | `ScanDocument.kt` | Document metadata: id, title, pageCount, cover path, groupId, timestamps |
| `ScanPage` | `ScanPage.kt` | Page: paths, crop quad, rotation, filter, processing state, pageIndex |
| `DocumentGroup` | `DocumentGroup.kt` | Collection: id, title, doc/page counts, cover path, timestamps |
| `PageCaptureDraft` | `PageCaptureDraft.kt` | Transient state during active capture session |
| `PagePreviewPaths` | `PagePreviewPaths.kt` | Resolved paths for preview display |
| `PageProcessingState` | `PageProcessingState.kt` | `PROCESSED` or `NEEDS_REVIEW` |
| `PageFilterPreset` | `PageFilterPreset.kt` | Ten filter modes with `storageValue` strings |

### PageFilterPreset values

`ORIGINAL`, `AUTO`, `ENHANCED_COLOR`, `GRAYSCALE`, `BLACK_AND_WHITE`, `CLEAN`, `SHADOW_REDUCTION`, `MAGIC_COLOR`, `RECEIPT`, `SOFT_BLACK_AND_WHITE`

## Export

| Model | File | Purpose |
| --- | --- | --- |
| `PdfExportOptions` | `PdfExportOptions.kt` | Page size, orientation, margins for PDF export |
| `ExportArtifact` | `ExportArtifact.kt` | Export result with file path and metadata |
| `ShareArtifact` | `ShareArtifact.kt` | Share-ready artifact with URI info |

## Settings and app data

| Model | File | Purpose |
| --- | --- | --- |
| `ThemeMode` | `ThemeMode.kt` | `SYSTEM`, `LIGHT`, `DARK` |
| `SettingsContent` | `SettingsContent.kt` | Aggregated FAQs and licenses for Settings |
| `SettingsFaq` | `SettingsFaq.kt` | Single FAQ entry |
| `LicenseInfo` | `LicenseInfo.kt` | Third-party license entry |
| `AppStorageUsage` | `AppStorageUsage.kt` | Byte counts: documents, export cache, database |

## App updates

| Model | File | Purpose |
| --- | --- | --- |
| `AppRelease` | `AppRelease.kt` | Remote release: tag, body, URL |
| `AppUpdateCheckResult` | `AppUpdateCheckResult.kt` | Result of update comparison |

## Related docs

- [../data/database.md](../data/database.md) — Room entity mapping
- [use-cases.md](use-cases.md) — operations on these models