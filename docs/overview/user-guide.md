# User Guide

How end users interact with Scanly. This describes app behavior, not implementation.

## First launch

1. App opens to the **onboarding** intro screen.
2. User completes onboarding → lands on **Home**.
3. On subsequent launches, onboarding is skipped.

## Scanning a new document

### From Home

1. Tap the scan/create action (FAB menu).
2. Choose **Scan** → camera session opens for a new document.
3. Point the camera at a document. Live overlay shows detected edges and quality hints.
4. Tap capture (or use auto-capture when stable).
5. Capture additional pages or finish the session.
6. App navigates to **document detail** with all captured pages.

### From Library

Same flow — create a new document via scan from the Library FAB menu.

## Importing from gallery

### Start a new document

1. On **Home**, choose **Import from gallery** in the FAB menu.
2. Select up to 10 images.
3. Each image is processed through the same pipeline as a camera capture.
4. Land on **document detail** with imported pages.

### Add pages to an existing document

1. Open **document detail**.
2. Choose import from gallery.
3. New pages are appended to the document.

## Reviewing and editing pages

### Page preview

1. From **document detail**, tap a page thumbnail.
2. **Page preview** opens — swipe between pages.
3. Tap edit to open the **page editor**.

### Page editor

1. Adjust crop corners by dragging the four handles.
2. Rotate the page in 90° steps.
3. Select a filter preset from the list.
4. Save — processed image and thumbnail regenerate from the raw capture.
5. **Retake** — opens camera in replacement mode; after capture, returns directly to the editor.

### Reorder and delete

From **document detail**:

- Use move controls to reorder pages.
- Delete unwanted pages.
- Rename the document from the header.

## Organizing with groups

### Create a group

1. Open **Library** → Folders tab, or use create action on Home/Library.
2. Create a new group with a title.

### Add documents to a group

- From **document detail** → assign to a group.
- From **group detail** → add existing documents.

### Browse a group

1. Tap a group on Home, Library, or Folders tab.
2. **Group detail** shows all member documents.
3. Rename, delete the group, or remove documents from it.

### Ungrouped documents

Documents without a `groupId` appear in the Documents tab and as "ungrouped" in All view.

## Exporting and sharing

### Single document

From **document detail**:

| Action | Result |
| --- | --- |
| Export PDF | Generates a PDF in export cache; user can save or share |
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

## Library search and sort

1. Open **Library**.
2. Type in the search field to filter documents and groups by title.
3. Switch tabs: **All**, **Folders**, **Documents**.
4. Change sort order (name or date, ascending or descending).

## Settings

### Theme

Choose **System**, **Light**, or **Dark**. Applied immediately and persisted.

### Storage usage

View how much space documents, export cache, and the database consume on device.

### Clear all data

1. Tap **Clear all data** in Settings.
2. Confirm the destructive action.
3. All documents, pages, groups, files, export cache, and thumbnails are removed.
4. **Cannot be undone.**

### FAQs and licenses

Tap to view bundled support content. Licenses list third-party dependencies.

### Check for updates

Manually checks the update channel built into the installed app. GitHub builds compare against the latest GitHub release and open its release page. Play Store builds use the Google Play in-app update flow.

## Permissions

| Permission | When needed |
| --- | --- |
| Camera | Scanning documents |
| Internet | Optional update check only |

Camera is optional at the hardware level — the app installs on devices without a camera but scanning requires one.

## Related docs

- [features.md](features.md) — complete feature inventory
- [../architecture/navigation.md](../architecture/navigation.md) — route and flow diagrams
- [../processing/export.md](../processing/export.md) — export pipeline details
