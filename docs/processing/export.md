# Export and Share

How Scanly generates PDFs and image archives for documents and groups.

## Overview

`DefaultDocumentExportRepository` (`data/export/`) handles all export and share operations. ViewModels call export use cases; use cases delegate to the repository.

## Export types

### Single document

| Type | Use case | Output |
| --- | --- | --- |
| PDF | `ExportDocumentPdfUseCase` | Single PDF in export cache |
| Image archive | `ExportDocumentImageArchiveUseCase` | ZIP of processed JPEGs |
| Share PDF | `PrepareDocumentPdfShareUseCase` | `ShareArtifact` with FileProvider URI |
| Share images | `PrepareDocumentImageShareUseCase` | `ShareArtifact` with FileProvider URI |

### Group

| Type | Use case | Output |
| --- | --- | --- |
| Merged PDF | `ExportGroupPdfUseCase` | One PDF with all pages from all group documents |
| Zipped PDFs | `ExportGroupZippedPdfsUseCase` | ZIP with one PDF per document |
| Share merged PDF | `PrepareGroupPdfShareUseCase` | Share artifact |
| Share zipped PDFs | `PrepareGroupZippedPdfsShareUseCase` | Share artifact |

## PDF generation

Uses Android `PdfDocument` API.

### Options (`PdfExportOptions`)

| Option | Values |
| --- | --- |
| Page size | A4, Letter, fit-to-content |
| Orientation | Portrait, landscape |
| Margins | Configurable |

### Page source

- Uses **processed** image paths from Room
- Falls back to **raw** if processed is unavailable
- Pages rendered in `pageIndex` order

### Group merged PDF

1. Load all documents in group (sorted by title).
2. For each document, load pages in index order.
3. Append all pages sequentially into one PDF.

### Group zipped PDFs

1. Generate one PDF per document in the group.
2. Package all PDFs into a single ZIP.

## Image archive

ZIP containing processed JPEGs (raw fallback) for all pages in a document. One file per page, named by page index.

## Export cache paths

```
cache/exports/{documentId}/          # Document exports
cache/exports/group_{groupId}/       # Group exports
```

Cache is ephemeral — safe to delete without losing library data. Included in storage usage and cleared by clear-all-data.

## Share flow

1. Export repository writes file to cache.
2. Returns `ShareArtifact` or `ExportArtifact` with file path.
3. ViewModel creates `FileProvider` URI: `${applicationId}.fileprovider`
4. Android share sheet opens with `grantUriPermissions`.

FileProvider configured in `AndroidManifest.xml` with paths in `res/xml/file_paths.xml`.

## Progress reporting

Group export operations support progress callbacks consumed by `GroupDetailViewModel` for UI progress indicators during long exports.

## Domain models

| Model | Purpose |
| --- | --- |
| `PdfExportOptions` | PDF layout configuration |
| `ExportArtifact` | Export result with file path |
| `ShareArtifact` | Share-ready result with URI metadata |

## Related docs

- [../data/file-storage.md](../data/file-storage.md) — export cache layout
- [../architecture/screens.md](../architecture/screens.md) — which ViewModels trigger export
- [../overview/user-guide.md](../overview/user-guide.md) — user-facing export steps