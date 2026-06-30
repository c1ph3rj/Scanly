# Screens and ViewModels

Every feature screen in Scanly **v1.0.9** and its responsibilities.

## Screen inventory

| Feature | Screen | ViewModel | Route | Primary responsibilities |
| --- | --- | --- | --- | --- |
| Onboarding | `OnboardingScreen` | `OnboardingViewModel` | (gate in MainActivity) | First-run intro; persist completion |
| Home | `HomeScreen` | `HomeViewModel` | `home` | Recent docs/groups, scan, import, library shortcut |
| Library | `LibraryScreen` | `LibraryViewModel` | `library` | Search, tabs, sort, document/group CRUD |
| Group detail | `GroupDetailScreen` | `GroupDetailViewModel` | `group/{groupId}` | Membership, rename, delete, group export |
| Document detail | `DocumentDetailScreen` | `DocumentDetailViewModel` | `document/{documentId}` | Pages, reorder, rename, import, export |
| Scan session | `ScanSessionScreen` | `ScanSessionViewModel` | `camera/session/{docId}` | CameraX, live guidance, finalize |
| Page preview | `PageImagePreviewScreen` | `PageImagePreviewViewModel` | `preview/page/{pageId}` | Swipeable page review |
| Page editor | `PageEditorScreen` | `PageEditorViewModel` | `editor/page/{pageId}` | Crop, rotate, filters, retake |
| Settings | `SettingsScreen` | `SettingsViewModel` | `settings` | Theme, storage, clear data, FAQs |
| Legal | `LegalDocumentScreen` | — | `legal/{documentType}` | Privacy / licenses content |
| App update | `AppUpdateDialog` | `AppUpdateViewModel` | (overlay) | Build-selected GitHub or Google Play update check, cooldown |
| Placeholder | `FeaturePlaceholderScreen` | — | `camera`, `review`, `editor` | Legacy stubs — do not extend |

## App-level ViewModels

Hosted in `MainActivity`, not tied to a single screen:

| ViewModel | Role |
| --- | --- |
| `AppSettingsViewModel` | Observes and applies theme mode globally |
| `OnboardingViewModel` | Tracks onboarding completion state |
| `AppUpdateViewModel` | Automatic and manual update checks; dialog state |

## ViewModel → use case mapping (key screens)

### HomeViewModel

- `ObserveRecentDocumentsUseCase`, `ObserveRecentGroupsUseCase`
- `CreateDocumentUseCase`, `CreateGroupUseCase`
- `ImportImagesUseCase`

### LibraryViewModel

- `ObserveDocumentsUseCase`, `ObserveGroupsUseCase`, `ObserveUngroupedDocumentsUseCase`
- `CreateDocumentUseCase`, `DeleteDocumentUseCase`, `RenameDocumentUseCase`
- `CreateGroupUseCase`, `DeleteGroupUseCase`, `RenameGroupUseCase`

### DocumentDetailViewModel

- `ObserveDocumentUseCase`, `ObserveDocumentPagesUseCase`
- `RenameDocumentUseCase`, `DeletePageUseCase`, `MovePageUseCase`
- `SetDocumentGroupUseCase`, `ImportImagesUseCase`
- `ExportDocumentPdfUseCase`, `ExportDocumentImageArchiveUseCase`
- `PrepareDocumentPdfShareUseCase`, `PrepareDocumentImageShareUseCase`

### ScanSessionViewModel

- `PreparePageCaptureUseCase`, `PrepareReplacementCaptureUseCase`
- `FinalizeCapturedPageUseCase`
- Uses `DocumentCornerDetector` for live overlay (injected via processing stack)

### PageEditorViewModel

- `ObservePageUseCase`, `UpdatePageEditsUseCase`
- Uses `CropQuadEditor` for interactive crop handles

### GroupDetailViewModel

- `ObserveGroupUseCase`, `ObserveGroupDocumentsUseCase`
- `RenameGroupUseCase`, `DeleteGroupUseCase`, `SetDocumentGroupUseCase`
- `ExportGroupPdfUseCase`, `ExportGroupZippedPdfsUseCase`
- `PrepareGroupPdfShareUseCase`, `PrepareGroupZippedPdfsShareUseCase`

### SettingsViewModel

- `ObserveThemeModeUseCase`, `SetThemeModeUseCase`
- `LoadSettingsContentUseCase`, `GetAppStorageUsageUseCase`
- `ClearAllAppDataUseCase`
- Triggers `AppUpdateViewModel.checkForUpdates(Manual)`

## Shared UI components

`feature/components/`:

| File | Purpose |
| --- | --- |
| `SharedComponents.kt` | Document/group cards, list items, thumbnails |
| `FabComponents.kt` | FAB menus for create/scan/import |
| `ExportShareComponents.kt` | Export and share bottom sheets |

`core/ui/`:

| File | Purpose |
| --- | --- |
| `ScanlyChrome.kt` | Top bars, shared chrome |
| `ThumbnailCache.kt` | In-memory thumbnail cache |
| `PreviewImageSizer.kt` | Consistent preview dimensions |
| `ImageImportSupport.kt` | Gallery picker (10 image limit) |
| `AdaptiveLayout.kt` | Phone vs tablet detection |
| `ZoomableImageDialog.kt` | Pinch-zoom preview |

## Scan session internals

Classes supporting `ScanSessionScreen` (not separate screens):

| Class | Role |
| --- | --- |
| `CaptureFrameQualityAnalyzer` | Lighting, blur, obstruction feedback |
| `CaptureStabilityTracker` | Auto-capture stability gating |
| `CameraOverlayMapper` | Maps ML quad to overlay coordinates |

## Related docs

- [navigation.md](navigation.md) — routes and flow diagrams
- [../reference/use-cases.md](../reference/use-cases.md) — full use case list
- [../overview/features.md](../overview/features.md) — user-facing feature descriptions
