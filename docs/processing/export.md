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

Uses Android `PdfDocument` for page rendering. Password-protected output is then encrypted with PdfBox-Android using AES and a 256-bit encryption key. The temporary unprotected file is deleted immediately after encryption.

### Options (`PdfExportOptions`)

| Option | Values |
| --- | --- |
| Password | Off, or a validated 4–64 character open password |
| Page number | None, lower left, bottom center, lower right |
| Page size | Auto fit, A3, A4, A5, B4, B5, Letter, Tabloid, Legal, Executive, Postcard, American foolscap, European foolscap |
| Orientation | Auto per page, portrait, landscape |
| Margins | None, small, large |

Fixed paper sizes are encoded in PostScript points (72 points per inch), so exported PDFs retain real print dimensions. Auto fit uses each source image's aspect ratio with an A4-length long edge. When numbering is enabled, the renderer reserves a footer instead of drawing over the scan.

### Page source

- Uses **processed** shared asset references from Room
- Falls back to **raw** and then thumbnail assets
- Materializes shared assets into a bounded app cache for Android PDF/ZIP APIs
- Pages rendered in `pageIndex` order

### Group merged PDF

1. Load all documents in group (sorted by title).
2. For each document, load pages in index order.
3. Append all pages sequentially into one PDF.
4. Page numbers, when enabled, continue across document boundaries.

### Group zipped PDFs

1. Generate one PDF per document in the group.
2. Package all PDFs into a single ZIP.
3. Page numbers, when enabled, restart at 1 for each PDF.

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
