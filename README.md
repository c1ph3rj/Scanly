# Scanly

<p align="center">
  <img src="screenshots/scanly-intro.png" alt="Scanly intro screenshot" width="900" />
</p>

Scanly is an offline-first Android document scanner built with Kotlin, Jetpack Compose, and Material 3.

It is designed for a practical, local-only scanning workflow:

1. capture a document with the camera or import images from the gallery
2. detect and correct page geometry
3. refine pages with editing tools
4. store and organize multi-page documents locally, including collections
5. export as PDF or image sets

## Highlights

- offline-first by default
- document library with searchable collections (groups) and recent-item home dashboard
- manual camera capture with live document guidance
- import images from the gallery to create or extend documents
- page crop, rotate, and filter editing
- Advanced PDF export/share with password protection, page numbering, auto orientation, print sizes, margins, and group-level export
- settings with theme mode, storage usage, clear-all-data, FAQs, and license info

**Current version:** `1.0.9` (version code `9`) — see [VERSION.md](VERSION.md) and [CHANGELOG.md](CHANGELOG.md).

## Screenshots

<table>
  <tr>
	<td><img src="screenshots/1.png" alt="Scanly screenshot 1" width="100%" /></td>
	<td><img src="screenshots/2.png" alt="Scanly screenshot 2" width="100%" /></td>
	<td><img src="screenshots/3.png" alt="Scanly screenshot 3" width="100%" /></td>
  </tr>
  <tr>
	<td><img src="screenshots/4.png" alt="Scanly screenshot 4" width="100%" /></td>
	<td><img src="screenshots/6.png" alt="Scanly screenshot 6" width="100%" /></td>
	<td></td>
  </tr>
</table>

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Hilt
- Navigation Compose
- CameraX
- Room
- DataStore
- LiteRT
- OpenCV
- Coroutines and Flow

## Project Structure

- `app/` – Android application source and module build files
- `docs/` – complete project documentation (start at [docs/README.md](docs/README.md))
- `gradle/` – wrapper and version catalog configuration

## Current Architecture

The repository is organized as a production-style single-module app:

- `:app` is the only Android module
- entry point: `app/src/main/java/in/c1ph3rj/scanly/MainActivity.kt`
- app wiring: `ScanlyApplication`, navigation, Hilt modules, feature screens, and domain/data layers live under `app/src/main/java/in/c1ph3rj/scanly/`
- local data: a Room operational index, DataStore preferences, and a recoverable user-selected shared library
- camera and processing stack: CameraX, LiteRT, and OpenCV-based page processing

## Build and Run

From the repository root on Windows:

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
```

For an additional verification pass:

```powershell
./gradlew.bat lintDebug
```

## Documentation

**Full documentation lives in [`docs/`](docs/).** Start at [docs/README.md](docs/README.md) for complete project context.

| Document | Purpose |
| --- | --- |
| [docs/README.md](docs/README.md) | Documentation index and reading paths |
| [docs/overview/](docs/overview/) | What Scanly is, features, user guide |
| [docs/architecture/](docs/architecture/) | Layers, navigation, screens |
| [docs/data/](docs/data/) | Room database, file storage, settings |
| [docs/processing/](docs/processing/) | Capture, ML, filters, export |
| [docs/development/](docs/development/) | Setup, conventions, testing, releasing |
| [docs/reference/](docs/reference/) | Models, use cases, tech stack |
| [VERSION.md](VERSION.md) | Release metadata and upgrade notes |
| [CHANGELOG.md](CHANGELOG.md) | User-facing release history |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution workflow |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting |
| [Agents.md](Agents.md) | AI coding agent guidance |
| [LICENSE](LICENSE) | GNU AGPL-3.0-only |

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

If you are planning a change that affects public behavior, update the relevant docs in `docs/` and [CHANGELOG.md](CHANGELOG.md) before or alongside the code change.

