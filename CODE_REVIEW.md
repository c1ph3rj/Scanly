# Scanly Android — Code Quality Review

| Field | Value |
| --- | --- |
| **Scope** | Full project (`feature/optimization` @ `master` tip `9393cd5`) |
| **Date** | 2026-07-20 |
| **Review type** | Structural maintainability / architecture health |
| **Module** | Single-module app (`app/`) under Clean Architecture layers |
| **Main Kotlin LOC** | ~40.5k |
| **Test Kotlin LOC** | ~2.3k (~5.7% of main) |

---

## Short version (read this first)

Scanly is a **well-architected document scanner** with clear layers (feature → domain → data → core), solid docs, Hilt DI, offline-first design, and careful capture/export flows. That foundation is real and worth protecting.

The main risk is **UI and feature surface area growing faster than modularization**:

1. **Giant Compose files** — eight files over 1,000 lines (largest ~2,000). Hard to change safely.
2. **Use cases are mostly pass-throughs** — ~57 classes that often only call one repository method. Indirection without business logic.
3. **Camera ViewModel skips domain** — live ML runs in the ViewModel via `core.ml`, against documented layer rules.
4. **Domain is not Android-free** — `ImportImagesUseCase` and PDF/QR contracts pull in `Context` / `Bitmap` / `Uri`.
5. **Copy-paste is spreading** — share intents, date formatters, PDF tool VMs, export sheets duplicated across screens.
6. **Tests miss the money paths** — good pure-logic tests; almost no coverage of repositories, ViewModels, or page finalize.
7. **Tools package is a second app** — ~5.8k LOC for PDF/QR tools with repeated state machines.

**Bottom line:** Keep shipping features, but **stop growing mega-screens**. Next work should split UI, collapse empty use cases (or make them real policies), pull live capture behind a domain service, and cover capture/edit/export with tests. Without that, every “optimization” PR will fight file size and duplication instead of improving product quality.

**Verdict:** Not approved as “healthy for unbounded growth.” Behavior and layering fundamentals are good; **structural simplification is the optimization priority.**

---

## What’s working well

These are strengths to preserve, not rewrite:

- **Documented architecture** (`docs/architecture/`, conventions) matches the package layout for most library flows.
- **Non-destructive capture model** (immutable `raw/`, regenerate `processed/` + thumbs) is the right product/engineering invariant.
- **`ScanlyResult` + use-case-shaped call sites** keep ViewModels free of Room/DAO.
- **Feature does not import `data/`** — repository boundary is respected for library CRUD.
- **Live capture helpers are testable** — `CaptureStabilityTracker`, `StableCornerSelector`, gate policies, quad math have unit tests.
- **Archive/export paths** show intentional complexity (coordinators, free-space preflight, WorkManager) rather than accidental complexity only.
- **Build variants** for update sources (GitHub vs Play) are cleanly modularized via source sets + Hilt.
- **Shared UI chrome** (`ScanlyDetailScaffold`, import progress overlay, theme) exists and is reused in places.

---

## Priority findings

Findings ordered by structural impact. Severity: **Blocker** (must address before large new features / “optimization” work lands as permanent shape) · **High** · **Medium** · **Low**.

---

### 1. Compose mega-files past the 1k-line line — Blocker

Eight production Kotlin files exceed **1,000 lines**:

| Lines | File |
| ---: | --- |
| 1967 | `feature/document/DocumentDetailScreen.kt` |
| 1798 | `feature/tools/pdf/PdfToolScreens.kt` |
| 1565 | `feature/components/SharedComponents.kt` |
| 1515 | `feature/camera/ScanSessionScreen.kt` |
| 1121 | `feature/tools/pdf/PdfToolShared.kt` |
| 1021 | `core/processing/OpenCvPageFilterProcessor.kt` |
| 1020 | `feature/tools/qr/QrToolScreen.kt` |
| 1017 | `feature/library/LibraryScreen.kt` |

Near-threshold and still too large to own as single units: `ScanlyNavHost` (940), `PageCropScreen` (908), `StorageBackupScreen` (860), `PdfToolViewModels` (810), `LibraryArchiveEngine` (765).

**Why this is structural, not cosmetic**

- `DocumentDetailScreen` alone holds route wiring, master-detail layout, drag-reorder math, export/share sheets, processing state chrome, FileProvider share helpers, and date formatters.
- `SharedComponents` is a junk drawer: cards, dialogs, sheets, thumbnails, progress overlays, title suggest rows — many unrelated reasons to change.
- `PdfToolScreens` packs merge / compress / password / watermark / reader into one compilation unit.

**Code-judo move**

Split by **user task**, not by “extract random private composable”:

```
feature/document/
  DocumentDetailRoute.kt
  DocumentDetailScreen.kt          # shell only
  DocumentPageGrid.kt
  DocumentPageReorder.kt
  DocumentExportUi.kt
  DocumentShareSupport.kt          # or move to feature/components once

feature/components/
  library/DocumentCard.kt
  library/GroupCard.kt
  dialogs/ScanlyDialogs.kt
  progress/ScanlyProgressOverlays.kt
  chrome/ScanlyScaffolds.kt

feature/tools/pdf/
  merge/ PdfMergeScreen.kt + PdfMergeViewModel.kt
  compress/
  password/
  watermark/
  reader/
  shared/PdfToolChrome.kt
```

**Rule for `feature/optimization`:** do not add net lines to any file already ≥900 lines. Decompose first, then optimize.

---

### 2. Thin use-case layer is mostly ceremony — High

Roughly **57** use-case files; most are one-liners:

```kotlin
// ObserveDocumentUseCase — entire body
operator fun invoke(documentId: String): Flow<ScanDocument?> =
    documentRepository.observeDocument(documentId)

// FinalizeCapturedPageUseCase — entire body
suspend operator fun invoke(draft: PageCaptureDraft): ScanlyResult<String> =
    pageRepository.finalizeCapture(draft)
```

PDF tools repeat the same pattern eight times in `PdfToolkitUseCases.kt`.

**Problem**

- Indirection without policy, validation, or composition.
- ViewModels inject **many** use cases (`DocumentDetailViewModel` ~19 deps, `SettingsViewModel` ~20) because each method became a class.
- Readers must jump feature → use case → repository for zero logic.

**Code-judo move (pick one direction and commit)**

| Option | When |
| --- | --- |
| **A. Collapse** observe/CRUD pass-throughs; inject repositories (or a narrow `DocumentQueries` facade) into ViewModels | Pure read/write with no rules |
| **B. Keep use cases only where they earn keep** | Multi-step orchestration, policy, multi-repo, progress (`ImportImagesUseCase`, archive start, export+save composition) |
| **C. Group by aggregate** | `DocumentCommands` / `DocumentQueries` instead of 15 single-method classes |

Prefer **B + C**. Do not keep 50 identity wrappers for ceremony.

---

### 3. Live capture logic lives in the wrong layer — High

Documented rules (`docs/development/conventions.md`):

| Layer | May call |
| --- | --- |
| `feature/` | `domain/usecase/`, `core/ui/` |

`ScanSessionViewModel` injects and runs:

- `BookAwareCornerResolver`
- `DocumentGateDetector`
- `AutomaticDocumentModelSelector`
- frame stability / quality helpers

…on the camera analysis path (`onPreviewFrame` → Default dispatcher → gate + corners + stability → auto-capture).

Meanwhile crop AI Detect is correctly routed through `DetectDocumentCornersUseCase` / `PageImageProcessor`.

**Problem**

- Two capture pipelines, two ownership models.
- ViewModel becomes a real-time ML orchestrator (~450+ lines) instead of a thin UI state holder.
- Harder to unit-test full analysis without Android/ViewModel harness.
- Contradicts the project’s own layer table.

**Code-judo move**

Introduce a domain-facing session analyzer:

```text
feature/camera/ScanSessionViewModel
  → domain: LiveDocumentAnalysisSession (interface)
      analyze(frame): LiveAnalysisSnapshot
      prepareCapture / finalizeCapture (existing use cases)
  → data or core implementation wires detectors + policies
```

ViewModel only maps snapshots → `LiveDetectionUiState` and triggers capture. Delete direct `core.ml` imports from feature.

---

### 4. Domain layer is not pure — High

Conventions say domain must not call Android framework. Actual:

| Location | Android types |
| --- | --- |
| `ImportImagesUseCase` | `Context`, `Uri`, `Bitmap`, `ImageDecoder` |
| `PdfToolkitRepository` + use cases | `Bitmap` |
| `QrCodeRepository` + use cases | `Bitmap` |

**Problem**

- Domain becomes untestable on JVM without Android stubs/Robolectric for critical import path.
- Real import/orchestration logic (~150 lines) belongs in `data/` (or a dedicated importer), not domain.

**Code-judo move**

- Domain: `ImportPagesCommand(documentId, rawSources: List<ImportSource>)` with a pure model (`ImportSource.LocalPath` / content ref resolved outside).
- Data: decode URI → write raw JPEG → `finalizeCapture`.
- PDF page bitmaps: either accept Android types only in data/feature, or use a thin `ImageBuffer` domain type with mapping at the edge.

---

### 5. Duplication and divergent “shared” helpers — High

Same concepts implemented multiple times:

| Concern | Locations |
| --- | --- |
| `sharePreparedFiles` + `exportUriFor` | `DocumentDetailScreen`, `PageImagePreviewScreen`, `PdfToolShared` |
| `toShortDate` | `SharedComponents` **and** private again in `DocumentDetailScreen` |
| `toReadableDateTime` | private in `DocumentDetailScreen` (presentation formatters already exist in `core/common`) |
| Export/share sheets | Document detail, group detail, PDF tools |
| PDF inspect + password + preview recycle | Copy-shaped blocks across `PdfCompressViewModel`, `PdfPasswordViewModel`, watermark VM |

**Code-judo move**

- One `ShareArtifactLauncher` / `ExportShareActions` in `feature/components` (or `core/ui` if pure Intent building).
- Use `DocumentPresentationFormatter` / shared date helpers only.
- Extract `PdfSourceInspectionController` for inspect/preview/password-needs flag (shared by compress/password/watermark).

Every duplicate is a future bug farm when FileProvider authorities or share flags change.

---

### 6. Tools feature is a second application — High

Approximate feature package LOC:

| Package | LOC |
| --- | --- |
| `feature/tools` | ~5.8k |
| `feature/settings` | ~4.0k |
| `feature/editor` | ~3.9k |
| `feature/camera` | ~2.5k |
| `feature/document` | ~2.4k |
| `feature/library` | ~2.2k |

PDF tools alone: multi-thousand-line screens + shared chrome + five ViewModels sharing one overloaded `PdfToolUiState` bag (sources, passwords, watermark knobs, compress sizes, reader bits).

**Problem**

- Shared `PdfToolUiState` couples unrelated tools; changing watermark fields risks compress/reader mental load.
- Navigation still registers `FeaturePlaceholderRoute` for unfinished routes — dead product surface.

**Code-judo move**

- Per-tool `UiState` types (merge state ≠ watermark state).
- One screen file + one ViewModel file per tool.
- Remove or gate placeholders; do not leave three placeholder destinations as permanent graph noise.

---

### 7. God ViewModels / constructor overload — Medium–High

| ViewModel | Approx. injected deps | Issue |
| --- | --- | --- |
| `SettingsViewModel` | ~20 | Settings hub owns backup, storage, models, content, theme… |
| `DocumentDetailViewModel` | ~19 | Document CRUD + import + export + share + groups |
| `PdfToolViewModels` file | multi-VM | Shared bloated state |
| `ScanSessionViewModel` | ML + capture | Wrong layer + orchestration weight |
| `LibraryViewModel` | ~15 | Library + groups + mutations |

**Code-judo move**

Split by **capability**, not by screen name only:

- `DocumentDetailViewModel` + `DocumentExportViewModel` + `DocumentImportViewModel` (scoped to same nav back stack entry), **or**
- One ViewModel + focused collaborators: `DocumentExportCoordinator`, `PageMutationCoordinator` as plain classes injected into the VM.

Settings: separate `StorageBackupViewModel` (screen already exists) from hub appearance/FAQ/model prefs.

---

### 8. Test coverage skew — Medium–High

**~40.5k main LOC vs ~2.3k test LOC.**

Strong areas:

- ML policies, quad math, filter tuning, stability trackers, formatters, a few pure UI helpers.

Weak / missing relative to risk:

- `DefaultPageRepository` finalize / replace / edit (core product path)
- `DefaultPageImageProcessor` end-to-end processing decisions
- `DefaultDocumentExportRepository` / PDF encryption paths
- `LibraryArchiveEngine` restore/merge (high data-loss impact)
- ViewModel state machines (export busy flags, import progress, auto-capture triggers)
- Almost no instrumented tests (3 androidTest files)

**Code-judo move for `feature/optimization`**

Add a thin **critical-path** suite before micro-optimizing OpenCV:

1. Page finalize success / NEEDS_REVIEW / replacement.
2. Export PDF smoke with fake storage.
3. Archive policy + path rewrite unit tests (expand existing policy tests into engine seams).

Optimizing untested hot paths is gambling.

---

### 9. `OpenCvPageFilterProcessor` as 1k-line object — Medium

Single `object` owns init, Auto resolution, all presets, analysis hooks, fallbacks. Related logic also in `AdaptivePageFilterTuning` and `PageFilterAdjustmentsApplier`.

**Code-judo move**

```
core/processing/filters/
  FilterPipeline.kt          # entry
  AutoPresetResolver.kt      # already partially AdaptivePageFilterTuning
  GrayscaleFilter.kt / CleanFilter.kt / …
  OpenCvMatSupport.kt
```

Keeps preset algorithms independently reviewable and profileable — which matters for an “optimization” branch.

---

### 10. Navigation host as kitchen sink — Medium

`ScanlyNavHost.kt` (~940 lines) owns:

- Bottom bar + rail chrome
- Transition specs
- Full route table including tools, settings subroutes, placeholders

**Code-judo move**

- `ScanlyScaffoldChrome.kt` (nav bar/rail)
- `libraryNavGraph()`, `toolsNavGraph()`, `settingsNavGraph()` extension builders
- Keep host as composition root only

---

### 11. Layer skew: feature dominates — Medium

Approximate main Kotlin by layer:

| Layer | LOC | Share |
| --- | ---: | ---: |
| feature | ~26.7k | ~66% |
| data | ~5.6k | ~14% |
| core | ~4.5k | ~11% |
| domain | ~1.8k | ~4% |
| navigation | ~1.1k | ~3% |
| di | ~0.3k | ~1% |

Domain is thin (good if pure), but **feature is overweight** because UI files absorb business-adjacent presentation rules, share plumbing, and tool state that could be smaller modules. Optimization work that only tunes algorithms in `core/` will not reduce the dominant cost of change (Compose feature code).

---

### 12. Smaller maintainability notes — Low–Medium

- **Stringly password detection** in PDF compress (`message.contains("password")`) — brittle; use typed errors.
- **Bitmap recycle discipline** in PDF VMs is careful but easy to get wrong when forking tools — centralize ownership.
- **`ImportImagesProgress.displayMessage` deprecated** still present — delete dead API.
- **`LibraryArchiveEngine`** mixes ZIP I/O, JSON manifest, Room transactions, progress — extract `ArchiveManifest`, `ArchiveZipStore`, `RestoreApplier` while behavior stays fixed.
- **Placeholder routes** still registered — either ship or remove from the graph.
- **Single Gradle module** is fine at this size, but tools/PDF could become `:feature-tools` later if compile times hurt; split files first, modules second.

---

## Architecture compliance scorecard

| Rule (from project docs) | Status | Notes |
| --- | --- | --- |
| feature ↛ data/Room | **Pass** | No feature→data imports found |
| feature → use cases for I/O | **Mostly** | Library flows good; live ML bypasses |
| domain ↛ Android | **Fail** | Import + Bitmap surfaces |
| data owns I/O | **Pass** | Repos, archive, export solid |
| core free of feature/UI VMs | **Pass** | |
| Use cases encapsulate business rules | **Weak** | Many pass-throughs; real rules in repos/processors |
| Reuse shared components | **Partial** | SharedComponents exists; still local forks |
| Tests for logic | **Partial** | Core pure logic yes; product paths thin |

---

## Recommended plan (for `feature/optimization` and beyond)

### Phase 0 — Guardrails (1–2 days)

- [ ] Add CONTRIBUTING/Agents note: **no new code in files ≥900 lines without split**
- [ ] Inventory mega-files; assign owners / target file map
- [ ] Delete or isolate `FeaturePlaceholder` routes if unused product-wise

### Phase 1 — Decompose UI (highest ROI)

- [ ] Split `DocumentDetailScreen` by task
- [ ] Split `SharedComponents` into focused files
- [ ] Split PDF tools into per-tool packages + per-tool UI state
- [ ] Deduplicate share/date helpers

### Phase 2 — Capture ownership

- [ ] Introduce `LiveDocumentAnalysisSession` (or equivalent) behind domain interface
- [ ] Slim `ScanSessionViewModel` to state mapping + capture commands
- [ ] Align live + still paths on shared policies

### Phase 3 — Domain honesty

- [ ] Move `ImportImagesUseCase` Android I/O to data
- [ ] Collapse pure pass-through use cases (or group into aggregate facades)
- [ ] Reduce ViewModel constructor dependency counts

### Phase 4 — Tests that protect optimization

- [ ] Page finalize / reprocess unit tests with fakes
- [ ] Export + archive critical paths
- [ ] Live analysis session pure tests (stability + phase transitions already partial)

### Phase 5 — Algorithm optimization (only after 1–4)

- [ ] Profile OpenCV filter + ML paths with real devices
- [ ] Split `OpenCvPageFilterProcessor` so hot presets can change without touching Auto

Doing Phase 5 first on mega-files will create large, unreviewable diffs and regress UI accidentally.

---

## Approval bar (this audit)

| Criterion | Met? |
| --- | --- |
| No clear structural regression in recent architecture docs vs code | Partial — docs better than live-camera reality |
| No obvious missed simplification | **No** — mega-files + thin use cases are clear |
| No unjustified 1k+ files | **No** — eight files over line |
| No spaghetti growth risk | **At risk** — tools + document detail |
| Abstractions earn keep | **Mixed** — processors yes; many use cases no |
| Boundaries clean | **Mixed** — data good; domain/camera not |
| Tests back critical paths | **No** |

**Overall: Request changes (structural).** The product code is serious and often careful. The codebase is not yet in a shape where unbounded feature growth stays safe. Treat modularization and boundary cleanup as the real optimization work.

---

## Appendix A — Largest files (reference)

```
1967  feature/document/DocumentDetailScreen.kt
1798  feature/tools/pdf/PdfToolScreens.kt
1565  feature/components/SharedComponents.kt
1515  feature/camera/ScanSessionScreen.kt
1121  feature/tools/pdf/PdfToolShared.kt
1021  core/processing/OpenCvPageFilterProcessor.kt
1020  feature/tools/qr/QrToolScreen.kt
1017  feature/library/LibraryScreen.kt
 940  navigation/ScanlyNavHost.kt
 908  feature/editor/PageCropScreen.kt
 860  feature/settings/StorageBackupScreen.kt
 837  feature/onboarding/OnboardingScreen.kt
 810  feature/tools/pdf/PdfToolViewModels.kt
 765  data/archive/LibraryArchiveEngine.kt
 762  feature/settings/ModelBenchmarkScreen.kt
 757  feature/editor/FilterPickerScreen.kt
 718  feature/editor/FilterCustomizeScreen.kt
 718  feature/tools/ToolsScreen.kt
 711  feature/library/GroupDetailScreen.kt
 685  data/pdftools/DefaultPdfToolkitRepository.kt
 573  data/page/DefaultPageRepository.kt
```

## Appendix B — Method of review

- Read architecture docs and conventions against package layout
- Measured Kotlin LOC by layer and largest files
- Sampled ViewModels, use cases, repositories, processors, navigation
- Grepped layer-boundary violations (feature→data, domain→Android)
- Mapped duplication (share, dates, PDF tool state)
- Compared unit-test inventory to critical product paths

This is a **maintainability audit**, not a functional QA pass or security audit. Behavior was not exhaustively re-verified on device.
)
