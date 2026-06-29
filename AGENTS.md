# AGENTS.md

Guidance for AI coding agents working in the Scanly repository.

**Full documentation:** [docs/README.md](docs/README.md)

## Project Snapshot

- Single-module Android app (`:app`) using Kotlin + Jetpack Compose + Material 3.
- Package: `in.c1ph3rj.scanly` — escape `in` as ``package `in`.c1ph3rj.scanly``.
- Current version: `1.0.9` (version code `9`) — see `app/build.gradle.kts`, [VERSION.md](VERSION.md).
- Entry point: `MainActivity.kt` → onboarding gate → `ScanlyNavHost`.
- Offline-first document scanner: camera capture, page editing, local persistence, PDF/image export.

## Architecture and Code Layout

```
app/src/main/java/in/c1ph3rj/scanly/
├── ui/theme/          # ScanlyTheme, colors, typography
├── navigation/        # ScanlyDestination, ScanlyNavHost
├── feature/           # Screens + ViewModels (home, library, camera, editor, …)
├── domain/            # Models, repository interfaces, use cases
├── data/              # Room, storage, export, settings, update implementations
├── core/              # ML, OpenCV, editing math, shared UI utilities
└── di/                # Hilt modules
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

Release builds use ProGuard/R8 (`isMinifyEnabled = true`). Debug builds do not minify.

## Project-Specific Conventions

- Add new UI as composables under `feature/`; wrap screens in `ScanlyTheme`.
- Add business logic as use cases in `domain/usecase/`; bind repositories in `di/`.
- Keep dependency versions in `gradle/libs.versions.toml`; reference via `libs.*` in Gradle scripts.
- Compose deps use BOM (`implementation(platform(libs.androidx.compose.bom))`).
- Preserve raw captures — never overwrite files under `raw/`; regenerate `processed/` and `thumbs/`.
- Room schema is version `3`. Any schema change requires a `Migration_X_Y` in `ScanlyDatabase.kt` and version bump.
- ML model asset: `app/src/main/assets/models/document_corners_float16.tflite` (keep `noCompress += "tflite"`).
- Gallery import limit: 10 images (`ImageImportSupport`).

## Navigation Quick Reference

Top-level tabs: `home`, `library`, `settings`.

Typed routes (real flows):

- `document/{documentId}` — document detail
- `camera/session/{documentId}?replacePageId={pageId}` — scan session
- `preview/page/{pageId}` — page preview
- `editor/page/{pageId}` — page editor
- `group/{groupId}` — group detail
- `legal/{documentType}` — privacy/licenses viewer

Legacy placeholder routes (`camera`, `review`, `editor` top-level) use `FeaturePlaceholderScreen` — do not wire new features there.

## Testing Reality

- Unit tests: `app/src/test/java/in/c1ph3rj/scanly/` (22 files).
- Instrumented: `app/src/androidTest/` (onboarding UI test + smoke test).
- See [docs/development/testing.md](docs/development/testing.md) for gaps.

## Agent Guardrails

- Keep changes confined to `:app` unless build-system updates are explicitly required.
- Match existing Kotlin style (`kotlin.code.style=official` in `gradle.properties`).
- When adding modules/dependencies, update `settings.gradle.kts` and `libs.versions.toml` together.
- Update `licenses.json` when adding third-party libraries.
- On user-facing behavior changes, update [CHANGELOG.md](CHANGELOG.md) and relevant `docs/` pages; on releases, also [VERSION.md](VERSION.md) and [README.md](README.md).
- Do not commit `local.properties`, keystore files, or build outputs.
- Do not change on-disk storage layout without a migration plan.

## Key Files

| File | Purpose |
| --- | --- |
| `MainActivity.kt` | App shell, onboarding gate, theme, update dialog |
| `ScanlyNavHost.kt` | Navigation registration and chrome |
| `ScanlyDatabase.kt` | Room schema, entities, migrations |
| `DefaultPageRepository.kt` | Capture finalize and page edit persistence |
| `PageImageProcessor` (interface) / implementation | Image processing pipeline |
| `DefaultDocumentExportRepository.kt` | PDF/ZIP export and share |
| `app/build.gradle.kts` | SDK levels, version, signing, dependencies |