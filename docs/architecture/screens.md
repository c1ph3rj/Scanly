# Screens and ViewModels

Every feature screen in Scanly **v1.0.12** and its responsibilities.

## Screen inventory

| Feature | Screen | ViewModel | Route | Primary responsibilities |
| --- | --- | --- | --- | --- |
| Onboarding | `OnboardingScreen` | `OnboardingViewModel` | (gate in MainActivity) | First-run intro; persist completion |
| Home | `HomeScreen` | `HomeViewModel` | `home` | Recent docs/groups, scan, import, suggest names, library shortcut |
| Widgets / shortcuts | (system chrome) | `LaunchActionViewModel` | via `MainActivity` intents | Scan, Import, QR, Library redirects |
| Library | `LibraryScreen` | `LibraryViewModel` | `library` | Search, filter pills, sort, document/group CRUD, suggest names |
| Tools hub | `ToolsScreen` | `ToolsViewModel` | `tools` | Workflow-focused tools workspace; scan/import, QR, and PDF utility entry points |
| QR tool | `QrToolScreen` | `QrToolViewModel` | `tools/qr` | Camera QR/barcode scan; generate QR PNG save/share |
| PDF reader | `PdfReaderRoute` | `PdfReaderViewModel` | `tools/pdf/reader` | Page-by-page or continuous viewer for device, library, or app-generated result PDFs |
| PDF merge | `PdfMergeRoute` | `PdfMergeViewModel` | `tools/pdf/merge` | Merge multiple PDFs |
| PDF compress | `PdfCompressRoute` | `PdfCompressViewModel` | `tools/pdf/compress` | Quality presets while editing; focused `PdfToolCompleteScreen` with size-savings detail when done |
| PDF password | `PdfPasswordRoute` | `PdfPasswordViewModel` | `tools/pdf/password` | First-page preview, Protect/Remove cards, focused `PdfToolCompleteScreen` when done |
| PDF watermark | `PdfWatermarkRoute` | `PdfWatermarkViewModel` | `tools/pdf/watermark` | Debounced first-page proof via the production engine; tiled/single layout, size presets, orientation, opacity, and page coverage |
| Group detail | `GroupDetailScreen` | `GroupDetailViewModel` | `group/{groupId}` | Membership, rename, delete, group export, create doc in group |
| Document detail | `DocumentDetailScreen` | `DocumentDetailViewModel` | `document/{documentId}` | Pages, reorder, rename, import, export/save, move to group |
| Scan session | `ScanSessionScreen` | `ScanSessionViewModel` | `camera/session/{docId}` | CameraX, gate + multi-model overlay, stability, auto-capture, finalize |
| Page preview | `PageImagePreviewScreen` | `PageImagePreviewViewModel` | `preview/page/{pageId}` | Image-only paging, zoom, share/edit/retake/delete overflow |
| Page editor | `PageEditorScreen` | `PageEditorViewModel` | `editor/page/{pageId}` | Opens filter/adjust/crop; retake, delete |
| Filter picker | `FilterPickerScreen` | (shares `PageEditorViewModel`) | (editor overlay) | Full-screen live preview + preset chips |
| Filter adjust | `FilterCustomizeScreen` | (shares `PageEditorViewModel`) | (editor overlay) | Brightness/contrast/saturation/sharpness sliders |
| Page crop | `PageCropScreen` | `PageCropViewModel` | `crop/page/{pageId}` | AI detect, rotate, four-point crop handles, reset, apply |
| Settings | `SettingsScreen` | `SettingsViewModel` | `settings` | Look & feel, document detection, links, manual update check |
| Storage & backup | `StorageBackupScreen` | `SettingsViewModel` | `settings/storage` | Destination, usage, backup/restore progress, clear data |
| Model benchmark | `ModelBenchmarkRoute` | `ModelBenchmarkViewModel` | `settings/model-benchmark` | Per-image overview with polygon overlays on scaled previews for each corner model, plus gate/pipeline stats |
| FAQs | `SettingsFaqScreen` | `SettingsViewModel` | `settings/faq` | Bundled FAQ content |
| Licenses | `SettingsLicensesScreen` | `SettingsViewModel` | `settings/licenses` | Third-party license list |
| Legal | `LegalDocumentScreen` | — | `legal/{documentType}` | Privacy / terms WebView content |
| App update | `AppUpdateDialog` | `AppUpdateViewModel` | (overlay) | Channel-specific update check, cooldown |
| Flexible update | `FlexibleUpdateSnackbar` | `AppUpdateViewModel` | (overlay) | Restart prompt after Play flexible download |
| Placeholder | `FeaturePlaceholderScreen` | — | `camera`, `review`, `editor` | Legacy stubs — do not extend |

## App-level ViewModels

Hosted in `MainActivity`, not tied to a single screen:

| ViewModel | Role |
| --- | --- |
| `AppSettingsViewModel` | Observes theme mode and pure black preference globally |
| `OnboardingViewModel` | Tracks onboarding completion state |
| `AppUpdateViewModel` | Automatic and manual update checks; dialog and snackbar state |

## ViewModel → use case mapping (key screens)

### HomeViewModel

- `ObserveRecentDocumentsUseCase`, `ObserveRecentGroupsUseCase`
- `CreateDocumentUseCase`, `CreateGroupUseCase`
- `SuggestDocumentTitleUseCase`, `SuggestGroupTitleUseCase`
- `ImportImagesUseCase`

### LibraryViewModel

- `ObserveDocumentsUseCase`, `ObserveGroupsUseCase`, `ObserveUngroupedDocumentsUseCase`
- `CreateDocumentUseCase`, `DeleteDocumentUseCase`, `RenameDocumentUseCase`
- `CreateGroupUseCase`, `DeleteGroupUseCase`, `RenameGroupUseCase`
- `SuggestDocumentTitleUseCase`, `SuggestGroupTitleUseCase`

### DocumentDetailViewModel

- `ObserveDocumentUseCase`, `ObserveDocumentPagesUseCase`
- `RenameDocumentUseCase`, `DeletePageUseCase`, `MovePageUseCase`
- `SetDocumentGroupUseCase`, `ImportImagesUseCase`, `SuggestGroupTitleUseCase`
- `ExportDocumentPdfUseCase`, `ExportDocumentImageArchiveUseCase`
- `SaveExportArtifactUseCase`
- `PrepareDocumentPdfShareUseCase`, `PrepareDocumentImageShareUseCase`

### ScanSessionViewModel

- `PreparePageCaptureUseCase`, `PrepareReplacementCaptureUseCase`
- `FinalizeCapturedPageUseCase`
- Observes live model / automatic selection / document gate preferences
- Uses `DocumentGateDetector`, `DocumentCornerDetector`, `StableCornerSelector`, `DocumentGateStabilityTracker`, and `CaptureStabilityTracker` for live overlay and auto-capture

### PageEditorViewModel

- `ObservePageUseCase`, `UpdatePageEditsUseCase`, `DeletePageUseCase`
- Filter preset + per-page adjustments (brightness/contrast/saturation/sharpness) and page delete; adopts crop/rotation updates after the crop screen applies

### PageCropViewModel

- `ObservePageUseCase`, `UpdatePageEditsUseCase`, `DetectDocumentCornersUseCase`
- Uses `CropQuadEditor` for rotation, interactive crop handles, and session reset
- **AI Detect** runs still-image corner detection (same post-processing path as capture)

### PageImagePreviewViewModel

- `ObservePageUseCase`, `ObserveDocumentPagesUseCase`
- `DeletePageUseCase`
- Share uses processed image path via FileProvider

### GroupDetailViewModel

- `ObserveGroupUseCase`, `ObserveGroupDocumentsUseCase`
- `RenameGroupUseCase`, `DeleteGroupUseCase`, `SetDocumentGroupUseCase`
- `SuggestDocumentTitleUseCase`, `CreateDocumentUseCase`
- `ExportGroupPdfUseCase`, `ExportGroupZippedPdfsUseCase`
- `PrepareGroupPdfShareUseCase`, `PrepareGroupZippedPdfsShareUseCase`
- `SaveExportArtifactUseCase`

### SettingsViewModel

- `ObserveThemeModeUseCase`, `SetThemeModeUseCase`
- `ObservePureBlackEnabledUseCase`, `SetPureBlackEnabledUseCase`
- Live / post model observe+set, automatic selection, document gate use cases
- `LoadSettingsContentUseCase`, `GetAppStorageUsageUseCase`
- `ObserveExportDestinationUseCase`, `SetExportDestinationUseCase`, `ResetExportDestinationUseCase`
- `EstimateLibraryBackupUseCase`, `StartLibraryBackupUseCase`, `StartLibraryRestoreUseCase`
- `ObserveLibraryArchiveWorkUseCase`, `CancelLibraryArchiveWorkUseCase`
- `ClearAllAppDataUseCase`
- `AutomaticDocumentModelSelector` for calibrated automatic picks
- Triggers `AppUpdateViewModel.checkForUpdates(Manual)`

### ModelBenchmarkViewModel

- Injects `DocumentCornerDetector` and `DocumentGateDetector` directly for temporary local runs
- Does not share `SettingsViewModel` (results are ephemeral)

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
| `AdaptiveLayout.kt` | Phone vs tablet / width-class helpers |
| `ZoomableImageDialog.kt` | Pinch-zoom preview (document detail) |
| `ZoomableImageViewer.kt` | Pinch-zoom viewer (page preview) |

### Large-screen editor layout

On tablet landscape (and other wide landscape windows that opt into tool two-pane), editor flows share `EditorLandscapeChrome`:

| Screen | Left pane | Right pane |
| --- | --- | --- |
| Page editor | Live cropped preview | Vertical tool rail (Crop, Filters, Adjust, Retake, Delete) |
| Page crop | Interactive crop canvas | Vertical rail (AI Detect, rotate, reset) |
| Filters | Live filter preview | Scope switch + 2-column preset grid |
| Adjust | Live adjusted preview | Scrollable sliders |

Phone portrait keeps the stacked preview-above-controls layout.

Editor package helpers (`feature/editor/`):

| File | Role |
| --- | --- |
| `EditorImageSupport.kt` | Live preview: decode, crop, filter, adjustments |
| `EditorScrollbars.kt` | Shared vertical scrollbar for Filters/Adjust panes |
| `FilterPickerScreen.kt` | Full-screen filter preset picker |
| `FilterCustomizeScreen.kt` | Full-screen filter fine-tuning |
| `PageCropScreen.kt` / `PageCropViewModel.kt` | Dedicated crop route |

## Scan session internals

Classes supporting `ScanSessionScreen` (not separate screens):

| Class | Role |
| --- | --- |
| `CaptureFrameQualityAnalyzer` | Lighting, blur, obstruction feedback |
| `CaptureStabilityTracker` | Auto-capture phase machine (`AutoCapturePhase`); worst-corner motion |
| `DocumentGateStabilityTracker` | Requires consecutive accepted gate frames before corners run |
| `StableCornerSelector` | Rank, confirm, and smooth the visible outline |
| `CameraOverlayMapper` | Maps ML quad to overlay coordinates |

## Related docs

- [navigation.md](navigation.md) — routes and flow diagrams
- [../reference/use-cases.md](../reference/use-cases.md) — full use case list
- [../overview/features.md](../overview/features.md) — user-facing feature descriptions
