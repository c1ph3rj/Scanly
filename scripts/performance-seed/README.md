# Scanly Performance Library Seed

Generates ~250–300 documents with real internet images and pushes them to a connected Android device's Scanly library folder for performance testing.

## Prerequisites

- USB debugging enabled on the device
- Scanly installed and connected to a library folder
- Python 3 with Pillow (`pip install Pillow`)
- `adb` on your PATH

## Quick start

```powershell
# From repo root
adb devices
./scripts/performance-seed/Seed-PerformanceLibrary.ps1 -DocumentCount 280
```

The script will:

1. Read your library folder from Scanly's DataStore (or use `-LibraryPath`)
2. Pull the existing catalog (append mode — keeps current documents)
3. Download ~50 internet images (Wikimedia + Picsum fallback)
4. Generate valid manifests, JPEG assets, and thumbnails
5. Push new documents/groups/catalog to the device
6. Launch Scanly for automatic delta sync

## Options

| Flag | Default | Description |
|------|---------|-------------|
| `-DocumentCount` | `280` | Number of new documents to add |
| `-LibraryPath` | auto | Override device path (e.g. `/storage/emulated/0/Download/Scanly`) |
| `-SkipLaunch` | off | Do not launch the app after push |
| `-DryRun` | off | Generate locally without pushing |

## Manual library path

If auto-detection fails, pass the path shown in Scanly Settings:

```powershell
./scripts/performance-seed/Seed-PerformanceLibrary.ps1 -LibraryPath "/storage/emulated/0/Download/Scanly"
```

## Dataset shape

- 12 new groups (Work, Receipts, Medical, …) + ~24 ungrouped documents
- Mixed page counts: 1–10 pages per document (~600+ pages total)
- Realistic titles and timestamps spread over 18 months
- JPEG raw/processed/thumb assets with valid SHA-256 checksums

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `No adb device connected` | Run `adb devices`, accept the debugging prompt |
| `Could not resolve library path` | Use `-LibraryPath` with your folder path |
| `Pillow is required` | `pip install Pillow` |
| App doesn't show new docs | Open Scanly, wait for sync, or Settings → Rebuild library index |
| Some images fail to download | Cached/fallback images are used automatically; check `.cache/` |

## Files

| File | Purpose |
|------|---------|
| `seed_library.py` | Manifest and asset generator |
| `image_sources.json` | Curated internet image URLs |
| `Seed-PerformanceLibrary.ps1` | adb orchestration |
| `.cache/` | Downloaded image cache (gitignored) |
| `.staging/` | Local pull/generation workspace (gitignored) |

## Verify

After seeding, the Library tab should show your existing documents plus ~280 new ones. Group detail screens should list documents per folder. Scroll through the library to assess performance.