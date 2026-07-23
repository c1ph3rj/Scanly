# Library Backup and Restore

Scanly creates portable, local `.scanly` archives from **Settings → Storage & backup**.

## Destination

- Default exports: `Downloads/Scanly/`
- Default backups: `Downloads/Scanly/backup/`
- Custom destination: selected tree for exports, with a Scanly-managed lowercase `backup/` child

Backup is enabled only when the library is non-empty, the destination is writable, and its provider reports at least the source size plus 5% or 16 MiB, whichever is larger. The worker repeats this check immediately before writing.

## Format version 1

`.scanly` is a DEFLATE ZIP whose first entry is `manifest.json`. The manifest contains the signature and format version, source app version and timestamp, groups/documents/pages with edit metadata (including crop coords, rotation, filter preset, optional filter adjustment floats, scan mode, and optional ID pair/side metadata), relative asset paths, byte sizes, and SHA-256 checksums. Document records also carry the preferred scan mode and optional ID/Book filter preferences. Raw captures, processed files, page thumbnails, and covers are included exactly. Preferences, SQLite/WAL files, export cache, and other backups are excluded.

Archives written before filter adjustments omit the four floats; restore fills `filterBrightness` / `filterContrast` / `filterSaturation` / `filterSharpness` with `0` (identity).

Archives written before scan modes omit the new fields; restore defaults both documents and pages to `document` and leaves ID pair/side and per-mode filter preferences unset. Format version remains 1 because all additions are optional and backward compatible.

## Restore safety

Restore rejects unsupported versions, malformed relationships or page order, duplicate IDs/paths, unsafe paths, unexpected or missing entries, size mismatches, checksum failures, and insufficient staging space. Files are extracted into an app-private workspace before any database change.

- **Merge** remaps every group/document/page ID and suffixes duplicate titles.
- **Replace** stages first, transactionally swaps Room records, then removes old document roots.

A durable recovery journal records generated and previous roots. On interruption, the next restore determines whether the database transaction committed and removes only the orphaned side. External completed backups are never removed by Clear all data.

## Background execution

Backup and restore are unique WorkManager operations using a foreground `dataSync` notification. In-app progress exposes validation, archive/restore, and finalization phases. Library mutation repositories share an operation coordinator, so writes pause while an exact snapshot or restore is active; read-only browsing continues.
