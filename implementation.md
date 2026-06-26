# Scanly Implementation Notes

This document is a compact technical snapshot of the current repository state. Historical sprint artifacts remain under `docs/sprint-*` as archive material.

## Current Architecture

Scanly is organized as a single Android app module with clean package boundaries inside `app/src/main/java/in/c1ph3rj/scanly/`.

Primary areas:

- `ui/` – Compose theme and shared UI styling
- `feature/` – screen-level UI and ViewModels
- `navigation/` – destinations and app navigation host
- `domain/` – models, repositories, and use cases
- `data/` – Room, storage, export, processing, and repository implementations
- `core/` – shared utilities for ML, processing, editing, UI, and result handling
- `di/` – Hilt modules and dependency wiring

## Technology Stack

- Kotlin
- Jetpack Compose + Material 3
- Hilt for dependency injection
- Navigation Compose
- CameraX
- Room
- DataStore
- LiteRT
- OpenCV
- Coroutines and Flow

## Current Product Shape

The app currently centers on a local document workflow:

- home dashboard with recent documents and groups
- searchable library with document groups (collections)
- manual camera capture and scan sessions
- gallery import for new or existing documents
- page persistence in Room (schema version 3)
- durable scan-file storage under `Pictures/Scanly`, with startup recovery for missing Room index rows
- non-destructive page editing
- export/share flows for PDF and images, including group export
- settings with storage usage, clear-all-data, and support content

**Current version:** `1.0.4` (version code `4`). See `VERSION.md` and `CHANGELOG.md`.

## Engineering Principles

- Keep raw captures intact.
- Treat processing results as derived data.
- Prefer offline-first behavior.
- Keep scan binaries outside app-private storage so uninstall does not remove the user's documents.
- Keep manual controls available even when automation is imperfect.
- Separate UI, domain logic, and storage responsibilities.

## Public Release Notes

The codebase is being prepared for open-source publication and public collaboration:

- README content is normalized for public readers
- sprint documents are kept as archive summaries
- repository hygiene rules are tightened for build and IDE artifacts
- onboarding and support docs are kept in root-level markdown files
- `VERSION.md` and `CHANGELOG.md` track release metadata and user-facing changes

## Recommended Next Technical Focus

1. Keep improving capture and page-processing reliability.
2. Add/expand tests for geometry, persistence, and export behavior.
3. Maintain documentation when public-facing routes or feature names change.
4. Keep open-source release tasks in `OPEN_SOURCE_NEXT_STEPS.md`.
