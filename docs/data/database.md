# Operational Database Index

Scanly uses `scanly-index.db` as a fast, rebuildable Room index. It is the only data source observed by screens and ViewModels after startup reaches `READY`, but it is not the durable owner of the library.

## Tables

| Table | Purpose |
| --- | --- |
| `documents` | Document metadata, group link, denormalized page count/cover, manifest revision/checksum |
| `scan_pages` | Page order, edit metadata, and shared `LibraryAssetRef` values |
| `document_groups` | Group metadata plus manifest revision/checksum |
| `library_state` | Connected library ID and applied shared catalog generation |
| `manifest_fingerprints` | Revision/checksum last applied for every document and group |

The database schema starts at version 1 under the new filename. The former `scanly.db` database is not migrated.

## Startup synchronization

`LibraryIndexSynchronizer` compares `library_state.appliedGeneration` with the newest shared catalog:

- Matching generations open immediately without scanning manifests.
- Changed records are imported as one Room transaction.
- Missing, corrupt, or foreign indexes are rebuilt completely from shared manifests.
- Same-revision checksum conflicts stop startup in a repair state.

Every successful mutation commits shared storage first and Room second. A crash between those steps is repaired from the newer shared generation on the next splash.

See [file-storage.md](file-storage.md).
