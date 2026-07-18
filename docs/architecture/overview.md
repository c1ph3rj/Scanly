# Architecture Overview

How Scanly is structured at **v1.0.11**. For navigation detail see [navigation.md](navigation.md). For screen inventory see [screens.md](screens.md).

## Layer diagram

```
┌─────────────────────────────────────────────────────────┐
│  feature/          Compose screens + ViewModels         │
├─────────────────────────────────────────────────────────┤
│  domain/           Use cases + repository interfaces    │
│                    + domain models                      │
├─────────────────────────────────────────────────────────┤
│  data/             Room, files, export, settings,       │
│                    update implementations               │
├─────────────────────────────────────────────────────────┤
│  core/             ML, OpenCV, editing math, UI utils   │
└─────────────────────────────────────────────────────────┘
         ▲                              ▲
         │         di/ (Hilt)           │
         └──────────────────────────────┘
```

**Call direction:** `feature` → `domain` → `data` → `core`. Never skip layers.

## Application shell

| Component | File | Role |
| --- | --- | --- |
| Application | `ScanlyApplication.kt` | `@HiltAndroidApp`; custom WorkManager + `HiltWorkerFactory` |
| Activity | `MainActivity.kt` | Onboarding gate, theme, NavHost, update dialog, widget/shortcut launch actions |
| Theme | `ui/theme/` | `ScanlyTheme`, colors, typography |
| Navigation | `navigation/ScanlyNavHost.kt` | Route registration, bottom nav / rail |

`MainActivity` hosts three top-level ViewModels:

- `AppSettingsViewModel` — observes theme mode and pure black preference
- `OnboardingViewModel` — first-run gate
- `AppUpdateViewModel` — update checks and dialog state
- `LaunchActionViewModel` — widget/shortcut actions (scan, import, QR, library)

## Package layout

All code under `app/src/main/java/in/c1ph3rj/scanly/`:

| Package | Responsibility |
| --- | --- |
| `ui/theme/` | Material 3 theming |
| `navigation/` | Destinations and NavHost |
| `feature/` | Screen UI and ViewModels |
| `feature/launch/` | Widget/shortcut launch action parsing and redirects |
| `feature/widget/` | App widget providers (actions, scan, QR) |
| `domain/model/` | Business models |
| `domain/repository/` | Repository contracts |
| `domain/usecase/` | Business operations (73 use case classes) |
| `domain/processing/` | `PageImageProcessor` interface |
| `data/local/db/` | Room database, entities, DAOs |
| `data/document/` | Document repository |
| `data/page/` | Page repository and capture finalize |
| `data/group/` | Group repository |
| `data/export/` | PDF/ZIP export |
| `data/archive/` | Versioned library backup/restore, workers, operation coordination |
| `data/storage/` | App-private file manager |
| `data/settings/` | DataStore and bundled assets |
| `data/update/` | Build-selected GitHub or Google Play update checks |
| `data/processing/` | `PageImageProcessor` implementation |
| `core/ml/` | LiteRT corner models, document gate, book/quad policies, auto model selection |
| `core/processing/` | Perspective warp, OpenCV filters, filter adjustments |
| `core/editing/` | Crop quad editor logic |
| `core/ui/` | Thumbnail cache, adaptive layout helpers |
| `core/common/` | Result types, formatters |
| `di/` | Hilt modules |

## Design principles

1. **Offline-first** — core flows need no network.
2. **Non-destructive captures** — `raw/` files are immutable.
3. **Derived processing** — processed images and thumbnails regenerate on edit.
4. **Manual fallback** — AI Detect, crop, rotate, filters, and fine adjustments always available; `NEEDS_REVIEW` state for failures.
5. **Testable boundaries** — use cases encapsulate business rules; repositories encapsulate I/O.

## Layer rules

| Layer | May call | Must not call |
| --- | --- | --- |
| `feature/` | `domain/usecase/`, `core/ui/` | Room DAOs, DataStore, filesystem |
| `domain/` | Repository interfaces | Android framework, Room, Compose |
| `data/` | Room, files, network, `core/` | Compose, ViewModels |
| `core/` | Other `core/` utilities | Feature screens, ViewModels |

## Dependency injection

Hilt modules in `di/` install into `SingletonComponent`:

| Module | Provides / binds |
| --- | --- |
| `DatabaseModule` | `ScanlyDatabase`, DAOs, migrations |
| `DocumentDataModule` | Document, page, group repos; storage manager |
| `ExportModule` | `DocumentExportRepository`, `ExportStorageRepository` |
| `ArchiveModule` | `LibraryArchiveRepository` |
| `SettingsModule` | `SettingsRepository` |
| `AppDataModule` | `AppDataRepository` |
| `ProcessingModule` | `PageImageProcessor` |
| `PdfToolkitModule` | PDF toolkit repository |
| `MlModule` | `DocumentCornerDetector` → `LiteRtDocumentCornerDetector`; `DocumentGateDetector` → `LiteRtDocumentGateDetector` |
| `AppUpdateModule` | Shared update notes, Play coordinator, and prompt storage |
| `DistributionAppUpdateModule` | Build-type-specific `AppUpdateRepository` binding |
| `CoroutineModule` | `ScanlyDispatchers` |

## Connection maps

### Camera capture

```
ScanSessionViewModel
  → live path: DocumentGateDetector → DocumentCornerDetector(live model)
    → DocumentQuadPolicy / StableCornerSelector → overlay + auto-capture
  → PreparePageCaptureUseCase → PageRepository.prepareCapture
  → CameraX writes raw JPEG
  → FinalizeCapturedPageUseCase → PageRepository.finalizeCapture
    → PageImageProcessor → gate + corner (post model) + warp + OpenCV filter (+ adjustments if any)
    → Room + file storage
```

### Page edit

```
PageEditorViewModel
  ├─ Filters / Adjust overlays (same ViewModel drafts)
  └─ UpdatePageEditsUseCase → PageRepository.updatePageEdits
        → PageImageProcessor.reprocessPage (crop + rotation + filter + adjustments)
        → Room update + thumbnail invalidation

PageCropViewModel
  ├─ DetectDocumentCornersUseCase → PageImageProcessor.detectDocumentCorners
  └─ UpdatePageEditsUseCase (apply crop + rotation; keep filter/adjustments)
```

### Export

```
DocumentDetailViewModel / GroupDetailViewModel
  → Export*UseCase → DocumentExportRepository
    → Room (page paths) + PdfDocument (+ PdfBox-Android encryption when requested) → cache/exports
  → SaveExportArtifactUseCase → configured Downloads/SAF destination
```

### Library backup and restore

```text
SettingsViewModel
  → LibraryArchiveRepository → unique foreground WorkManager job
    → LibraryArchiveEngine
      → free-space preflight + mutation coordinator
      → versioned manifest + exact files + SHA-256 → destination/backup/*.scanly
      → validate + stage → transactional Replace or Merge with rewritten app-private paths
```

### Clear all data

```
SettingsViewModel
  → ClearAllAppDataUseCase → AppDataRepository
    → Room clear + files wipe + cache wipe + ThumbnailCache.clearAll
```

## Permissions

| Permission | Purpose |
| --- | --- |
| `CAMERA` | Document capture |
| `INTERNET` | Google Play update check, GitHub release notes |
| `POST_NOTIFICATIONS` | Background archive progress on Android 13+ |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Long-running backup and restore |

Camera hardware is optional (`android:required="false"`). No `REQUEST_INSTALL_PACKAGES`.

FileProvider: `${applicationId}.fileprovider` for export/share URIs.

## Related docs

- [navigation.md](navigation.md) — routes and user flows
- [screens.md](screens.md) — screen and ViewModel table
- [../data/database.md](../data/database.md) — Room schema
- [../processing/](../processing/) — capture and image processing
