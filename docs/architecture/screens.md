# Screens and ViewModels

Every feature screen in Scanly **v1.0.18** and its responsibilities.

## Screen inventory

| Feature | Screen | ViewModel | Route | Primary responsibilities |
| --- | --- | --- | --- | --- |
| Onboarding | `OnboardingScreen` | `OnboardingViewModel` | (gate in MainActivity) | First-run intro, then a camera-permission step when the camera is not yet allowed; persist completion |
| Home | `HomeScreen` | `HomeViewModel` | `home` | Recent folders and documents (shimmer placeholders until the first load), scan/import/folder, suggest names, daily Pip tip, library shortcut |
| Widgets / shortcuts | (system chrome) | `LaunchActionViewModel` | via `MainActivity` intents | Scan, Import, QR, Library redirects |
| Library | `LibraryScreen` | `LibraryViewModel` | `library` | Paper cards, overflow/long-press actions, search, filter pills, sort, CRUD, suggest names |
| Library search | `LibrarySearchScreen` | `LibrarySearchViewModel` | `library/search` | Search names and page text, recent searches, type filters, ranked excerpts |
| Extracted text | `PageTextScreen` | `PageTextViewModel` | `document/{documentId}/text` | Read or correct recognized text; copy/share; show text regions on the scan |
| Tools hub | `ToolsScreen` | `ToolsViewModel` | `tools` | Scan / PDF / Utilities groups; Utilities: QR & barcode (opens straight into scanning), Image to text, My signatures |
| Image to text | `ImageTextRoute` (reuses `PdfFilePickerScreen`) | `ImageTextViewModel` | `tools/text` | Photos → new document → Extracted text; scans open their Extracted text directly |
| QR tool | `QrToolScreen` (+ `QrScanScreen`, `QrResultScreen`, `QrCreateScreen`, `QrHistoryScreen`) | `QrToolViewModel` | `tools/qr` | Scan with torch and photo import, typed results, Create types with caption and ink, on-phone history |
| PDF reader | `PdfReaderRoute` | `PdfReaderViewModel` | `tools/pdf/reader` | Picker step, dark reader with one page counter, hideable chrome, Pages grid, Layout; ⋮ menu (`PdfReaderOverflowMenu`) with Share, All pages, hand-off to each PDF tool, Open another PDF; also the target of Open in Scanly |
| PDF merge | `PdfMergeRoute` | `PdfMergeViewModel` | `tools/pdf/merge` | Multi-select picker → order & name → done |
| PDF compress | `PdfCompressRoute` | `PdfCompressViewModel` | `tools/pdf/compress` | Picker → quality → working → done with saving and before/after |
| PDF password | `PdfPasswordRoute` | `PdfPasswordViewModel` | `tools/pdf/password` | Picker → Add password (strength, restrict editing) or Remove password, chosen by the file → done |
| PDF watermark | `PdfWatermarkRoute` | `PdfWatermarkViewModel` | `tools/pdf/watermark` | Picker → editor with pinned live preview (presets, date, style, colour, pages) → done |
| PDF to images | `PdfToImagesRoute` | `PdfToImagesViewModel` | `tools/pdf/to-images` | Picker → pages, JPG/PNG, Sharing or Print size → images in `Pictures/Scanly`, share all or some |
| PDF signing | `PdfSignRoute` | `PdfSignViewModel` | `tools/pdf/sign` | Pick a PDF, place marks across pages, review, save or share a new signed PDF |
| Scan signing | `DocumentSignRoute` + `SignEditorScreen` | `DocumentSignViewModel` | `document/{documentId}/sign` | Place editable marks on scan pages; update page composites and thumbnails |
| Saved signatures | `SignatureManageRoute` | `SignatureManageViewModel` | `signatures/manage` | Review and delete saved signatures or initials |
| Create signature | `SignatureCreateRoute` | `SignatureCreateViewModel` | `signatures/create?kind={kind}` | Draw a signature/initials or extract ink from an imported photo |
| Group detail | `GroupDetailScreen` | `GroupDetailViewModel` | `group/{groupId}` | Membership (Add documents sheet with cover thumbnails and edited dates), rename, delete, group export, create doc in group |
| Document detail | `DocumentDetailScreen` | `DocumentDetailViewModel` | `document/{documentId}` | Portrait page tiles, tap-to-review, long-press reorder, rename, import, export/save, move to group |
| Scan session | `ScanSessionScreen` | `ScanSessionViewModel` | `camera/session/{docId}` | Camera permission (ask first), CameraX, gate + multi-model overlay, stability, auto-capture, finalize |
| Page preview | `PageImagePreviewScreen` | `PageImagePreviewViewModel` | `preview/page/{pageId}` | Image-only paging, zoom, share/edit/retake/delete overflow |
| Page editor | `PageEditorScreen` | `PageEditorViewModel` | `editor/page/{pageId}` | Opens filter/adjust/crop; retake, delete |
| Filter picker | `FilterPickerScreen` | (shares `PageEditorViewModel`) | (editor overlay) | Full-screen live preview + preset chips |
| Filter adjust | `FilterCustomizeScreen` | (shares `PageEditorViewModel`) | (editor overlay) | Brightness/contrast/saturation/sharpness sliders |
| Page crop | `PageCropScreen` | `PageCropViewModel` | `crop/page/{pageId}` | AI detect, rotate, four-point crop handles, reset, apply |
| Settings | `SettingsScreen` | `SettingsViewModel` | `settings` | Look & feel, document detection, links, manual update check |
| Pip settings | `SettingsPipScreen` | `PipViewModel` | `settings/pip` | Pip master switch, Home tips, camera coaching, export cheers, animation |
| Storage | `SettingsStorageScreen` | `SettingsViewModel` | `settings/storage` | Usage, export destination, clear temporary exports, clean empty items, clear data |
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
- `ClearExportCacheUseCase`, `DeleteEmptyDocumentsUseCase`, `DeleteEmptyGroupsUseCase`, `ClearAllAppDataUseCase`
- `ObserveRatingPromptStateUseCase`, `DismissSettingsRatingCardUseCase`, `MarkAppRatedUseCase`
- `AutomaticDocumentModelSelector` for calibrated automatic picks
- Triggers `AppUpdateViewModel.checkForUpdates(Manual)`

### LibrarySearchViewModel and PageTextViewModel

- Search combines document/folder name results with on-device page-text results, recent searches, ranking, filters, and correction suggestions.
- `PageTextViewModel` loads extracted page text, saves corrections, and prepares page/all-page copy and share actions.

### Signing ViewModels

- `PdfSignViewModel` opens a PDF signing session, manages marks by page, and saves the signed PDF as a separate output.
- `DocumentSignViewModel` loads and saves editable markups for a Scanly document; `SignEditorScreen` handles mark placement and editing.
- `SignatureCreateViewModel` saves drawn/imported signature images; `SignatureManageViewModel` observes and deletes them.

### ModelBenchmarkViewModel

- Injects `DocumentCornerDetector` and `DocumentGateDetector` directly for temporary local runs
- Does not share `SettingsViewModel` (results are ephemeral)

## Shared UI components

`feature/components/`:

| File | Purpose |
| --- | --- |
| `SharedComponents.kt` | Paper document/group cards, overflow/long-press actions, thumbnails |
| `FabComponents.kt` | FAB menus for create/scan/import |
| `ExportShareComponents.kt` | Export and share bottom sheets |

`core/ui/`:

| File | Purpose |
| --- | --- |
| `ScanlyChrome.kt` | Top bars, shared chrome |
| `ThumbnailCache.kt` | In-memory thumbnail cache |
| `PreviewImageSizer.kt` | Consistent preview dimensions |
| `ImageImportSupport.kt` | System file picker for images (50 image limit) |
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
