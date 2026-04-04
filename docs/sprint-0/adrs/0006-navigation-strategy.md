# ADR 0006: Navigation Strategy

- Status: Accepted for Sprint 0

## Context

The product flow will move between library, camera, review, editor, and export screens. Those screens will need stable route contracts and back-stack behavior.

## Decision

Use Navigation Compose in Sprint 1 and keep route definitions isolated under a dedicated `navigation` package.

## Consequences

- screen routes stay explicit instead of spreading through UI code
- feature slices can own their own navigation entry points
- camera and editor flows can be tested as separate destinations later
