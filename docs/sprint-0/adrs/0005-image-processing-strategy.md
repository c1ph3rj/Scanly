# ADR 0005: Image Processing Strategy

- Status: Accepted for Sprint 0

## Context

Scan quality depends on perspective correction, shading cleanup, and filter presets. That work should not be mixed into UI or repository code.

## Decision

Implement image processing behind a `PageProcessor` abstraction and prototype with an OpenCV-backed implementation first.

The processing pipeline will be separate from live preview inference.

## Consequences

- preview and still-image processing can evolve independently
- OpenCV can be replaced later if binary size or performance becomes a problem
- geometry and perspective logic can be tested without UI code
