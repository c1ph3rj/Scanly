# ADR 0004: Local Storage Strategy

- Status: Accepted for Sprint 0

## Context

The app needs multi-page documents, re-editable pages, and safe exports without a backend.

## Decision

Use:

- Room for document and page metadata
- app-private files for raw, processed, thumbnail, and export artifacts

Raw captures are never overwritten.

## Consequences

- editing stays non-destructive
- exports can be regenerated later
- the database remains small and focused on metadata
