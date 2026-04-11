# Scanly

Scanly is an offline-first Android document scanner built with Kotlin, Jetpack Compose, and Material 3.

The app focuses on a practical scanning workflow:

1. capture a document with the camera
2. detect and correct page geometry
3. refine pages with editing tools
4. store multi-page documents locally
5. export as PDF or image sets

## Current State

The codebase is already split into a production-style single module app:

- `:app` is the only Android module
- entry point: `app/src/main/java/in/c1ph3rj/scanly/MainActivity.kt`
- app wiring: `ScanlyApplication`, navigation, Hilt modules, feature screens, and domain/data layers live under `app/src/main/java/in/c1ph3rj/scanly/`
- UI: Compose + Material 3
- local data: Room, DataStore, and app-private file storage
- camera and processing stack: CameraX, LiteRT, and OpenCV-based page processing

## What Scanly Does Today

- document library and document detail flows
- manual camera capture
- page persistence and thumbnail generation
- page editing with crop, rotate, and filter controls
- PDF and image export/share flows
- settings screens for app preferences and support content

## Repository Layout

- `app/` – Android application source and module build files
- `docs/` – architecture notes, sprint archives, and release-readiness documentation
- `gradle/` – wrapper and version catalog configuration
- `implementation.md` – architecture snapshot and current implementation notes

## Build and Run

From the repository root on Windows:

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
```

If you want a release-style verification pass:

```powershell
./gradlew.bat lintDebug
```

## Documentation

- `LICENSE` – GNU AGPL-3.0-only license for this repository
- `implementation.md` – current architecture snapshot and technical direction
- `OPEN_SOURCE_NEXT_STEPS.md` – checklist for publishing and maintaining the repo
- `docs/sprint-0/` through `docs/sprint-8/` – historical sprint notes kept as archive material

## License

Scanly source code in this repository is licensed under `AGPL-3.0-only`.

Third-party dependencies and model assets can have their own license terms. Review
`app/src/main/assets/settings/licenses.json` and upstream sources before redistribution.

## Project Principles

- offline-first by default
- preserve raw captures and avoid destructive edits
- keep manual controls available even when automation is imperfect
- prefer small, testable domain and data boundaries

## Contributing

Before sending changes, run the Gradle checks above and keep new code aligned with the existing package structure under `in.c1ph3rj.scanly`.
