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

- document library and document detail screens
- manual camera capture and scan sessions
- page persistence in Room
- non-destructive page editing
- export/share flows for PDF and images
- settings and support content

## Engineering Principles

- Keep raw captures intact.
- Treat processing results as derived data.
- Prefer offline-first behavior.
- Keep manual controls available even when automation is imperfect.
- Separate UI, domain logic, and storage responsibilities.

## Public Release Notes

The codebase is being cleaned for open-source publication:

- README content has been normalized for public readers
- sprint documents are being reduced to archive summaries
- repository hygiene rules are being tightened for build and IDE artifacts
- additional onboarding docs are kept in root-level markdown files

## Recommended Next Technical Focus

1. Keep improving capture and page-processing reliability.
2. Add/expand tests for geometry, persistence, and export behavior.
3. Maintain documentation when public-facing routes or feature names change.
4. Keep open-source release tasks in `OPEN_SOURCE_NEXT_STEPS.md`.
