# Database (Room)

Scanly persists document metadata in a **Room** database at schema version **7**.

## Database file

| Property | Value |
| --- | --- |
| Class | `ScanlyDatabase` |
| Path | `databases/scanly.db` (+ `-wal`, `-shm`) under app internal storage |
| Schema version | `7` |
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
| `filterPreset` | TEXT | `PageFilterPreset.storageValue` (legacy values are remapped by `MIGRATION_4_5`) |
| `filterBrightness` | REAL | Post-filter brightness tweak (`-1..1`, default 0) |
| `filterContrast` | REAL | Post-filter contrast tweak (`-1..1`, default 0) |
| `filterSaturation` | REAL | Post-filter saturation tweak (`-1..1`, default 0) |
| `filterSharpness` | REAL | Post-filter sharpness (`0..1`, default 0) |
| `filterIntensity` | REAL | Preset strength (`0..1.5`, default 1; `1` is the tuned look, `0` is lighting correction only). Added in schema 5 |
| `processingState` | TEXT | `CAPTURED`, `PROCESSED`, or `NEEDS_REVIEW` |
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

### `page_texts` (`PageTextEntity`)

One row per scanned page, with recognized text, an optional user correction, the text used for search, normalized text-layout blocks, source image dimensions, and recognition timestamps. `pageId` is unique and references `scan_pages` with cascade delete; `documentId` is indexed.

`page_texts_fts` is an FTS4 external-content index over `searchText`. Room-managed triggers keep it synchronized. Rows are replaced with delete + insert so the triggers run. `page_texts_terms` is an FTS4 vocabulary table created idempotently when the database opens.

### `signatures` (`SignatureEntity`)

Saved signatures and initials with source type, app-private image path, dimensions, and creation/update timestamps. Indexed by `updatedAtMillis`.

### `page_markups` (`PageMarkupEntity`)

One row per signed Scanly page, containing serialized marks, the clean base-image path, and update time. `pageId` is the primary key and cascades with its `scan_pages` row. The visible `processed` image contains the composite; the base image lets the user edit or remove marks later.

## DAOs

| DAO | Key operations |
| --- | --- |
| `DocumentDao` | Observe all/recent/ungrouped/by-group; CRUD; update snapshots |
| `ScanPageDao` | Observe by document; CRUD; reorder (update indices) |
| `DocumentGroupDao` | Observe all/recent; CRUD; join stats (doc count, page count, cover) |
| `PageTextDao` | Read/write per-page OCR text, FTS search, and index progress |
| `SignatureDao` | Observe, save, and delete saved signatures |
| `PageMarkupDao` | Read/write per-page markups |

## Migrations

### `MIGRATION_1_2`

Adds `preferredFilterPreset TEXT` column to `documents`.

### `MIGRATION_2_3`

1. Creates `document_groups` table.
2. Rebuilds `documents` with `groupId` FK (SQLite table-recreation pattern).
3. Copies existing rows with `groupId = NULL` (all pre-1.0.4 documents stay ungrouped).
4. Recreates indexes.

### `MIGRATION_3_4`

Adds per-page filter customization columns on `scan_pages` (all `REAL NOT NULL DEFAULT 0`):

- `filterBrightness`
- `filterContrast`
- `filterSaturation`
- `filterSharpness`

Registered in `DatabaseModule` with `MIGRATION_1_2` and `MIGRATION_2_3`.

### `MIGRATION_4_5`

1. Adds `filterIntensity REAL NOT NULL DEFAULT 1` to `scan_pages`. Existing pages get the default intensity, which is the tuned look.
2. Remaps presets removed in the filter overhaul. For each legacy value, `scan_pages.filterPreset` and `documents.preferredFilterPreset` are rewritten to the replacement:

| Legacy value | Replacement |
| --- | --- |
| `enhanced_color` | `natural_color` |
| `shadow_reduction` | `magic_color` |
| `clean` | `grayscale` |
| `soft_black_and_white` | `faded` |
| `receipt` | `faded` |

The mapping comes from `PageFilterPreset.legacyStorageMigrations`, so the migration and `PageFilterPreset.fromStorage` stay in sync. Registered in `DatabaseModule` after `MIGRATION_3_4`.

### `MIGRATION_5_6`

Creates `page_texts`, its FTS4 external-content table, the page/document indexes, and synchronization triggers. Existing pages have no text rows yet; the background indexer recognizes them after upgrade.

### `MIGRATION_6_7`

Creates `signatures` and `page_markups`, including the signature timestamp index and the markup-to-page foreign key. Existing document pages and their images are unchanged.

## Repository mapping

| Repository | DAOs used |
| --- | --- |
| `DefaultDocumentRepository` | `DocumentDao` |
| `DefaultPageRepository` | `ScanPageDao`, `DocumentDao` |
| `DefaultGroupRepository` | `DocumentGroupDao`, `DocumentDao` |
| `DefaultDocumentExportRepository` | `DocumentDao`, `ScanPageDao`, `DocumentGroupDao` |
| `DefaultAppDataRepository` | `ScanlyDatabase.clearAllTables()` |
| `DefaultPageTextRepository` | `PageTextDao`, `ScanPageDao`, `DocumentDao` |
| `DefaultSignatureRepository` | `SignatureDao` and app-private signature image files |
| `DefaultPageMarkupRepository` | `PageMarkupDao`, page/document DAOs, page image files |

## Upgrade notes

- Upgrading from v1.0.0: Room migrates automatically from schema 1 or 2 through schema 7.
- Existing documents remain; they appear ungrouped until moved into a collection.
- Upgrading from schema 5: Room runs `MIGRATION_5_6` and `MIGRATION_6_7`; page-text indexing resumes in the background.
- Upgrading from schema 6: `MIGRATION_6_7` adds saved signatures and editable page markups.
- No manual migration steps required.

See [../releases.md](../releases.md) and [../../VERSION.md](../../VERSION.md).

## Related docs

- [file-storage.md](file-storage.md) — image files referenced by entity paths
- [../development/conventions.md](../development/conventions.md) — how to add migrations
