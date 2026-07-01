# Shared Library Storage

Scanly stores its durable library in a user-selected local Storage Access Framework folder. A typical location is `Documents/Scanly`. The folder survives uninstall; after reinstall, Android requires the user to select it once again before Scanly can rebuild Room.

```text
Scanly/
├── library.json
├── catalog/catalog-r000000000042.json
├── documents/{documentId}/
│   ├── manifests/document-r000000000008.json
│   ├── raw/{pageId}-{captureId}.jpg
│   ├── processed/{pageId}-r{revision}.jpg
│   └── thumbs/{pageId}-r{revision}.jpg
├── groups/{groupId}/group-r{revision}.json
├── operations/
├── tombstones/
└── .nomedia
```

## Rules

- Raw captures are immutable. Retake writes a new capture before switching the page manifest.
- Processed images and thumbnails use revisioned filenames.
- Manifests store only validated relative paths, sizes, MIME types, revisions, and SHA-256 checksums.
- The newest two manifest and catalog revisions are retained.
- Tombstones prevent a partially deleted folder from being rediscovered.
- The catalog accelerates delta startup but can be rebuilt from manifests.
- Unknown user files in the selected folder are never deleted.

Camera capture and processing use `cache/library-work/`. Shared images can be materialized into `cache/asset-cache/` for bitmap APIs that require a file. Both caches are disposable. PDF/ZIP artifacts remain in `cache/exports/`.

## Settings operations

- **Clear temporary cache** removes work, decoded-asset, thumbnail, and export caches.
- **Rebuild library index** discards Room and reconstructs it from shared storage.
- **Reconnect or switch library** opens the system folder picker.
- **Permanently delete library** removes only Scanly-owned shared directories after confirmation.
