# Features

Complete inventory of Scanly features as of **v1.0.9** (including unreleased work on the current branch).

## Home dashboard

- Shows up to **8 recent documents** and **6 recent groups**
- Quick actions: start a new scan, create an empty document, create a group
- **Gallery import** — pick up to 10 images to start a new document
- **Suggested names** — create dialogs offer a **Suggest name** button with rotatable date-based title formats; duplicate titles receive numeric suffixes
- Shortcut into the full Library
- Adaptive layout: bottom navigation on phone, navigation rail on tablet

## Library

- Primary place to search, browse, and manage all content
- **Three filter pills:** All, Folders (groups), Documents — rounded Material 3 surfaces (not underline tabs)
- **Search** across document and group titles
- **Six sort options** (name, date created, date updated — ascending and descending)
- Create, rename, and delete documents and groups with **Suggest name** for new items
- Move documents between groups or create a new group inline when moving
- Open document detail or group detail from any list item
- **All tab semantics:** foldered documents appear under their group, not in the main All list (unless searching)

## Document scanning (camera session)

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

Fourth bottom-nav tab with categorized utilities:

### Create

- **Scan** — create a document and open the camera session
- **Import** — gallery import (max 10 images) into a new document

### QR Code

- **Scan** — CameraX + ML Kit barcode scanning; copy or open http(s) results
- **Generate** — encode text/URL with ZXing; live preview; save PNG to export folder or share

### PDF tools

Operate on **device PDFs** (system document picker) or **Scanly library documents** (exported to a temporary PDF first). Every generated result can be previewed in Scanly's built-in reader before it is saved to the configured export destination or shared; an external PDF app is not required.

| Tool | Behavior |
| --- | --- |
| Reader | Page-by-page or continuous-scroll preview for device, library, and generated result PDFs; pinch zoom and password unlock when required |
| Merge | Combine two or more PDFs into one |
| Compress | High / Balanced / Smallest quality presets via page re-encode |
| Password | First-page preview; Protect or Remove with clear password form; export-focused completion with Preview / Save / Share |
| Watermark | Export-accurate first-page proof from the production stamping engine; page-relative font size; dense tiled security field or one large single stamp; Small/Medium/Large scale; first-page or all-page coverage; diagonal/horizontal orientation; opacity control |

## Document detail

- View all pages in a multi-page document
- **Reorder** pages (move up/down)
- **Delete** individual pages
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

- **Four-point crop** — drag corner handles to adjust document boundaries
- **Rotate** — 90° increments
- **Filter presets** (10 modes):
  - Original, Auto, Enhanced Color, Grayscale, Black & White
  - Clean, Shadow Reduction, Magic Color, Receipt, Soft Black & White
- **Retake** — opens camera session in replacement mode
- **Re-detect corners**, reset, delete page
- Non-destructive: edits reprocess from the raw capture

## Document groups (collections)

- Optional folders for organizing related documents
- Create, rename, delete groups with **Suggest name** formats
- Move documents between groups or leave ungrouped
- **Group detail screen** — view all documents in a group, manage membership, create documents in group
- **Group export:**
  - Single merged PDF (all pages across all group documents)
  - Zipped PDF set (one PDF per document)
  - Uses the same password, page number, orientation, page size, and margin controls as a single-document PDF

## Settings

Main screen (`settings`) — lean layout with links to sub-screens:

- **Look & feel** — theme mode (System, Light, Dark) plus optional **pure black** Material 3 surfaces for AMOLED battery savings
- **Storage & backup** — link to dedicated sub-screen (usage, export path, backup/restore, clear data)
- **Document detection**
  - Automatic model selection (device calibration) or independent Live / Post models (Lite, Standard, High, Accurate)
  - Physical-document gate toggle
  - **Model benchmark** sub-screen — run selected local images through gate + all corner models
- **About** — app version, developer portfolio, manual update check
- **Support** — email and project website links
- **Legal** — privacy policy, terms, open-source licenses (dedicated sub-screens)

### Storage & backup (`settings/storage`)

- **Storage usage** — documents, export cache, database, and archive workspace bytes
- **Save location** — exports default to `Downloads/Scanly`; custom base folder via SAF folder picker
- **Library backup** — exact compressed `.scanly` archives under `{destination}/backup/` after free-space preflight
- **Library restore** — validate and stage a `.scanly` archive; **Replace** or **Merge as copies**
- **Live progress** — foreground WorkManager job with phase/progress, cancellation
- **Clear all data** — destructive wipe of library, files, export cache, and thumbnail cache (with confirmation)

### FAQs and licenses (`settings/faq`, `settings/licenses`)

- Bundled JSON assets (`faqs.json`, `licenses.json`) rendered in dedicated sub-screens

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

## Shared UI components

Reusable building blocks in `feature/components/`:

- Document and group cards with thumbnails
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
