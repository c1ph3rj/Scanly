# Use Cases

Use case classes under `domain/usecase/` (including `pdftools/` and `qr/`), grouped by area.

ViewModels call use cases. Use cases call repository interfaces (or thin processors).

## Document lifecycle

| Use case | Purpose |
| --- | --- |
| `CreateDocumentUseCase` | Create empty document |
| `DeleteDocumentUseCase` | Delete document and all pages |
| `RenameDocumentUseCase` | Update document title |
| `ObserveDocumentUseCase` | Flow of single document |
| `ObserveDocumentsUseCase` | Flow of all documents |
| `ObserveRecentDocumentsUseCase` | Recent documents (limit 8) |
| `ObserveUngroupedDocumentsUseCase` | Documents without a group |
| `SuggestDocumentTitleUseCase` | Generate a duplicate-safe date-based document title |

## Page lifecycle

| Use case | Purpose |
| --- | --- |
| `PreparePageCaptureUseCase` | Allocate paths for new page capture |
| `PrepareReplacementCaptureUseCase` | Allocate paths for page retake |
| `FinalizeCapturedPageUseCase` | Run processing and persist page |
| `ObservePageUseCase` | Flow of single page |
| `ObserveDocumentPagesUseCase` | Flow of pages in a document |
| `UpdatePageEditsUseCase` | Save crop / rotation / filter / adjustments and reprocess |
| `DetectDocumentCornersUseCase` | Still-image AI crop detection for the crop screen |
| `MovePageUseCase` | Reorder page within document |
| `DeletePageUseCase` | Remove page from document |
| `ImportImagesUseCase` | Import gallery images as pages |

## Groups

| Use case | Purpose |
| --- | --- |
| `CreateGroupUseCase` | Create document group |
| `DeleteGroupUseCase` | Delete group (documents become ungrouped) |
| `RenameGroupUseCase` | Update group title |
| `SetDocumentGroupUseCase` | Assign or remove document from group |
| `ObserveGroupsUseCase` | Flow of all groups |
| `ObserveGroupUseCase` | Flow of single group |
| `ObserveRecentGroupsUseCase` | Recent groups (limit 6) |
| `ObserveGroupDocumentsUseCase` | Documents in a group |
| `SuggestGroupTitleUseCase` | Generate a duplicate-safe date-based group title |

## Export

| Use case | Purpose |
| --- | --- |
| `ExportDocumentPdfUseCase` | Generate document PDF |
| `ExportDocumentImageArchiveUseCase` | Generate document image ZIP |
| `PrepareDocumentPdfShareUseCase` | Prepare PDF share artifact |
| `PrepareDocumentImageShareUseCase` | Prepare image share artifact |
| `ExportGroupPdfUseCase` | Generate merged group PDF |
| `ExportGroupZippedPdfsUseCase` | Generate zipped PDF set for group |
| `PrepareGroupPdfShareUseCase` | Prepare merged PDF share |
| `PrepareGroupZippedPdfsShareUseCase` | Prepare zipped PDFs share |
| `SaveExportArtifactUseCase` | Copy an export into the configured shared-storage base folder |

## Page text and search

| Use case | Purpose |
| --- | --- |
| `RecognizeDocumentTextUseCase` | Ensure all pages in a document have current text recognition |
| `RecognizePageTextUseCase` | Recognize a page with the on-device ML Kit model |
| `UpdatePageTextUseCase` | Persist user corrections to recognized text |
| `SearchPageTextUseCase` | Search the page-text index and return matching pages |
| `SuggestSearchCorrectionUseCase` | Suggest a correction from indexed page vocabulary |
| `ObserveRecentSearchesUseCase` | Observe saved recent queries |
| `AddRecentSearchUseCase` | Record a submitted query |
| `RemoveRecentSearchUseCase` | Remove one recent query |
| `ClearRecentSearchesUseCase` | Clear recent query history |

## Document signing

| Use case | Purpose |
| --- | --- |
| `ObserveSignaturesUseCase` | Observe saved signatures and initials |
| `SaveSignatureUseCase` | Save a drawn or imported ink mask locally |
| `ExtractSignatureInkUseCase` | Extract signature ink from an imported image |
| `DeleteSignatureUseCase` | Delete a reusable signature |
| `LoadDocumentMarkupsUseCase` | Load editable marks on a Scanly document's pages |
| `SaveDocumentMarkupsUseCase` | Save mark changes and composite signed page images |
| `OpenPdfSignSessionUseCase` | Open and inspect a PDF for signing |
| `SignPdfUseCase` | Render marks into a new signed PDF |
| `ClosePdfSignSessionUseCase` | Release a PDF signing session |

## Legacy archive cleanup

| Use case | Purpose |
| --- | --- |
| `DeleteLegacyArchiveLeftoversUseCase` | Remove leftover temporary files from the retired backup/restore flow during upgrade |

## Settings and app data

| Use case | Purpose |
| --- | --- |
| `ObserveThemeModeUseCase` | Flow of current theme mode |
| `SetThemeModeUseCase` | Persist theme mode |
| `ObservePureBlackEnabledUseCase` | Flow of pure black (AMOLED) preference |
| `SetPureBlackEnabledUseCase` | Persist pure black preference |
| `ObserveLiveDetectionModelUseCase` | Flow of manual live-preview corner model |
| `SetLiveDetectionModelUseCase` | Persist live-preview corner model |
| `ObservePostProcessingModelUseCase` | Flow of manual post-processing corner model |
| `SetPostProcessingModelUseCase` | Persist post-processing corner model |
| `ObserveAutomaticModelSelectionUseCase` | Flow of automatic model-selection flag |
| `SetAutomaticModelSelectionUseCase` | Persist automatic model-selection flag |
| `ObserveDocumentGateEnabledUseCase` | Flow of physical-document gate flag |
| `SetDocumentGateEnabledUseCase` | Persist physical-document gate flag |
| `ObserveExportDestinationUseCase` | Flow of the configured export base folder |
| `SetExportDestinationUseCase` | Persist a custom SAF base folder |
| `ResetExportDestinationUseCase` | Return to `Downloads/Scanly` |
| `ClearExportCacheUseCase` | Remove temporary generated exports |
| `DeleteEmptyDocumentsUseCase` / `DeleteEmptyGroupsUseCase` | Clean empty library items |
| `LoadSettingsContentUseCase` | Load FAQs and licenses |
| `ObserveOnboardingCompletedUseCase` | Flow of onboarding flag |
| `CompleteOnboardingUseCase` | Mark onboarding done |
| `GetAppStorageUsageUseCase` | Calculate storage byte counts |
| `ClearAllAppDataUseCase` | Wipe all local data |
| `ObserveRatingPromptStateUseCase`, `DismissSettingsRatingCardUseCase`, `MarkAppRatedUseCase` | Manage Settings rating-card state |

## Tools — QR

| Use case | Purpose |
| --- | --- |
| `GenerateQrBitmapUseCase` | Encode text/URL as a QR bitmap |
| `SaveQrPngUseCase` | Persist QR PNG for save/share |

## Tools — PDF toolkit

| Use case | Purpose |
| --- | --- |
| `InspectPdfUseCase` | Probe device or library PDF metadata |
| `MergePdfsUseCase` | Merge multiple PDFs |
| `CompressPdfUseCase` | Re-encode with quality presets |
| `SetPdfPasswordUseCase` | Protect with open password |
| `RemovePdfPasswordUseCase` | Remove existing password |
| `WatermarkPdfUseCase` | Apply text watermark |
| `RenderWatermarkPreviewUseCase` | Debounced first-page watermark proof |
| `RenderPdfPageUseCase` | Render a page bitmap for reader/preview |
| `PreparePdfToolShareUseCase` | FileProvider share for tool outputs |

## Updates

| Use case | Purpose |
| --- | --- |
| `CheckForAppUpdateUseCase` | Delegate to the GitHub or Google Play repository selected by the build type |

## Related docs

- [../architecture/screens.md](../architecture/screens.md) — which ViewModels use which use cases
- [domain-models.md](domain-models.md) — models passed between layers
