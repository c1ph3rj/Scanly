# Scanly Documentation

Complete documentation for **Scanly v1.0.9** — an offline-first Android document scanner.

If you are new to this project, start here. This folder contains everything needed to understand what Scanly is, how users interact with it, and how the codebase is built.

## What is Scanly?

Scanly is a local-only document scanner for Android. Users capture pages with the camera or import images from the gallery, correct page geometry, apply filters, organize multi-page documents into collections, and export PDFs or image archives — all without a backend or cloud sync.

| Attribute | Value |
| --- | --- |
| Platform | Android 10+ (API 29), targets API 36 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Single module (`:app`), clean-architecture-style layers |
| Data | Rebuildable Room index, SAF shared-library manifests/assets, DataStore preferences |
| Processing | LiteRT corner detection + OpenCV filters + CameraX capture |
| License | AGPL-3.0-only ([LICENSE](../LICENSE)) |
| Current version | `1.0.9` (version code `9`) |

## Core workflow

```
Capture or import image
  → Detect document corners (ML) or manual crop
  → Perspective correction + filter
  → Save as page in a local document
  → Organize in library / groups
  → Export PDF or share images
```

**Design principles:** offline-first, preserve raw captures, treat processed output as derived, keep manual controls when automation fails.

## Reading paths

Choose a path based on your goal:

| I want to… | Start with |
| --- | --- |
| Understand the product and features | [overview/what-is-scanly.md](overview/what-is-scanly.md) → [overview/features.md](overview/features.md) |
| Learn how users interact with the app | [overview/user-guide.md](overview/user-guide.md) |
| Understand code structure and layers | [architecture/overview.md](architecture/overview.md) |
| Trace navigation and screen flows | [architecture/navigation.md](architecture/navigation.md) |
| Understand persistence and storage | [data/database.md](data/database.md) → [data/file-storage.md](data/file-storage.md) |
| Understand the scan/processing pipeline | [processing/capture-and-scan.md](processing/capture-and-scan.md) → [processing/image-processing.md](processing/image-processing.md) |
| Build, test, or contribute code | [development/setup.md](development/setup.md) → [development/conventions.md](development/conventions.md) |
| Look up models, use cases, or dependencies | [reference/](reference/) |
| See release history | [releases.md](releases.md) |

## Documentation map

### Overview — product context

| Document | Contents |
| --- | --- |
| [what-is-scanly.md](overview/what-is-scanly.md) | Vision, principles, tech summary, repository layout |
| [features.md](overview/features.md) | Complete feature inventory by area |
| [user-guide.md](overview/user-guide.md) | End-user workflows: scan, edit, organize, export, settings |

### Architecture — how the app is built

| Document | Contents |
| --- | --- |
| [overview.md](architecture/overview.md) | Layers, packages, DI, connection maps |
| [navigation.md](architecture/navigation.md) | Routes, transitions, user flow diagrams |
| [screens.md](architecture/screens.md) | Every screen, ViewModel, and responsibility |

### Data — persistence and storage

| Document | Contents |
| --- | --- |
| [database.md](data/database.md) | Room schema v3, entities, migrations, DAOs |
| [file-storage.md](data/file-storage.md) | On-disk layout, raw/processed/thumbs, export cache |
| [settings-and-updates.md](data/settings-and-updates.md) | DataStore prefs, FAQs/licenses, GitHub/Google Play update variants |

### Processing — capture, ML, filters, export

| Document | Contents |
| --- | --- |
| [capture-and-scan.md](processing/capture-and-scan.md) | CameraX session, quality feedback, finalize flow |
| [image-processing.md](processing/image-processing.md) | LiteRT corners, perspective warp, filter presets |
| [export.md](processing/export.md) | PDF, image archive, group export, FileProvider share |

### Development — build and contribute

| Document | Contents |
| --- | --- |
| [setup.md](development/setup.md) | Prerequisites, build commands, release signing |
| [conventions.md](development/conventions.md) | Layer rules, adding screens, Room migrations |
| [testing.md](development/testing.md) | Test inventory, gaps, how to run tests |
| [releasing.md](development/releasing.md) | Version bump checklist, docs to update |

### Reference — quick lookup

| Document | Contents |
| --- | --- |
| [implementation-snapshot.md](reference/implementation-snapshot.md) | One-page technical snapshot |
| [tech-stack.md](reference/tech-stack.md) | Dependencies and versions from `libs.versions.toml` |
| [domain-models.md](reference/domain-models.md) | All domain model classes |
| [use-cases.md](reference/use-cases.md) | All 39 use cases grouped by area |
| [repository-layout.md](reference/repository-layout.md) | Root repo file and folder guide |

### Releases

| Document | Contents |
| --- | --- |
| [releases.md](releases.md) | Version policy, current release, links to changelog |

## Root-level documents (outside `docs/`)

These files live at the repository root for GitHub conventions and quick access:

| File | Purpose |
| --- | --- |
| [README.md](../README.md) | Public landing page with screenshots and quick start |
| [CHANGELOG.md](../CHANGELOG.md) | User-facing release notes |
| [VERSION.md](../VERSION.md) | Version metadata and upgrade notes |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | Contribution workflow |
| [SECURITY.md](../SECURITY.md) | Vulnerability reporting |
| [Agents.md](../Agents.md) | AI coding agent guidance |
| [LICENSE](../LICENSE) | AGPL-3.0-only |

## Key source locations

| Area | Path |
| --- | --- |
| Entry point | `app/src/main/java/in/c1ph3rj/scanly/MainActivity.kt` |
| Navigation | `app/src/main/java/in/c1ph3rj/scanly/navigation/` |
| Features (UI) | `app/src/main/java/in/c1ph3rj/scanly/feature/` |
| Domain logic | `app/src/main/java/in/c1ph3rj/scanly/domain/` |
| Data layer | `app/src/main/java/in/c1ph3rj/scanly/data/` |
| ML / processing | `app/src/main/java/in/c1ph3rj/scanly/core/` |
| Build config | `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| ML model | `app/src/main/assets/models/document_corners_float16.tflite` |

## Screenshots

UI screenshots are in [`../screenshots/`](../screenshots/). The root [README.md](../README.md) embeds them for GitHub display.
