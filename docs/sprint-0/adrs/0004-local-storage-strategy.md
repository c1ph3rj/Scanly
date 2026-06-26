# ADR 0004: Local Storage Strategy

- Status: Superseded after durable-document-storage update

## Context

The app needs multi-page documents, re-editable pages, and safe exports without a backend.

## Decision

Use:

- Room for document and page metadata
- shared device media storage for raw, processed, and thumbnail scan images
- app-private cache for temporary export artifacts

Raw captures are never overwritten.

Room remains the fast library index. On startup, Scanly migrates legacy
`filesDir/documents` content into `Pictures/Scanly` and can rebuild missing
Room rows from persistent scan files when the local database is missing.

## Consequences

- editing stays non-destructive
- exports can be regenerated later
- the database remains small and focused on metadata
- scan files are not removed by a normal uninstall
- Scanly's in-app clear-all-data action must delete the persistent scan files
  as well as Room metadata
