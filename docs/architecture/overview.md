# Architecture Overview

How Scanly is structured at **v1.0.9**. For navigation detail see [navigation.md](navigation.md). For screen inventory see [screens.md](screens.md).

## Layer diagram

```
┌─────────────────────────────────────────────────────────┐
│  feature/          Compose screens + ViewModels         │
├─────────────────────────────────────────────────────────┤
│  domain/           Use cases + repository interfaces    │
│                    + domain models                      │
├─────────────────────────────────────────────────────────┤
│  data/             Room, files, export, settings,     │
│                    update implementations               │
├─────────────────────────────────────────────────────────┤
│  core/             ML, OpenCV, editing math, UI utils  │
└─────────────────────────────────────────────────────────┘
         ▲                              ▲
         │         di/ (Hilt)           │
         └──────────────────────────────┘
```

**Call direction:** `feature` → `domain` → `data` → `core`. Never skip layers.

## Application shell

| Component | File | Role |
| --- | --- | --- |
| Application | `ScanlyApplication.kt` | `@HiltAndroidApp` entry |
| Activity | `MainActivity.kt` | Onboarding gate, theme, NavHost, update dialog |
| Theme | `ui/theme/` | `ScanlyTheme`, colors, typography |
| Navigation | `navigation/ScanlyNavHost.kt` | Route registration, bottom nav / rail |

`MainActivity` hosts three top-level ViewModels:

- `AppSettingsViewModel` — observes theme mode
- `OnboardingViewModel` — first-run gate
- `AppUpdateViewModel` — update checks and dialog state

## Package layout

All code under `app/src/main/java/in/c1ph3rj/scanly/`:

| Package | Responsibility |
| --- | --- |
| `ui/theme/` | Material 3 theming |
| `navigation/` | Destinations and NavHost |
| `feature/` | Screen UI and ViewModels |
| `domain/model/` | Business models |
| `domain/repository/` | Repository contracts |
| `domain/usecase/` | Business operations (39 classes) |
| `domain/processing/` | `PageImageProcessor` interface |
| `data/local/db/` | Rebuildable Room operational index, entities, DAOs |
| `data/library/` | SAF access, versioned manifests, shared assets, mutation coordination, startup sync |
| `data/document/` | Document repository |
| `data/page/` | Page repository and capture finalize |
| `data/group/` | Group repository |
| `data/export/` | PDF/ZIP export |
| `data/storage/` | Cover-art utility retained for non-library presentation only |
| `data/settings/` | DataStore and bundled assets |
| `data/update/` | Build-selected GitHub or Google Play update checks |
| `data/processing/` | `PageImageProcessor` implementation |
| `core/ml/` | LiteRT corner detection |
| `core/processing/` | Perspective math, OpenCV filters |
| `core/editing/` | Crop quad editor logic |
| `core/ui/` | Thumbnail cache, layout helpers |
| `core/common/` | Result types, formatters |
| `di/` | Hilt modules |

## Design principles

1. **Offline-first** — core flows use a local SAF folder and need no network.
2. **Non-destructive captures** — `raw/` files are immutable.
3. **Derived processing** — processed images and thumbnails regenerate on edit.
4. **Manual fallback** — crop, rotate, filter always available; `NEEDS_REVIEW` state for failures.
5. **Database-first runtime** — screens observe Room only after splash synchronization.
6. **Recoverable persistence** — versioned shared manifests and assets reconstruct Room after reinstall.
7. **Testable boundaries** — use cases encapsulate business rules; repositories encapsulate I/O.

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
| `ExportModule` | `DocumentExportRepository` |
| `SettingsModule` | `SettingsRepository` |
| `AppDataModule` | `AppDataRepository` |
| `ProcessingModule` | `PageImageProcessor` |
| `MlModule` | `DocumentCornerDetector` → `LiteRtDocumentCornerDetector` |
| `AppUpdateModule` | Shared update notes, Play coordinator, and prompt storage |
| `DistributionAppUpdateModule` | Build-type-specific `AppUpdateRepository` binding |
| `CoroutineModule` | `ScanlyDispatchers` |

## Connection maps

### Camera capture

```
ScanSessionViewModel
  → PreparePageCaptureUseCase → PageRepository.prepareCapture
  → CameraX writes raw JPEG
  → FinalizeCapturedPageUseCase → PageRepository.finalizeCapture
    → PageImageProcessor → LiteRT detect + OpenCV filter
    → Room + file storage
```

### Page edit

```
PageEditorViewModel
  → UpdatePageEditsUseCase → PageRepository.updatePageEdits
    → PageImageProcessor.reprocessPage
    → Room update + thumbnail invalidation
```

### Export

```
DocumentDetailViewModel / GroupDetailViewModel
  → Export*UseCase → DocumentExportRepository
    → Room (page paths) + PdfDocument (+ PdfBox-Android encryption when requested) → cache/exports
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

Camera hardware is optional (`android:required="false"`). No `REQUEST_INSTALL_PACKAGES`.

FileProvider: `${applicationId}.fileprovider` for export/share URIs.

## Related docs

- [navigation.md](navigation.md) — routes and user flows
- [screens.md](screens.md) — screen and ViewModel table
- [../data/database.md](../data/database.md) — Room schema
- [../processing/](../processing/) — capture and image processing
