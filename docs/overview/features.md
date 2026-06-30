# Features

Complete inventory of Scanly features as of **v1.0.9**.

## Home dashboard

- Shows up to **8 recent documents** and **6 recent groups**
- Quick actions: start a new scan, create an empty document, create a group
- **Gallery import** — pick up to 10 images to start a new document
- Shortcut into the full Library
- Adaptive layout: bottom navigation on phone, navigation rail on tablet

## Library

- Primary place to search, browse, and manage all content
- **Three tabs:** All, Folders (groups), Documents
- **Search** across document and group titles
- **Six sort options** (name, date created, date updated — ascending and descending)
- Create, rename, and delete documents and groups
- Open document detail or group detail from any list item

## Document scanning (camera session)

- **CameraX**-based manual capture with live preview
- **ML corner overlay** — LiteRT model detects document edges in real time
- **Quality feedback** — lighting, blur, lens obstruction, framing guidance
- **Stability tracking** — gates auto-capture when the frame is stable
- **Multi-page sessions** — capture multiple pages into one document in a single session
- **Page replacement (retake)** — replace an existing page; returns to editor after capture (v1.0.9)
- Portrait and landscape layouts with theme-aligned controls (v1.0.9)

## Gallery import

- Import up to **10 images** per pick from the device photo picker
- Available on **Home** and **Document detail** (extend existing documents)
- Reuses the same capture finalize pipeline as camera captures

## Document detail

- View all pages in a multi-page document
- **Reorder** pages (move up/down)
- **Delete** individual pages
- **Rename** document (does not affect first-page thumbnail — v1.0.9 fix)
- **Assign to group** or remove from group
- Add more pages via scan session or gallery import
- Open page preview for full-screen review
- **Export and share:**
  - PDF with optional open password, footer page numbers, auto/portrait/landscape orientation, print-size or auto-fit pages, and margins
  - Image archive (ZIP of JPEGs)

## Page preview

- Swipeable full-page review within a document
- **On-device text recognition** — enter Text mode to detect Latin-script words without uploading the page
- Tap or drag across detected words, adjust the selection handles, select all, and copy while zooming or panning
- Navigate to page editor from any page

## Page editor

- **Four-point crop** — drag corner handles to adjust document boundaries
- **Rotate** — 90° increments
- **Filter presets** (10 modes):
  - Original, Auto, Enhanced Color, Grayscale, Black & White
  - Clean, Shadow Reduction, Magic Color, Receipt, Soft Black & White
- **Retake** — opens camera session in replacement mode
- Non-destructive: edits reprocess from the raw capture

## Document groups (collections)

- Optional folders for organizing related documents
- Create, rename, delete groups
- Move documents between groups or leave ungrouped
- **Group detail screen** — view all documents in a group, manage membership
- **Group export:**
  - Single merged PDF (all pages across all group documents)
  - Zipped PDF set (one PDF per document)
  - Uses the same password, page number, orientation, page size, and margin controls as a single-document PDF

## Settings

- **Appearance** — theme mode: System, Light, or Dark (persisted in DataStore)
- **About** — app version from package manager
- **Support** — FAQs and third-party license disclosures (bundled JSON assets)
- **Storage usage** — shows on-device bytes for documents, export cache, and database
- **Clear all data** — destructive wipe of library, files, export cache, and thumbnail cache (with confirmation)
- **Check for updates** — manual check against the build's GitHub or Google Play channel

## Onboarding

- First-run intro screen shown once
- Completion flag persisted in DataStore
- Automatic update checks deferred until onboarding is complete

## App updates

- Checks the build's fixed update channel on app start (after onboarding)
- **6-hour cooldown** between automatic update dialog appearances
- GitHub builds open the latest GitHub release page
- Play Store builds download and install via flexible or immediate Play flows
- Shows GitHub release notes when available

## Shared UI components

Reusable building blocks in `feature/components/`:

- Document and group cards with thumbnails
- FAB menus for create/scan actions
- Export and share bottom sheets
- Consistent chrome via `core/ui/ScanlyChrome`

## What Scanly does not do

- Cloud sync or backup
- Cloud OCR or recognition of scripts outside ML Kit's bundled Latin model
- Batch cloud upload
- In-app APK installation
- Account or authentication system

## Related docs

- [user-guide.md](user-guide.md) — step-by-step user workflows
- [../architecture/screens.md](../architecture/screens.md) — screen and ViewModel mapping
- [../processing/](../processing/) — how capture and processing work
