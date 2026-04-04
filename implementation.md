# Scanly Implementation Plan

This document turns the current [README.md](README.md) product scope into a production-grade implementation roadmap for the Android app.

It is written for the repo as it exists today:

- single Android module: `:app`
- Kotlin + Jetpack Compose + Material 3
- no backend
- no scanner pipeline yet
- document edge model currently available as a YOLO11n pose model trained on 4 document corners

## 1. Product Readiness Analysis

The README describes a scanner that feels simple on the surface, but it actually depends on five tightly coupled systems:

1. camera capture
2. live document detection
3. post-processing and perspective correction
4. document/page persistence
5. export and share

To make this production-grade, the app should be designed around these realities:

- The ML model is assistive, not authoritative. Users must always be able to manually capture, re-crop, rotate, and reprocess pages.
- The raw image must always be preserved. All edits should be non-destructive.
- Live preview inference and final image processing are different jobs. Preview can run on downscaled frames; export must use the original captured image.
- Auto-capture should be gated by stability rules, not only by one good frame.
- The app must be offline-first. The README scope does not require any server.
- Multi-page documents are a core domain concept, not a UI convenience.

## 2. Key Technical Reality About Your Model

Your current model is a YOLO11n pose model that predicts four ordered keypoints:

- `TL`
- `TR`
- `BR`
- `BL`

That is a strong fit for document corner detection, but there is an important implementation gap:

- your sample artifact is a PyTorch `.pt` model
- the README says the Android app should use a LiteRT float16 model

That means the first production task is not UI, but deployment validation:

- export the model to a mobile-friendly runtime format
- verify the keypoint tensor layout is preserved after export
- benchmark latency and memory on at least one mid-range Android device
- confirm that corner order stays stable enough for perspective correction

If the exported mobile model is unstable or too slow, the app can still ship with:

- manual capture
- post-capture detection
- manual 4-point crop fallback

So the plan below intentionally does not make the whole product depend on perfect live auto-capture from day one.

## 3. Recommended Architecture

## Architecture Style

Use clean architecture with strict boundaries inside `:app` first, then extract modules only after the contracts are stable. For this repo, that is the best balance between production quality and delivery speed.

Recommended top-level package structure inside `app/src/main/java/in/c1ph3rj/scanly/`:

- `core/`
- `data/`
- `domain/`
- `feature/`
- `di/`
- `navigation/`

## Layer Responsibilities

`feature/`

- Compose screens
- ViewModels
- UI state
- screen-specific mappers
- user interaction handling

`domain/`

- pure business models
- repository interfaces
- use cases
- processing policies
- geometry and validation rules that should not depend on Android UI

`data/`

- Room database
- file storage
- CameraX adapters
- ML runtime adapters
- image processing implementations
- repository implementations

`core/`

- shared error/result models
- coroutine dispatchers
- logging
- image and geometry helpers
- reusable UI primitives
- testing utilities

`di/`

- Hilt dependency graph
- runtime bindings for repositories, processors, and dispatchers

`navigation/`

- app destinations
- nav graphs
- route argument definitions

## Recommended Feature Slices

- `feature/home`
- `feature/camera`
- `feature/review`
- `feature/editor`
- `feature/export`

## 4. Core Domain Model

The app should revolve around documents and pages, not around temporary captures.

Suggested domain entities:

- `Document`
  - `id`
  - `title`
  - `createdAt`
  - `updatedAt`
  - `pageCount`
  - `coverPageId`

- `ScanPage`
  - `id`
  - `documentId`
  - `pageIndex`
  - `rawImagePath`
  - `processedImagePath`
  - `thumbnailPath`
  - `rotationDegrees`
  - `cropQuad`
  - `filterPreset`
  - `processingState`

- `CropQuad`
  - normalized `topLeft`
  - normalized `topRight`
  - normalized `bottomRight`
  - normalized `bottomLeft`

- `DetectionResult`
  - `quad`
  - `confidence`
  - `isStable`
  - `sourceWidth`
  - `sourceHeight`

- `ExportJob`
  - `documentId`
  - `format`
  - `outputUri`
  - `status`

## Non-Destructive Editing Rule

Never overwrite the original capture. The page should keep:

- the raw source image
- the latest processed image
- metadata that describes edits

That allows:

- re-cropping from the original
- reprocessing after algorithm changes
- quality-preserving export

## 5. Recommended Android Stack

- UI: Jetpack Compose
- Navigation: Navigation Compose
- DI: Hilt
- Local persistence: Room
- Background jobs: WorkManager
- Camera: CameraX (`Preview`, `ImageCapture`, `ImageAnalysis`)
- ML runtime: LiteRT if export is clean and performant; otherwise evaluate ONNX Runtime Mobile as the fallback deployment path
- Image processing: OpenCV-backed processor behind a clean interface, or a small native processing layer if OpenCV footprint becomes unacceptable
- Concurrency: Coroutines + Flow

## 6. Scanner Pipeline Design

The scanner should be implemented as two related but separate pipelines.

## A. Live Preview Pipeline

Purpose:

- draw document overlay
- estimate stability
- drive auto-capture countdown

Design:

1. CameraX preview runs continuously.
2. `ImageAnalysis` receives downscaled frames.
3. The detector runs on analysis frames, not on the full capture resolution.
4. Keypoints are reordered and validated.
5. A temporal smoother compares the latest N frames.
6. The app derives:
   - detection confidence
   - corner movement delta
   - aspect ratio sanity
   - frame-to-frame stability score
7. If stable long enough, the UI shows:
   - overlay
   - "Hold steady"
   - `3 -> 2 -> 1`
8. A capture lock prevents duplicate shots.

## B. Capture Processing Pipeline

Purpose:

- generate the final scan-quality page

Design:

1. Capture full-resolution image with `ImageCapture`.
2. Persist the raw image immediately.
3. Try to map the live preview quad into capture coordinates.
4. If mapping is low-confidence, rerun detection on the captured still image.
5. Apply perspective correction.
6. Apply enhancement pipeline:
   - shading normalization
   - contrast improvement
   - denoise
   - optional grayscale / black-and-white preset
7. Save:
   - processed page
   - thumbnail
   - edit metadata
8. Open page review/editor if the result is weak or user chooses to edit.

This split is critical. It keeps preview fast and export quality high.

## 7. Clean Architecture Interfaces

The following interfaces should exist before feature growth gets too far:

- `DocumentRepository`
- `PageRepository`
- `CameraSessionController`
- `DocumentDetector`
- `CaptureStabilityEvaluator`
- `PageProcessor`
- `PageEditor`
- `PdfExporter`
- `ImageExporter`
- `ShareLauncher`

Representative use cases:

- `ObserveDocumentsUseCase`
- `CreateDocumentUseCase`
- `RenameDocumentUseCase`
- `DeleteDocumentUseCase`
- `CreateScanSessionUseCase`
- `AnalyzePreviewFrameUseCase`
- `CapturePageUseCase`
- `ProcessCapturedPageUseCase`
- `UpdatePageCropUseCase`
- `RotatePageUseCase`
- `ApplyFilterUseCase`
- `ReorderPagesUseCase`
- `ExportDocumentPdfUseCase`
- `ExportDocumentImagesUseCase`

## 8. Storage Strategy

Use Room for metadata and app-private storage for binary files.

Suggested file layout:

- `files/documents/{documentId}/raw/page_001.jpg`
- `files/documents/{documentId}/processed/page_001.jpg`
- `files/documents/{documentId}/thumbs/page_001.jpg`
- `cache/exports/{documentId}/document.pdf`

Room should store metadata only:

- document title
- timestamps
- page order
- crop quad
- filter preset
- rotation
- file paths
- export status if needed

This keeps DB small and image operations safe.

## 9. Production Requirements

The following are not optional if the app should feel production-grade.

## Functional

- manual capture must always work even if ML fails
- manual crop must always be available
- raw page should never be lost
- multi-page grouping must survive app restarts
- export should work for both single-page and multi-page documents

## Performance

- camera preview should feel real-time
- live detection should run on throttled frames, not every frame
- processing should happen off the main thread
- export should happen in background work for larger documents

## Reliability

- protect against duplicate capture
- protect against half-written files
- recover documents after app process death
- make export retryable

## UX

- show detection confidence indirectly through overlay stability, not a technical number
- keep manual override visible
- make editing per-page and reversible
- avoid blocking the user on long processing jobs

## Privacy

- offline-first by default
- no upload requirement
- store files only in app-private storage unless user exports or shares

## 10. Sprint Plan

Recommended cadence:

- Sprint 0: 1 week
- Sprint 1 onward: 2 weeks each

This gives a realistic v1 path of about 15 to 17 weeks, depending on device debugging and model conversion effort.

## Sprint 0: Technical Validation and Delivery Foundations

Goal:

- remove the biggest project risk first: whether the document-corner model is actually shippable on Android

Scope:

- confirm the current repo state: no Android-ready model artifact is checked in yet, so this sprint is documentation-first unless a mobile export is added externally
- validate the YOLO keypoint model export path for Android when a mobile artifact is available
- decide ML runtime for production, with LiteRT float16 as the primary path and ONNX Runtime Mobile as the fallback to evaluate if export or latency is poor
- record the exact tensor contract for the exported model, including input resolution, normalization, output shape, and ordered corner semantics
- lock the corner ordering contract as `TL -> TR -> BR -> BL`
- define measurable performance budgets for preview inference, still-image inference, and memory use on a target mid-range Android device
- create architecture decision records for:
  - DI
  - local storage
  - image processing library
  - navigation approach
- collect a small regression dataset of real document photos from target devices
- document the manual-first fallback path if live auto-capture is too slow or unstable for Sprint 0

Deliverables:

- documented model deployment decision
- sprint 0 technical validation note in `docs/sprint-0-validation.md`
- sample Android spike only if a mobile model artifact exists and can be loaded in the app
- corner-order contract documented as `TL -> TR -> BR -> BL`
- measured risk log for slow devices, poor lighting, false auto-captures, and export instability
- recorded fallback decision for manual capture plus post-capture detection if the runtime is not production-ready

Exit criteria:

- the model runs on Android hardware, or emulator-backed validation is complete enough to choose the runtime
- mobile output format and tensor contract are confirmed in writing
- preview latency target and memory budget are defined for the target device class
- the fallback manual-first path is approved if the runtime is not ready for live auto-capture

Why this sprint matters:

- if model export is weak, we should pivot early to a manual-first scanning flow with post-capture detection instead of discovering the problem halfway through the UI build

## Sprint 1: Clean Architecture Skeleton and App Shell

Goal:

- create the base application structure so later features do not collapse into one large Activity/ViewModel

Scope:

- add package boundaries for `core`, `data`, `domain`, `feature`, `di`, `navigation`
- introduce Hilt
- introduce Navigation Compose
- add common result/error models
- add dispatcher abstraction
- add ViewModel base patterns where needed
- add simple startup/home nav shell
- add CI-friendly Gradle tasks for assemble, unit tests, lint

Deliverables:

- app shell with placeholder home/camera/review/editor destinations
- dependency graph bootstrapped
- coding conventions documented in code comments and package layout

Exit criteria:

- app builds cleanly
- navigation works
- dependencies are injected through interfaces, not concrete classes

## Sprint 2: Document Library and Persistence

Goal:

- establish the real app domain: documents and pages

Scope:

- add Room schema for documents and pages
- add repositories for CRUD operations
- implement home screen library list
- add empty state
- add create, rename, delete document flows
- generate and display thumbnails
- persist document state across app restarts

Deliverables:

- usable home screen
- local metadata persistence
- app-private file manager abstraction

Exit criteria:

- a document can be created, reopened, renamed, and deleted
- library survives process death
- no scanner code is required yet for the library to work

## Sprint 3: Manual Camera Capture MVP

Goal:

- make the app useful before auto-capture exists

Scope:

- integrate CameraX preview
- request camera permission
- implement manual shutter
- create a scan session tied to a document
- save raw captures to document storage
- handle orientation and EXIF correctly
- show captured pages in a simple review strip
- allow adding multiple pages to one document

Deliverables:

- working manual document capture flow
- multi-page session creation
- page persistence with thumbnails

Exit criteria:

- user can create a document and manually scan several pages
- files are stored safely
- app remains usable even if ML is disabled

## Sprint 4: Live Detection Overlay and Auto-Capture

Goal:

- add smart scanning on top of the manual baseline

Scope:

- integrate production ML runtime into `DocumentDetector`
- run detection in `ImageAnalysis`
- map frame coordinates to preview overlay coordinates
- draw polygon overlay
- add auto-capture toggle
- add stability evaluator based on:
  - confidence threshold
  - corner jitter threshold
  - minimum stable duration
  - shape sanity checks
- add countdown UI
- add duplicate-capture lockout and cooldown

Deliverables:

- blue live overlay around detected document
- "Hold steady" behavior
- `3 -> 2 -> 1` countdown
- auto-capture ON/OFF switch

Exit criteria:

- manual capture still works
- auto-capture can be disabled instantly
- overlay tracks document in common lighting conditions
- false repeated captures are blocked

## Sprint 5: Image Processing Pipeline

Goal:

- convert captured photos into scan-quality pages

Scope:

- build `PageProcessor`
- perspective correction from 4-point quad
- re-run detection on still image when preview mapping is weak
- add lighting/shadow normalization
- add contrast enhancement
- add document-style presets:
  - original
  - enhanced color
  - grayscale
  - black and white
- generate processed image and thumbnail
- preserve raw image and processing metadata

Deliverables:

- automatic crop and flatten pipeline
- first production-quality scan output
- non-destructive page processing

Exit criteria:

- processed output is visibly better than the raw photo
- manual editor can be opened when the crop is wrong
- raw capture remains preserved

## Sprint 6: Per-Page Editor and 4-Point Cropper

Goal:

- make every page correctable

Scope:

- build page editor screen
- rotate left/right
- apply and preview filter presets
- freeform 4-point cropper
- prefill crop handles from detected edges
- support reset to original detection
- save edits back into processing metadata
- reprocess page after crop/filter changes

Deliverables:

- fully functional page editor
- recrop workflow
- reversible page adjustments

Exit criteria:

- each page can be edited independently
- crop handles are usable on phones of different sizes
- updated processed image replaces the previous derived image only, not the raw source

## Sprint 7: Multi-Page Review, Reorder, and Document Finalization

Goal:

- complete the document assembly workflow

Scope:

- document detail screen with all pages
- reorder pages
- delete page from document
- retake/replace page
- add more pages to an existing document
- improve review UX around current page vs full document

Deliverables:

- polished multi-page review flow
- stable document lifecycle from first page to final document

Exit criteria:

- users can build a document incrementally
- page ordering is persisted
- page replacement does not corrupt the document

## Sprint 8: Export and Share

Goal:

- turn scanned documents into useful outputs

Scope:

- PDF export
- image export
- Android share sheet integration
- export progress and error states
- background export for larger documents
- output naming and duplicate handling
- exported file cleanup strategy for cached temporary files

Deliverables:

- merged PDF export
- image set export
- share flows for both

Exit criteria:

- exported PDF opens correctly in common viewers
- page order in PDF matches document order
- user can share document without exposing app-private raw files directly

## Sprint 9: Hardening, Testing, and Release Readiness

Goal:

- make v1 dependable enough to ship

Scope:

- unit tests for use cases, geometry, crop ordering, processing decisions
- instrumented tests for Room persistence and selected navigation flows
- Compose UI tests for home/library/editor basics
- device matrix validation
- low-memory and process-death testing
- crash logging and lightweight analytics hooks
- app startup/performance tuning
- accessibility polish
- release checklist

Deliverables:

- release candidate build
- test suite covering the highest-risk behavior
- launch readiness checklist

Exit criteria:

- no blocker bugs in scanning, editing, or export
- acceptable latency on target devices
- app can recover from common interruptions

## 11. Cross-Sprint Engineering Rules

These rules should stay true in every sprint.

## Rule 1: Keep the app useful at every stage

Do not block all progress on auto-capture quality. A manual-first scanner with excellent editing is more valuable than a fancy preview that fails under real lighting.

## Rule 2: Preserve raw data

Every capture should be recoverable. Never make the processed image the only source of truth.

## Rule 3: Test geometry aggressively

The highest-risk logic is not the Compose UI. It is:

- corner ordering
- frame-to-view coordinate transforms
- preview-to-capture coordinate remapping
- perspective transform correctness

These should have unit and image regression coverage.

## Rule 4: Separate runtime concerns

Keep these isolated:

- ML inference
- image processing
- storage
- export
- UI

That keeps the app maintainable when the model or processing stack changes later.

## Rule 5: Prefer offline-first UX

No backend is required for v1. Treat cloud features as future scope, not hidden dependencies.

## 12. Testing Strategy

The app needs more than standard unit tests.

## Unit Tests

- use case behavior
- crop quad validation and ordering
- stability evaluator logic
- export naming rules
- repository mapping logic

## Instrumented Tests

- Room persistence
- file storage lifecycle
- selected image processor tests on representative sample images

## UI Tests

- home screen empty and populated states
- document creation and rename
- editor screen basic interaction
- export action visibility

## Regression Image Pack

Maintain a small device-captured dataset with:

- white paper on dark table
- receipt on textured background
- low light
- skewed angle
- partially visible page
- non-document distractors

Use it to validate:

- corner accuracy
- false positive rate
- processing quality

## 13. Biggest Risks and Mitigations

## Risk: Mobile model is too slow for live preview

Mitigation:

- run on throttled analysis frames
- use smaller analysis resolution
- use manual capture fallback
- rerun detection on still capture only when necessary

## Risk: Corner order becomes unstable after model export

Mitigation:

- enforce an ordering pass in domain logic
- add regression tests on exported model output
- reject impossible quads

## Risk: Auto-capture feels unreliable

Mitigation:

- use stability windows, not one-frame capture
- keep toggle visible
- let user fall back to manual instantly

## Risk: Processed pages look over-filtered

Mitigation:

- keep filter presets conservative
- always keep original and enhanced variants
- make the editor easy to reach

## Risk: Export pipeline causes memory issues on large documents

Mitigation:

- process pages sequentially
- avoid loading all full-size bitmaps into memory together
- move larger exports to background work

## 14. Recommended v1 Scope Boundary

In scope for v1:

- document library
- manual and auto capture
- live edge overlay
- multi-page documents
- per-page editing
- 4-point crop
- PDF/image export
- share

Out of scope for v1:

- OCR/text extraction
- text search
- cloud backup/sync
- account system
- collaborative sharing
- advanced document classification

## 15. Final Recommendation

The correct production order for Scanly is:

1. validate the model on Android
2. build clean architecture foundations
3. ship a strong manual scanner baseline
4. layer auto-detection and auto-capture on top
5. invest heavily in editing and export quality
6. harden for real-device reliability

That ordering matches the README goals while protecting the project from the biggest practical risk: a great model notebook that does not yet behave like a great mobile scanner.
