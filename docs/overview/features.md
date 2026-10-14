# Features

Complete inventory of Scanly features as of **v1.0.17**.

## Home dashboard

- **Search bar** under the greeting opens library search (same field style as the Library)

- Shows up to **8 recent documents** and **6 recent groups** as paper-shaped cards (same language as Library)
- Quick actions: **Scan**, **Import**, **Folder**
- **Gallery import** — pick up to 10 images to start a new document
- **Default names** — new document and folder dialogs open with a duplicate-safe suggested name selected; a clear control empties the field, and **Suggest name** cycles date-based formats
- Shortcut into the full Library
- Adaptive layout: bottom navigation on phone, navigation rail on tablet

## Library

- Primary place to search, browse, and manage all content
- **Three filter pills:** All, Folders (groups), Documents — rounded Material 3 surfaces (not underline tabs)
- **Search** across document and group titles
- **Six sort options** (name, date created, date updated — ascending and descending)
- Paper-shaped document and folder cards with title and compact metadata; tap opens the item
- Rename, move, delete, and remove-from-folder from a single overflow or long-press menu (not always-visible icon rows)
- Create, rename, and delete documents and groups; new items start with a suggested name plus clear / **Suggest name**
- Move documents between groups or create a new group inline when moving
- Open document detail or group detail from any list item
- **All tab semantics:** foldered documents appear under their group, not in the main All list (unless searching)

## Document scanning (camera session)

- **Camera permission** — system prompt on first Scan/QR; in-app retry if denied; **Open Settings** only after a permanent block
- **CameraX**-based manual capture with live preview
- **Physical-document semantic gate** — rejects digital screens and non-documents before corner inference (optional; Settings toggle)
- **ML corner overlay** — LiteRT multi-model detection (Lite / Standard / High / Accurate) with independent live vs post-processing selection
- **Automatic model selection** — on by default; on-device calibration picks the best models within latency budgets
- **Stable outlines** — temporal confirmation, High verification for ambiguous quads, and worst-corner stability against nearby false edges
- **Book-page isolation** — gutter-aware trim for off-centre adjacent pages; ambiguous two-page spreads ask the user to move closer
- **Quality feedback** — lighting, blur, lens obstruction, framing guidance
- **Auto-capture** — stability tracker phases (`SEARCHING` → `HOLD_STEADY` → `COUNTDOWN` → `CAPTURING` → `COOLDOWN`) gate automatic shutter when the frame is steady
- **Manual capture** — tap shutter at any time
- **Torch/flash** toggle and **alignment grid** toggle
- **Tap-to-focus** on preview
- **Multi-page sessions** — capture multiple pages into one document in a single session
- **Page replacement (retake)** — replace an existing page; returns to editor after capture (v1.0.9)
- Portrait and landscape layouts with theme-aligned controls (v1.0.9)
- Live camera keeps a clean overlay (no permanent model/latency HUD); use **Model benchmark** for measurements

## Gallery import

- Import up to **10 images** per pick from the device photo picker
- Available on **Home**, **Tools**, and **Document detail** (extend existing documents)
- Reuses the same capture finalize pipeline as camera captures

## Tools hub

Fourth bottom-nav tab, grouped into **Scan**, **PDF**, and **Utilities**:

- **Scan a document** — create a document and open the camera session
- **Images to PDF** — gallery import (max 10 images) into a new document
- **QR code** — opens straight into scanning; Create is a tab on the same screen

### QR code

- **Scan** — CameraX + ML Kit; torch; **From photo** decodes a gallery image. A found code shows a result card over the camera and is saved to history
- **Typed results** — Link (full domain, http warning), UPI (payee, UPI ID, amount; hands off to a UPI app — Scanly never pays), Wi-Fi (one-tap join on Android 11+, copy password otherwise), Contact (save / call), Email, Phone, Text. Raw content is always available
- **Create** — Link, Text, Wi-Fi, UPI, and Contact payloads; live preview; optional caption printed under the code; dark ink colours only; Save image (PNG, 1024 px) or Share
- **History** — on-phone list of scanned and created codes with search, All / Scanned / Created filters, and Clear history

### PDF tools

Operate on **device PDFs** (system document picker) or **Scanly library documents** (exported to a temporary PDF first). Every tool follows the same steps:

1. **Choose a file** — full-screen picker: Phone storage, then Scanly documents as page thumbnails with search and sort; empty scans are hidden
2. **Options** — tool-specific settings with one primary action
3. **Done** — first-page preview of the result (tap to open in the reader), editable file name, a summary of what was applied, **Save to phone** and **Share**. The original is never changed

| Tool | Behavior |
| --- | --- |
| Reader | Real file name, one page counter, paged or continuous, pinch-zoom, tap to hide chrome, **Pages** grid to jump, Share; password unlock when required |
| Merge | Multi-select in tap order, then reorder (move up / down), see page totals, and name the output before merging |
| Compress | High / Balanced / Smallest quality via page re-encode; result shows the saving and before/after sizes, with "Try another quality" |
| Password | Unlocked files open **Add password** (strength meter, live match check, optional **Restrict editing**); locked files open **Remove password**. AES-256 |
| Watermark | Live preview from the production engine pinned above the controls; presets (CONFIDENTIAL, DRAFT, COPY, For KYC use only) and optional date; Tiled or Single; diagonal or horizontal; size and opacity sliders; Grey / Red / Blue / Teal; all pages or first page. Stamps follow page rotation, single stamps always fit the page, and tiles run in even rows along the stamp angle |

## Document detail

- View all pages as portrait paper tiles with a visible page number
- **Tap** a tile to review the page; edit / retake / share / delete stay on the review chrome
- **Reorder** pages by long-press and drag when more than one page exists
- **Delete** individual pages from review
- **Rename** document (does not affect first-page thumbnail — v1.0.9 fix)
- **Assign to group** or remove from group (with inline new-folder creation)
- Add more pages via scan session or gallery import
- Open page preview for full-screen review
- **NEEDS_REVIEW** indicator when corner detection fails
- **Export and share:**
  - PDF with optional open password, footer page numbers, auto/portrait/landscape orientation, print-size or auto-fit pages, and margins
  - Image archive (ZIP of JPEGs)
  - **Save** writes directly to the configured export folder (no per-export file creator)

## Page preview

- Swipeable full-page review within a document — only the image moves; chrome stays fixed
- Page title follows the selected page
- Pinch/double-tap zoom; double-tap or fit-to-screen icon resets to fitted scale; zoom level hidden at 1.0×
- **Overflow menu:** Share page, Edit page, Retake, Delete (with confirmation)
- Navigate to page editor from any page

## Page editor

- **Live result preview** — shows the perspective-cropped page with the current filter and adjustments
- **Crop screen** (`crop/page/{pageId}`) — **AI Detect**, **Left** / **Right** rotation, four-point handles, **Reset**, apply on Done
- **Filter picker** (full-screen) — large live preview of each preset on the cropped page; optional apply-to-all-pages
- **Filter engine** — a paper-aware lighting model removes uneven shading while keeping photos, logos and colour ink; colour looks stay recognizably the capture, scan looks (Grayscale, B&W, Faded Text) read as clean paper; the same look appears at chip, preview and saved-JPEG sizes
- **Filter presets** (8):
  - Original, Auto (picks Magic Color, Natural, Faded Text or Grayscale for the page)
  - Magic Color, Natural, Grayscale, B&W, Whiteboard, Faded Text
  - Pages saved with the earlier presets (Enhanced Color, Shadow Reduction, Clean, Soft B&W, Receipt) are remapped to the closest new preset when the app upgrades
- **Filter adjust** (full-screen) — **Intensity** (0–150 %, default 100 %) first, then brightness, contrast, saturation, sharpness; hold **Compare** for filter-only; scrollbar beside sliders
- **Retake** — opens camera session in replacement mode
- **Delete page** from the editor
- Non-destructive: edits reprocess from the raw capture; raw files are never overwritten

## Document groups (collections)

- Optional folders for organizing related documents
- Create, rename, delete groups; new folders start with a suggested name, with clear and **Suggest name** formats
- Move documents between groups or leave ungrouped
- **Group detail screen** — view all documents in a group, manage membership, create documents in group
- **Group export:**
  - Single merged PDF (all pages across all group documents)
  - Zipped PDF set (one PDF per document)
  - Uses the same password, page number, orientation, page size, and margin controls as a single-document PDF

## Settings

Main **Settings** hub keeps a short list; details open as sub-screens:

| Hub | Sub-screen | Highlights |
| --- | --- | --- |
| Appearance | `settings/appearance` | Theme (System/Light/Dark), pure black AMOLED option |
| Storage & backup | `settings/storage` | Usage, export path, backup/restore, empty cleanup, clear all |
| Document detection | `settings/detection` | Automatic or manual models, document gate, model benchmark |
| Widgets | `settings/widgets` | Pin Actions / Scan / QR |
| Help & FAQ | `settings/faq` | Bundled FAQ topics |
| About | `settings/about` | Version, updates, support links, privacy/terms/licenses |

### Storage & backup (`settings/storage`)

- **Storage usage** — documents, export cache, database, and archive workspace bytes
- **Save location** — exports default to `Downloads/Scanly`; custom base folder via SAF folder picker
- **Library backup** — exact compressed `.scanly` archives under `{destination}/backup/` after free-space preflight
- **Library restore** — validate and stage a `.scanly` archive; **Replace** or **Merge as copies**
- **Library cleanup** — remove empty documents / empty folders
- **Clear all data** — destructive wipe of library and caches (with confirmation)

## Onboarding

- First-run intro screen shown once
- Completion flag persisted in DataStore
- Automatic update checks deferred until onboarding is complete

## App updates

- Dual distribution channels via build type (`githubRelease` / `playStoreRelease`)
- Checks the build's fixed update channel on app start (after onboarding)
- **6-hour cooldown** between automatic update dialog appearances
- **GitHub builds** — compare `versionName` with latest GitHub release; open release page
- **Play Store builds** — Google Play in-app updates (flexible or immediate); restart snackbar after flexible download
- Update messaging reflects the installed channel (not always "Google Play")
- Shows GitHub release notes when available

## Home-screen widgets and quick actions

- **Actions widget (4×1)** — capsule bar with Scan, Import, QR, and Library icon wells
- **Scan widget (1×1)** — brand circle only (no card background); starts a new camera scan
- **QR widget (1×1)** — brand circle only (no card background); opens the QR tool
- **Device theming** — pill/glyph colors follow system light/dark mode
- **Reliable first paint** — layouts avoid RemoteViews layer-list icons so content is visible immediately after placement
- **Add from Settings → Widgets** — pin Actions / Scan / QR when the launcher supports `requestPinAppWidget`
- **Launcher quick actions** — long-press the app icon for Scan, QR, Import, and Library
- All entry points use explicit `MainActivity` launch actions (`in.c1ph3rj.scanly.action.*`) and are held until onboarding completes

## Shared UI components

Reusable building blocks in `feature/components/`:

- Document and group cards with paper-shaped covers, compact metadata, and overflow / long-press actions
- FAB menus for create/scan actions
- Export and share bottom sheets
- Consistent chrome via `core/ui/ScanlyChrome`

## What Scanly does not do

- Cloud synchronization or automatic scheduled backup
- OCR / text recognition
- Batch cloud upload
- In-app APK installation (GitHub builds open the release page)
- Account or authentication system

## Related docs

- [user-guide.md](user-guide.md) — step-by-step user workflows
- [../architecture/screens.md](../architecture/screens.md) — screen and ViewModel mapping
- [../processing/](../processing/) — how capture and processing work
