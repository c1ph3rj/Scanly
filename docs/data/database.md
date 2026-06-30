# Database (Room)

Scanly persists document metadata in a **Room** database at schema version **3**.

## Database file

| Property | Value |
| --- | --- |
| Class | `ScanlyDatabase` |
| Path | `app_database/scanly.db` (app-internal) |
| Schema version | `3` |
| `exportSchema` | `false` |

Location: `app/src/main/java/in/c1ph3rj/scanly/data/local/db/ScanlyDatabase.kt`

## Entities

### `documents` (`DocumentEntity`)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | UUID |
| `title` | TEXT | User-visible name |
| `pageCount` | INTEGER | Denormalized count |
| `coverThumbnailPath` | TEXT? | Path to cover thumb |
| `preferredFilterPreset` | TEXT? | Default filter for new pages |
| `rootDirectoryPath` | TEXT | App-private document directory |
| `createdAtMillis` | INTEGER | Creation timestamp |
| `updatedAtMillis` | INTEGER | Last modification |
| `groupId` | TEXT? FK | → `document_groups.id`, `ON DELETE SET NULL` |

Indexes: `updatedAtMillis`, `groupId`

### `scan_pages` (`ScanPageEntity`)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | UUID |
| `documentId` | TEXT FK | → `documents.id`, `CASCADE` |
| `pageIndex` | INTEGER | Order within document (0-based) |
| `rawImagePath` | TEXT | Immutable capture |
| `processedImagePath` | TEXT? | Derived corrected image |
| `thumbnailPath` | TEXT? | List thumbnail |
| `cropTopLeftX/Y` … `cropBottomRightX/Y` | REAL | Normalized crop quad (8 values) |
| `rotationDegrees` | INTEGER | User rotation |
| `filterPreset` | TEXT | `PageFilterPreset.storageValue` |
| `processingState` | TEXT | `PROCESSED` or `NEEDS_REVIEW` |
| `createdAtMillis` | INTEGER | Creation timestamp |

Unique constraint: `(documentId, pageIndex)`

### `document_groups` (`DocumentGroupEntity`)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | UUID |
| `title` | TEXT | Group name |
| `createdAtMillis` | INTEGER | Creation timestamp |
| `updatedAtMillis` | INTEGER | Last modification |

Index: `updatedAtMillis`

## DAOs

| DAO | Key operations |
| --- | --- |
| `DocumentDao` | Observe all/recent/ungrouped/by-group; CRUD; update snapshots |
| `ScanPageDao` | Observe by document; CRUD; reorder (update indices) |
| `DocumentGroupDao` | Observe all/recent; CRUD; join stats (doc count, page count, cover) |

## Migrations

### `MIGRATION_1_2`

Adds `preferredFilterPreset TEXT` column to `documents`.

### `MIGRATION_2_3`

1. Creates `document_groups` table.
2. Rebuilds `documents` with `groupId` FK (SQLite table-recreation pattern).
3. Copies existing rows with `groupId = NULL` (all pre-1.0.4 documents stay ungrouped).
4. Recreates indexes.

Registered in `DatabaseModule` alongside `ScanlyDatabase` construction.

## Repository mapping

| Repository | DAOs used |
| --- | --- |
| `DefaultDocumentRepository` | `DocumentDao` |
| `DefaultPageRepository` | `ScanPageDao`, `DocumentDao` |
| `DefaultGroupRepository` | `DocumentGroupDao`, `DocumentDao` |
| `DefaultDocumentExportRepository` | `DocumentDao`, `ScanPageDao`, `DocumentGroupDao` |
| `DefaultAppDataRepository` | `ScanlyDatabase.clearAllTables()` |

## Upgrade notes

- Upgrading from v1.0.0: Room migrates automatically from schema 1 or 2 to 3.
- Existing documents remain; they appear ungrouped until moved into a collection.
- No manual migration steps required.

See [../releases.md](../releases.md) and [../../VERSION.md](../../VERSION.md).

## Related docs

- [file-storage.md](file-storage.md) — image files referenced by entity paths
- [../development/conventions.md](../development/conventions.md) — how to add migrations