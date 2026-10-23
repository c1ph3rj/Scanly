# User Guide

How end users interact with Scanly. This describes app behavior, not implementation.

## First launch

1. App opens to the **onboarding** intro screen, where Pip says hello.
2. Tap **Get started**. If the camera is not allowed yet, a short step explains why Scanly needs it and asks for access. **Maybe later** always continues; the step is skipped when the camera is already allowed.
3. You land on **Home**. On later launches, onboarding is skipped.

While Home loads your library, shimmering placeholders stand in for folders and documents.

## Scanning a new document

### From Home

Home quick actions are **Scan**, **Import**, and **Folder**. Only **Scan** opens the camera.

1. Tap **Scan**. A suggested name is already filled in. Keep it, tap the clear icon to type your own, or tap **Suggest name** to cycle formats, then the camera session opens.
2. If you skipped the camera step in onboarding, Android asks for camera access now. If you deny, Scanly stays on an in-app prompt (an empty viewfinder with Pip) so you can allow it again. If you block camera permanently, Scanly spells out how to allow it in **Settings** — it does not jump to Settings on first open.
3. Point the camera at a **physical** document. Scanly filters out many phone screens and non-document rectangles before drawing edges.
4. Live overlay shows detected page edges and quality hints. For open books, frame a single dominant page when possible.
5. Tap the shutter ring (or leave **AUTO** on to capture when the page is steady). Pip's line at the bottom of the preview coaches you — edges found, hold steady, too dark, blurry, lens covered — and shows the countdown.
6. Capture additional pages, or tap **Done** to open the document.
7. App navigates to **document detail** with all captured pages.

**Import** opens the gallery picker (up to 50 images; see below). **Folder** opens folder creation with a suggested name.

Home shows **New folder**, up to three recent folders (plus **View all** when there are more), and the four most recently updated documents. Tap a row to open it, or **See all** to browse the full Library. Recent files and folders use the same presentation as Library. A closable Pip tip may appear under the greeting once a day.

### From Library

Same scan flow — create a new document from the Library FAB menu.

Library opens in a document grid. Use the grid/list controls beside **Last modified** to change layout, and tap the sort label for other ordering options. **All** includes documents in folders; a folder chip filters to its members. **Folders** opens folder management. Tap the magnifying glass for a separate search screen that always includes both documents and folders. Search matches names, including documents inside folders; returning to Library keeps its filters independent of the search query.

The list layout joins compact rows on one shared card background with internal dividers, full-page thumbnails, filenames, page counts, and relative dates such as **Today**. Grid items do not display document tags. The overflow action stays within each card, and tapping a grid tile highlights only that tile's rounded bounds.

## Importing from gallery

### Start a new document

1. On **Home**, choose **Import from gallery** in the FAB menu.
2. Select up to 50 images in the file picker.
3. Each image is processed through the same pipeline as a camera capture.
4. Land on **document detail** with imported pages.

### Add pages to an existing document

1. Open **document detail**.
2. Choose import from gallery.
3. New pages are appended to the document.

## Reviewing and editing pages

### Page preview

1. From **document detail**, tap a page tile (portrait paper preview with a page number).
2. **Page preview** opens — swipe between pages.
3. Pinch or double-tap to zoom. Double-tap again, or use the fit-to-screen action, to reset the image.
4. The zoom level appears only while the page is enlarged.
5. Tap edit to open the **page editor**.

### Page editor

1. Tap **Filters** for a full-screen picker with a large live preview of each preset on the cropped page, then Done. Color looks stay close to the photo you captured; scan looks (Grayscale, B&W, Faded Text) still whiten paper. Retake or delete from the editor toolbar as needed.
   - **Vivid** — punchy color for posters, covers, photos.
   - **Screen** — photos of monitors, laptops and phones: removes moiré, glare haze and screen color cast; keeps dark mode dark.
2. Tap **Adjust** (next to Filters) to change the **Intensity** of the filter (0–150 %; 100 % is the default look, 0 only evens out lighting) and fine-tune brightness, contrast, saturation, and sharpness on a live cropped preview. Use reset in the adjust screen to clear customizations.
3. Tap **Crop** to open the crop screen. Use **AI Detect** to find the document automatically, rotate with **Left** / **Right**, drag the four corner handles, use **Reset** to restore the starting crop, then **Done** to apply crop and rotation.
4. Back on the editor, tap the check mark to save filter and adjustment changes — processed image and thumbnail regenerate from the raw capture.
5. **Retake** — opens camera in replacement mode; after capture, returns directly to the editor.

### Reorder and delete

From **document detail**:

- Long-press and drag a page tile to reorder when the document has more than one page.
- Open a page to review it; edit, retake, share, and delete live on that review chrome — not as buttons on every tile.
- Rename or delete the document from the header overflow. The current name is filled in; tap clear to type a new one.

## Organizing with groups

### Create a folder

1. Open **Library** → Folders filter, or use create action on Home/Library.
2. Create a new folder. A suggested name is filled in by default. Keep it, tap clear to type your own, or tap **Suggest name** to cycle date-based formats. Duplicates are avoided automatically.

### Add documents to a group

- From **document detail** → assign to a group.
- From **group detail** → **Add documents**. Create a new document inside the folder, or tick documents that are not in a folder yet and tap **Add** (the button counts your selection, e.g. "Add 2 documents"). Each row shows the document's cover page, page count, and when it was last edited (for example "7 pages · Today, 3:35 pm"), so similar names are easy to tell apart. **Select all** / **Clear** toggles every row.

### Browse a group

1. Tap a group on Home, Library, or Folders tab.
2. **Group detail** shows all member documents.
3. Rename, delete the group, or remove documents from it.

### Ungrouped documents

Documents without a `groupId` appear alongside foldered documents in Library's **All** view. A folder chip shows only documents assigned to that folder.

## Exporting and sharing

### Single document

From **document detail**:

| Action | Result |
| --- | --- |
| Export PDF | Generates a PDF and saves it to the configured export folder |
| Export images | ZIP archive of processed JPEGs |
| Share PDF / images | Opens Android share sheet with FileProvider URI |

Before saving or sharing a PDF, choose:

- optional password protection (enter and confirm a 4–64 character open password)
- no page number, or a number at the lower left, bottom center, or lower right
- auto orientation per scanned page, portrait, or landscape
- auto fit, A3, A4, A5, B4, B5, Letter, Tabloid, Legal, Executive, Postcard, American foolscap, or European foolscap
- no, small, or large margins

When password protection is enabled, send the password to the recipient separately from the PDF.

### Group export

From **group detail**:

| Action | Result |
| --- | --- |
| Merged PDF | One PDF containing all pages from all documents in the group |
| Zipped PDFs | ZIP containing one PDF per document |

Merged and zipped group PDFs use the same advanced PDF options. In a merged PDF, page numbering runs continuously across the group. In a ZIP, numbering restarts at 1 in each document.

Saved exports go to `Downloads/Scanly` by default. Change the base folder from **Settings → Storage**. Scanly creates unique filenames rather than replacing an existing export.

## Library search and sort

1. Open **Library**.
2. Documents and folders show a page-shaped cover, title, and compact metadata (page count and date, or document/page counts for folders).
3. Tap a card to open it. Rename, move, delete, or remove-from-folder are on the card’s overflow menu or a long-press menu — not as always-visible icon rows.
4. Type in the search field to find documents and groups by title or words on scanned pages.
5. In the dedicated search screen, choose **Everywhere**, **Names**, or **Text in pages** and optionally filter by document type.
6. Tap a page-text excerpt to open extracted text at that page. Results show a matching excerpt and page number.
7. Change sort order (name or date, ascending or descending) in the Library.

### Read and correct extracted text

1. Open a page-text search result, or choose **Extract text** from a document or page action.
2. Use **Text** to read and select recognized text, or **On page** to see text regions over the scan.
3. Copy or share a page or all pages. Edit recognition when needed; corrections are kept and used in later searches.

Page text is recognized on-device with a bundled ML Kit model. Existing pages are indexed in the background after upgrade; search indicates when indexing is still in progress.

## Sign documents

### Sign a PDF

1. Open **Tools → PDF → Sign** and choose a PDF from the phone or the Scanly library.
2. Add a saved signature or initials, text, today's date, or a checkbox. Move or resize marks, choose an ink colour, and repeat on other pages as needed.
3. Review the signed PDF, set its output name, then **Save to phone** or **Share**. The source PDF is not changed.

### Sign a Scanly document

1. Open the document and choose **Sign document**.
2. Place and edit marks on its pages, then save. Marks appear in previews, thumbnails, and exports and remain editable if you reopen Sign.
3. Crop, filter, and retake edits preserve placed marks.

Create a reusable signature or initials by drawing on the signature pad, or import a photo and extract its ink. Saved signatures stay on this device.

## Settings

The main Settings list is a short hub. Open a row for the full controls.

### Appearance

1. Open **Settings → Appearance**.
2. Choose **System**, **Light**, or **Dark**. Applied immediately and persisted.
3. Optionally enable **Pure black** for true black surfaces in dark theme (helps battery on AMOLED displays).

### Pip, your helper

1. Open **Settings → Pip, your helper**.
2. Turn Pip off entirely (screens fall back to plain text), or keep Pip and choose **Home tips**, **camera coaching**, **export cheers**, and **animation** separately.
3. Pip stays still when system animations are turned off.

### Document detection

1. Open **Settings → Document detection**.
2. **Automatic model selection** is on by default: Scanly times Lite/Standard/High on this device and picks responsive live + accurate post models. Turn it off to choose **Live preview** and **Post-processing** models manually (Lite, Standard, High, Accurate).
3. Keep **Physical-document gate** on to reject screens and non-documents before edge detection (recommended). Turn it off only for troubleshooting.
4. Open **Model benchmark** to run your own photos through the gate and all corner models and review timings (local-only; nothing is uploaded).
5. Under **Text recognition (experimental)**, choose **ML Kit** (default, recommended) or **Indra OCR**. Indra OCR is slower than ML Kit. If it can't read a page, ML Kit reads that page instead, and the page's text screen says so. If Indra stops unexpectedly twice, it is **paused** and a warning appears; select Indra OCR again to retry. **Re-read existing pages** appears under the selector when other pages were read with a different engine; tap it and confirm to re-read them in the background (switching engines offers it once). Text you edited is kept. When a page's text screen offers **Read again**, it re-reads that page with the selected engine.

### Storage usage

Open **Settings → Storage** to view library, export-cache, and database usage, clear temporary exports, clean empty items, or clear all data.

### Save location

1. Open **Settings → Storage**.
2. Tap **Change folder** and grant Scanly persistent access to a base folder.
3. Exports are saved in the selected folder.
4. Tap **Reset default** to return to `Downloads/Scanly`.

Library backup and restore are not available in v1.0.18.

### Clear all data

1. Tap **Clear all data** in Settings.
2. Confirm the destructive action.
3. All documents, pages, groups, files, export cache, and thumbnails are removed.
4. **Cannot be undone.**

### FAQs and licenses

Open **Settings → FAQs** or **Settings → Open-source licenses** for bundled support content.

### Check for updates

Manually checks the update channel built into the installed app. GitHub builds compare against the latest GitHub release and open its release page. Play Store builds use the Google Play in-app update flow.

## Home-screen widgets and quick actions

### Add a widget

1. Long-press an empty area on the Android home screen → **Widgets**.
2. Find **Scanly** and choose:
   - **Scanly actions** — Scan · Import · QR · Library
   - **Scanly Scan** — one-tap camera scan
   - **Scanly QR** — open the QR tool
3. Place the widget on the home screen.

### Launcher quick actions

Long-press the Scanly app icon to open shortcuts for **Scan**, **QR**, **Import**, and **Library**.

### What each action does

| Action | Result |
| --- | --- |
| Scan | Creates a document with a suggested title and opens the camera session |
| Import | Opens the file picker (up to 50 images) and creates an imported document |
| QR | Opens Tools → QR in scan mode |
| Library | Opens the Library tab |

If onboarding has not finished yet, the action waits until onboarding completes, then continues.

## Tools

Open the **Tools** tab for QR and offline PDF utilities. Pick a PDF from the device or the Scanly library.

| Tool | What you do |
| --- | --- |
| QR | **Scan** a code (copy or open a link) or **Create** one from a URL or message |
| Reader | Read a PDF with the full title, page n of m, page-by-page or continuous scroll, pinch-zoom, and tap to hide the bars. The ⋮ menu shares the file, jumps to **All pages**, opens it in any PDF tool below (Back returns to the reader), or opens another PDF |
| Merge | Add two or more PDFs in listed order, then combine them |
| Compress | Choose High / Balanced / Smallest quality, then reduce file size |
| Password | **Protect** or **Remove** an open password |
| Watermark | Stamp text with layout, size, pages, orientation, and opacity |
| PDF to images | Choose pages, JPG or PNG, and **Sharing** (1240 px) or **Print** (2480 px, 300 dpi A4) size. Images save to Pictures/Scanly; share them all at once or a few at a time |
| Sign | Place signatures, initials, text, dates, and checkboxes on a PDF, then save or share it |

When a PDF is ready, preview it in Scanly, then save or share.

### Open PDFs from other apps

In Files, Gmail, Drive, WhatsApp, or any app that opens or shares PDFs, choose **Open in Scanly**. The PDF opens in Scanly's reader, where the ⋮ menu offers every PDF tool. Scanly works on a copy; the original file is never changed.

## Permissions

| Permission | When needed |
| --- | --- |
| Camera | Scanning documents and QR codes. Scanly asks the system first; Settings is only after a permanent block |
| Internet | Optional update check only |

Camera is optional at the hardware level — the app installs on devices without a camera but scanning requires one.

## Related docs

- [features.md](features.md) — complete feature inventory
- [../architecture/navigation.md](../architecture/navigation.md) — route and flow diagrams
- [../processing/export.md](../processing/export.md) — export pipeline details
