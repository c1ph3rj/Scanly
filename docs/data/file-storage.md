# File Storage

Scanly stores document images in **app-private directories** managed by `AppPrivateDocumentStorageManager` (`data/storage/`).

Image paths referenced in Room entities point to files under these directories. The database stores metadata; the filesystem stores pixels.

## Per-document layout

```
files/documents/{documentId}/
├── raw/
│   └── page_001.jpg, page_002.jpg, …    # Immutable captures
├── processed/
│   ├── page_001.jpg, page_002.jpg, …    # Derived corrected/filtered output; signed composite when marked
│   └── page_001.base.jpg                # Clean image retained while page marks are editable
├── markups/
│   └── {markId}.png                     # Private copies of signature images placed on pages
└── thumbs/
    ├── cover.jpg                         # Document cover thumbnail
    └── page_001.jpg, page_002.jpg, …     # Page list thumbnails
```

| Directory | Mutability | Purpose |
| --- | --- | --- |
| `raw/` | **Immutable** | Original camera capture or imported image |
| `processed/` | Regenerated on edit | Perspective-corrected, filtered JPEG |
| `processed/*.base.jpg` | Regenerated on edit | Clean pre-signing page retained while editable marks exist |
| `markups/` | Regenerated on edit | Page-owned copies of placed signature images |
| `thumbs/` | Regenerated on edit | Small previews for lists and covers |

Reusable signature ink masks are stored separately under `files/signatures/{signatureId}/ink.png`. They stay on this device; page markups keep private image copies so deleting a reusable signature does not change a signed page.

## Design rules

1. **Never overwrite raw files.** Edits call `reprocessPage`, which reads from `raw/` and writes new `processed/` and `thumbs/` paths.
2. **Paths in Room are authoritative.** Repositories update entity path columns after every write.
3. **Document root is stored** in `DocumentEntity.rootDirectoryPath` for cleanup and migration.

## App-private working directories

| Path | Purpose |
| --- | --- |
| `cache/library-archive/` | Legacy restore workspace; cleaned up after the backup/restore feature was removed |
| `files/library-archive-journal/` | Legacy restore journal; cleaned up after the backup/restore feature was removed |
| `databases/scanly.db` (+ `-wal`, `-shm`) | Room database |

## Export cache

Ephemeral export artifacts live outside document directories:

```
cache/exports/{documentId}/     # Single-document PDF or ZIP
cache/exports/group_{groupId}/  # Group merged PDF or zipped PDFs
```

Export cache can be cleared without losing library data. It is included in storage usage reporting and wiped by clear-all-data.

## User-visible exports

Generated cache artifacts are copied to shared storage when the user chooses Save:

```text
Downloads/Scanly/                 # Default direct-save destination
```

A Storage Access Framework tree selected in Settings replaces `Downloads/Scanly` as the export base. Persisted access is required, existing names receive numeric suffixes, and partial writes are removed after failure or cancellation.

Library backup and restore were removed in v1.0.18. See [library-backup.md](library-backup.md) for the retained legacy format notes.

## Thumbnail cache

`ThumbnailCache` (`core/ui/`) maintains an in-memory cache of decoded thumbnails for fast list scrolling. Invalidated when:

- A page is reprocessed
- A document cover changes
- Clear-all-data runs

## Capture path allocation

Before capture or import:

1. `PageRepository.prepareCapture` or `prepareReplacementCapture` allocates the next `raw/page_NNN.jpg` path.
2. Camera or gallery writes the raw JPEG to that path.
3. `finalizeCapture` runs processing and writes `processed/` and `thumbs/`.

## Image format settings

Processed JPEG output (from `PageImageProcessor`):

- Quality: 94
- Max dimension: 2800 px (longer edge; processing is capped at the same size, see [../processing/image-processing.md](../processing/image-processing.md))

## Storage usage reporting

`GetAppStorageUsageUseCase` → `DefaultAppDataRepository` reports:

| Category | What it measures |
| --- | --- |
| Documents | All files under `files/documents/` |
| Export cache | All files under `cache/exports/` |
| Database & search index | `scanly.db` and its `-wal` / `-shm` files, including the page-text search index |

Displayed in Settings via `StorageFormatter`. Legacy archive workspaces are not counted; they are removed during upgrade cleanup.

## Clear all data

`ClearAllAppDataUseCase` performs a full wipe:

1. `database.clearAllTables()` — all Room records
2. Delete every directory under `files/documents/`
3. Delete `files/signatures/`
4. Delete `cache/exports/`
5. `thumbnailCache.clearAll()`
6. Delete leftover legacy backup/restore workspace files

**Destructive and irreversible.** Requires user confirmation in Settings.

## Related docs

- [database.md](database.md) — Room entities referencing these paths
- [../processing/image-processing.md](../processing/image-processing.md) — how processed files are generated
- [../processing/export.md](../processing/export.md) — export cache usage
