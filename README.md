# Scanly

Scanly is an Android document scanner app focused on fast capture, clean processing, and easy export.

The goal is to let users scan paper documents (receipts, notes, IDs, contracts) with a phone camera and produce high-quality digital output with manual controls when needed.

## Overview

Scanly is designed as a workflow-first scanner:

1. Capture from camera
2. Detect document edges
3. Correct perspective and lighting
4. Edit each page if needed
5. Save as a multipage document
6. Export as PDF or image set

## Current Status

This repository currently contains a Compose-first Android app scaffold:

- Single module: `:app`
- Entry point: `app/src/main/java/in/c1ph3rj/scanly/MainActivity.kt`
- Theme files under `app/src/main/java/in/c1ph3rj/scanly/ui/theme/`
- Basic unit/instrumented test scaffolds
- Sprint 0 is a documentation-first technical validation pass because no Android-ready model artifact is checked in yet
- The app should stay manual-first until the mobile runtime, tensor contract, and performance budget are proven
- Sprint 0 notes live in `docs/sprint-0-validation.md`

Scanner-specific features listed below are the intended product scope.

## Planned Product Capabilities

### 1) Document Library (Home)
- Home screen shows all captured documents.
- Each document can contain multiple scanned pages.
- Typical actions: open, rename, delete, export/share.

### 2) Smart Camera + Auto-Capture
- Live camera feed detects document boundaries.
- Shows a transparent blue overlay around detected page.
- If stable, shows "Hold steady" and starts `3 -> 2 -> 1` countdown.
- Captures automatically after countdown.
- Manual shutter remains available.

### 3) Auto-Capture Toggle
- User can switch auto-capture ON/OFF.
- OFF mode supports manual capture only.

### 4) Automatic Image Processing
For each captured page, pipeline should:
- detect edges,
- crop,
- perspective-correct (flatten),
- improve shading/lighting,
- produce clean scan-like output.

### 5) Multi-Page Documents
- Users can scan multiple pages under one document.
- Pages remain grouped for review and export.

### 6) Per-Page Editing
Each page can be edited independently:
- rotate left/right,
- re-crop,
- apply preset document filters,
- confirm/save edits.

### 7) Freeform 4-Point Cropper
- If auto-crop is imperfect, user adjusts four corners manually.
- Crop handles are prefilled using detected edges for faster correction.

### 8) Export
- Export as one merged PDF.
- Export as individual images.
- Share through Android share flows.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin with Gradle wrapper
- clean architecture.
- LiteRT float16 model for document-corner prediction, with a fallback evaluation path for ONNX Runtime Mobile if needed.
