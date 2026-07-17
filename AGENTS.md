# AGENTS.md

Guidance for AI coding agents working in the Scanly repository.

**Full documentation:** [docs/README.md](docs/README.md)

## Project Snapshot

- Single-module Android app (`:app`) using Kotlin + Jetpack Compose + Material 3.
- Package: `in.c1ph3rj.scanly` — escape `in` as ``package `in`.c1ph3rj.scanly``.
- Current version: `1.0.11` (version code `11`) — see `app/build.gradle.kts`, [VERSION.md](VERSION.md).
- Entry point: `MainActivity.kt` → onboarding gate → `ScanlyNavHost`.
- Offline-first document scanner: camera capture, page editing, local persistence, PDF/image export, library backup/restore.

## Architecture and Code Layout

```
app/src/main/java/in/c1ph3rj/scanly/
├── ui/theme/          # ScanlyTheme, colors, typography
├── navigation/        # ScanlyDestination, ScanlyNavHost
├── feature/           # Screens + ViewModels (home, library, tools, camera, editor, widgets, …)
├── domain/            # Models, repository interfaces, use cases (73 classes)
├── data/              # Room, storage, export, archive, settings, update implementations
├── core/              # ML (corners + gate), OpenCV, editing math, shared UI utilities
└── di/                # Hilt modules (+ flavor-specific update bindings)
```

**Layer rules:** ViewModels call use cases; use cases call repository interfaces; repositories handle Room/files/network. Never call DAOs or DataStore from Compose screens or ViewModels directly.

| Doc | Contents |
| --- | --- |
| [docs/architecture/overview.md](docs/architecture/overview.md) | Layers, DI, connection maps |
| [docs/architecture/navigation.md](docs/architecture/navigation.md) | Routes and user flows |
| [docs/development/setup.md](docs/development/setup.md) | Build commands |
| [docs/development/conventions.md](docs/development/conventions.md) | Adding screens, migrations |

## Build and Test (Windows/PowerShell)

- Gradle wrapper: 9.5.0 (`gradle/wrapper/gradle-wrapper.properties`).
- Daemon JVM: Java 21 (`gradle/gradle-daemon-jvm.properties`).
- Compile target: Java 11; SDK 36 compile/target, min SDK 29.

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
```

- **Debug** — no minify; `UPDATE_CHANNEL = "github"`.
- **githubRelease** / **playStoreRelease** — R8 + shrink resources; the generic `release` variant is disabled.
- Release signing via `SCANLY_RELEASE_*` (gradle props, env vars, or `local.properties`).

## Project-Specific Conventions

- Add new UI as composables under `feature/`; wrap screens in `ScanlyTheme`.
- Add business logic as use cases in `domain/usecase/`; bind repositories in `di/`.
- Keep dependency versions in `gradle/libs.versions.toml`; reference via `libs.*` in Gradle scripts.
- Compose deps use BOM (`implementation(platform(libs.androidx.compose.bom))`).
- Preserve raw captures — never overwrite files under `raw/`; regenerate `processed/` and `thumbs/`.
- Room schema is version `4`. Any schema change requires a `Migration_X_Y` in `ScanlyDatabase.kt` and version bump.
- ML model assets under `app/src/main/assets/models/`: corner variants (Lite/Standard/High/Accurate) + `scanly_document_gate_float16.tflite` (keep `noCompress += "tflite"`).
- Document detection prefs in DataStore: live/post models, automatic selection, document gate; pure black theme is separate (`pure_black_enabled`).
- Gallery import limit: 10 images (`ImageImportSupport`).
- Export saves go to `Downloads/Scanly` by default; custom SAF trees persist via DataStore (`export_tree_uri`, `export_tree_label`).
- Library backups write `.scanly` ZIPs under the destination's lowercase `backup/` child via `LibraryArchiveWorker`.

## Navigation Quick Reference

Top-level tabs: `home`, `library`, `tools`, `settings`.

Typed routes (real flows):

- `document/{documentId}` — document detail
- `camera/session/{documentId}?replacePageId={pageId}` — scan session
- `preview/page/{pageId}` — page preview
- `editor/page/{pageId}` — page editor (live preview; full-screen Filters + Adjust overlays; retake/delete)
- `crop/page/{pageId}` — AI Detect, rotate, four-point crop, reset, apply
- `group/{groupId}` — group detail
- `legal/{documentType}` — privacy/licenses viewer
- `settings/appearance`, `settings/detection`, `settings/widgets`, `settings/about`, `settings/faq`, `settings/licenses`, `settings/storage`, `settings/model-benchmark` — settings hub sub-screens
- `tools/qr` — QR scan + generate
- `tools/pdf/reader`, `tools/pdf/merge`, `tools/pdf/compress`, `tools/pdf/password`, `tools/pdf/watermark` — PDF toolkit

Legacy placeholder routes (`camera`, `review`, `editor` top-level) use `FeaturePlaceholderScreen` — do not wire new features there.

Home-screen widgets / quick actions use `in.c1ph3rj.scanly.action.{SCAN,IMPORT,QR,LIBRARY}` → `LaunchActionViewModel` (see [docs/architecture/navigation.md](docs/architecture/navigation.md)).

## Testing Reality

- Unit tests: `app/src/test/java/in/c1ph3rj/scanly/` (41 files).
- Instrumented: `app/src/androidTest/` (onboarding UI, OpenCV filter processor, smoke).
- See [docs/development/testing.md](docs/development/testing.md) for gaps.

## Agent Guardrails

- Keep changes confined to `:app` unless build-system updates are explicitly required.
- Match existing Kotlin style (`kotlin.code.style=official` in `gradle.properties`).
- When adding modules/dependencies, update `settings.gradle.kts` and `libs.versions.toml` together.
- Update `licenses.json` when adding third-party libraries.
- On user-facing behavior changes, update [CHANGELOG.md](CHANGELOG.md) and relevant `docs/` pages; on releases, also [VERSION.md](VERSION.md) and [README.md](README.md).
- Do not commit `local.properties`, keystore files, or build outputs.
- Do not change on-disk storage layout without a migration plan.

## Docs updates — ask the user first (required)

Whenever the user asks to **update the docs**, **update documentation**, **update the docs folder**, **refresh docs**, **docs/site config**, or similar, **do not invent content**. Stop and ask the user first:

1. **What's landing next?** — Which features, fixes, or work-in-progress should be documented as upcoming / in development?
2. **What must go into docs?** — Which topics, screens, APIs, user flows, release notes, architecture notes, or site-config fields need updating?
3. **What is required for this docs pass?** — Scope (overview vs deep technical), audience, files to touch, version/tag, and anything that must *not* change.

Only after the user answers those questions, edit under `docs/` (including `docs/site/config.json` for marketing product data). Prefer precise updates over rewriting whole trees.

Related product-config authoring notes: [docs/site/README.md](docs/site/README.md).

## Key Files

| File | Purpose |
| --- | --- |
| `MainActivity.kt` | App shell, onboarding gate, theme / pure black, update dialog, widget/shortcut redirects |
| `feature/launch/` | `ScanlyLaunchAction`, `LaunchActionViewModel` |
| `feature/widget/` | Actions / Scan / QR `AppWidgetProvider`s |
| `res/xml/shortcuts.xml` | Launcher quick actions |
| `ScanlyNavHost.kt` | Navigation registration and chrome |
| `ScanlyDatabase.kt` | Room schema, entities, migrations |
| `DefaultPageRepository.kt` | Capture finalize and page edit persistence |
| `PageImageProcessor` (interface) / implementation | Capture/reprocess + `detectDocumentCorners` |
| `LiteRtDocumentCornerDetector` / `LiteRtDocumentGateDetector` | Multi-model corner + semantic gate inference |
| `PageCropViewModel` / `PageCropScreen` | Crop route (AI Detect, rotate, handles) |
| `FilterPickerScreen` / `FilterCustomizeScreen` | Full-screen filter pick + adjust overlays |
| `DefaultDocumentExportRepository.kt` | PDF/ZIP export and share |
| `DefaultLibraryArchiveRepository.kt` | Backup/restore orchestration |
| `DefaultExportStorageRepository.kt` | Save exports to configured destination |
| `app/build.gradle.kts` | SDK levels, version, signing, dependencies |
