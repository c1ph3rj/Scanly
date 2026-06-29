# Use Cases

All 39 use case classes in `domain/usecase/`, grouped by area.

ViewModels call use cases. Use cases call repository interfaces.

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

## Page lifecycle

| Use case | Purpose |
| --- | --- |
| `PreparePageCaptureUseCase` | Allocate paths for new page capture |
| `PrepareReplacementCaptureUseCase` | Allocate paths for page retake |
| `FinalizeCapturedPageUseCase` | Run processing and persist page |
| `ObservePageUseCase` | Flow of single page |
| `ObserveDocumentPagesUseCase` | Flow of pages in a document |
| `UpdatePageEditsUseCase` | Save crop/rotate/filter and reprocess |
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

## Settings and app data

| Use case | Purpose |
| --- | --- |
| `ObserveThemeModeUseCase` | Flow of current theme mode |
| `SetThemeModeUseCase` | Persist theme mode |
| `LoadSettingsContentUseCase` | Load FAQs and licenses |
| `ObserveOnboardingCompletedUseCase` | Flow of onboarding flag |
| `CompleteOnboardingUseCase` | Mark onboarding done |
| `GetAppStorageUsageUseCase` | Calculate storage byte counts |
| `ClearAllAppDataUseCase` | Wipe all local data |

## Updates

| Use case | Purpose |
| --- | --- |
| `CheckForAppUpdateUseCase` | Compare installed vs GitHub latest |

## Related docs

- [../architecture/screens.md](../architecture/screens.md) — which ViewModels use which use cases
- [domain-models.md](domain-models.md) — models passed between layers