# File Storage

Scanly stores document images in **app-private directories** managed by `AppPrivateDocumentStorageManager` (`data/storage/`).

Image paths referenced in Room entities point to files under these directories. The database stores metadata; the filesystem stores pixels.

## Per-document layout

```
files/documents/{documentId}/
├── raw/
│   └── page_001.jpg, page_002.jpg, …    # Immutable captures
├── processed/
│   └── page_001.jpg, page_002.jpg, …    # Derived corrected/filtered output
└── thumbs/
    ├── cover.jpg                         # Document cover thumbnail
    └── page_001.jpg, page_002.jpg, …     # Page list thumbnails
```

| Directory | Mutability | Purpose |
| --- | --- | --- |
| `raw/` | **Immutable** | Original camera capture or imported image |
| `processed/` | Regenerated on edit | Perspective-corrected, filtered JPEG |
| `thumbs/` | Regenerated on edit | Small previews for lists and covers |

## Design rules

1. **Never overwrite raw files.** Edits call `reprocessPage`, which reads from `raw/` and writes new `processed/` and `thumbs/` paths.
2. **Paths in Room are authoritative.** Repositories update entity path columns after every write.
3. **Document root is stored** in `DocumentEntity.rootDirectoryPath` for cleanup and migration.

## Export cache

Ephemeral export artifacts live outside document directories:

```
cache/exports/{documentId}/     # Single-document PDF or ZIP
cache/exports/group_{groupId}/  # Group merged PDF or zipped PDFs
```

Export cache can be cleared without losing library data. It is included in storage usage reporting and wiped by clear-all-data.

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
- Max dimension: 2400 px (longer edge)

## Storage usage reporting

`GetAppStorageUsageUseCase` → `DefaultAppDataRepository` reports:

| Category | What it measures |
| --- | --- |
| Documents | All files under `files/documents/` |
| Export cache | All files under `cache/exports/` |
| Database | `scanly.db` file size |

Displayed in Settings via `StorageFormatter`.

## Clear all data

`ClearAllAppDataUseCase` performs a full wipe:

1. `database.clearAllTables()` — all Room records
2. Delete every directory under `files/documents/`
3. Delete `cache/exports/`
4. `thumbnailCache.clearAll()`

**Destructive and irreversible.** Requires user confirmation in Settings.

## Related docs

- [database.md](database.md) — Room entities referencing these paths
- [../processing/image-processing.md](../processing/image-processing.md) — how processed files are generated
- [../processing/export.md](../processing/export.md) — export cache usage