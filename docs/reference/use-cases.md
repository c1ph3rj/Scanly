# Use Cases

All **73** use case classes under `domain/usecase/` (including `pdftools/` and `qr/`), grouped by area.

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

## Library backup and restore

| Use case | Purpose |
| --- | --- |
| `EstimateLibraryBackupUseCase` | Calculate source, required, and available destination bytes |
| `StartLibraryBackupUseCase` | Enqueue a unique background `.scanly` backup |
| `StartLibraryRestoreUseCase` | Enqueue validated Replace or Merge restore |
| `ObserveLibraryArchiveWorkUseCase` | Observe WorkManager phase and progress |
| `CancelLibraryArchiveWorkUseCase` | Cancel the active backup or restore |

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
| `LoadSettingsContentUseCase` | Load FAQs and licenses |
| `ObserveOnboardingCompletedUseCase` | Flow of onboarding flag |
| `CompleteOnboardingUseCase` | Mark onboarding done |
| `GetAppStorageUsageUseCase` | Calculate storage byte counts |
| `ClearAllAppDataUseCase` | Wipe all local data |

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
