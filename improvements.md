# Scanly Improvements Plan

## Purpose

This document captures the current Scanly document flow and a clean implementation plan for the next set of product improvements:

1. Import images from gallery or capture images using the camera.
2. Reorder pages inside a document.
3. Group documents under user-defined names, such as invoices, land records, or receipts.
4. Revamp the dashboard to focus on search, groups, documents, and a bottom navigation driven scan action.

The plan is based on the current codebase state in `app/src/main/java/in/c1ph3rj/scanly`.

## Current Flow

### App structure

Scanly is a single-module Android app built with Kotlin, Jetpack Compose, Material 3, Room, Hilt, CameraX, and a local image-processing pipeline.

The current navigation starts at `ScanlyDestination.Home.route` and is wired through `ScanlyNavHost.kt`. The main active routes are:

- `home`: document library and scan entry point.
- `library`: currently reuses the same `HomeRoute`.
- `settings`: settings screen.
- `document/{documentId}`: document review/detail screen.
- `camera/session/{documentId}`: camera capture session.
- `editor/{pageId}`: page editor.

The `camera`, `review`, and `editor` top-level destination entries still exist as destination metadata, but camera and editor now have real nested routes while some top-level routes still point to placeholders.

### Home and dashboard today

The current home screen is implemented in `feature/home/HomeScreen.kt`.

What exists now:

- The screen title shows `Scanly` with `Library` as the subtitle.
- Settings are available from a top-right tune icon.
- A top-right add button creates a new document.
- A floating action button opens the camera for the latest document, or opens the create-document dialog if no document exists.
- A large hero card is shown near the top:
  - `Ready for the next scan` when documents exist.
  - `Start a new document` when the library is empty.
- The hero card shows document/page counts and actions for scan/create and new document.
- The document list is displayed under a simple `Documents` heading.
- Each document card shows a cover thumbnail or initials fallback, title, page count, updated date, and actions for open, rename, and delete.
- Loading state is a single loading card.
- Empty state asks the user to create a document.

What is missing today:

- No search bar.
- No bottom navigation.
- No separate Home, Groups, Recent, and Settings shell.
- No central scan action that opens scan/import choices.
- No group section.
- No group data model.
- No sorting control in the home UI.
- No document group labels.
- No quick share action on home document cards.
- No gallery import entry point from home.

### Document creation and document list

`HomeViewModel.kt` observes documents through `ObserveDocumentsUseCase`.

Current document actions:

- Create document through `CreateDocumentUseCase`.
- Rename document through `RenameDocumentUseCase`.
- Delete document through `DeleteDocumentUseCase`.
- Open document through a navigation event.

The current `HomeUiState` only contains:

```kotlin
data class HomeUiState(
    val documents: List<ScanDocument> = emptyList(),
    val isLoading: Boolean = true,
)
```

This means search query, selected sort, group summaries, selected tab, recent filters, and import state are not represented yet.

### Capture flow today

The camera flow is implemented in `feature/camera/ScanSessionScreen.kt` and `ScanSessionViewModel.kt`.

Current behavior:

- A document must exist before scanning.
- `ScanSessionDestination.route(documentId)` opens the camera session.
- Camera permission is requested from the scan session screen.
- CameraX preview is shown full-screen.
- Live document detection runs on preview frames through `DocumentCornerDetector`.
- Auto-capture can be toggled from quick controls.
- Manual capture is available through the shutter button.
- Captured files are prepared through `PreparePageCaptureUseCase`.
- Replacement capture uses `PrepareReplacementCaptureUseCase`.
- After CameraX writes the raw image, `FinalizeCapturedPageUseCase` persists the page.
- The page then flows through `DefaultPageRepository.finalizeCapture`.
- `DefaultPageImageProcessor.processCapture` runs detection, perspective correction, filter processing, and thumbnail generation.

The existing flow is already suitable for processing imported images if the import feature creates the same kind of `PageCaptureDraft` and writes the imported image into the draft raw path before calling the existing finalize path.

### Gallery import today

There is no gallery import flow yet.

The current app already has parts that can be reused:

- `DefaultPageImageProcessor.processCapture` can process an image path.
- `DefaultPageRepository.finalizeCapture` can persist a page once a raw file exists at the draft path.
- `DocumentStorageManager` already owns document/page file layout.
- The document detail screen already uses Android document creation launchers for export, so Compose activity-result launchers are already an accepted pattern in the app.

The missing pieces are:

- A picker launcher using `ActivityResultContracts.PickMultipleVisualMedia` or `GetMultipleContents`.
- A use case/repository method that creates capture drafts for imported images.
- Copying imported image content from `Uri` into the app-private raw image path.
- Batch processing with visible progress.
- Error handling for unsupported/failed image imports.
- A route or bottom sheet entry point from the central scan action.

### Page reorder today

Page reorder already exists at the data and document-detail level.

Current behavior:

- `MovePageUseCase` exists.
- `DefaultPageRepository.movePage(pageId, targetIndex)` reorders pages inside a transaction.
- It uses a temporary page-index offset to avoid violating the unique index on `(documentId, pageIndex)`.
- `DocumentDetailViewModel` exposes `moveSelectedPageLeft()` and `moveSelectedPageRight()`.
- `DocumentDetailScreen` shows `Earlier` and `Later` actions in the review action dock.

What is missing:

- No drag-and-drop reorder UI.
- No dedicated reorder mode.
- No full-page list view optimized for rearranging many pages.
- Reorder is limited to one-step left/right actions.

### Grouping today

Grouping does not exist yet.

Current document model:

```kotlin
data class ScanDocument(
    val id: String,
    val title: String,
    val pageCount: Int,
    val coverThumbnailPath: String?,
    val rootDirectoryPath: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
```

Current `DocumentEntity` has:

- `id`
- `title`
- `pageCount`
- `coverThumbnailPath`
- `preferredFilterPreset`
- `rootDirectoryPath`
- `createdAtMillis`
- `updatedAtMillis`

There is no `groupId`, group table, group repository, or group screen. Group support requires a database migration and domain/repository additions.

### Storage and processing today

Documents and pages are persisted locally.

Relevant pieces:

- `ScanlyDatabase` currently has `DocumentEntity` and `ScanPageEntity`.
- Current database version is `2`.
- `DocumentDao.observeDocuments()` returns all documents ordered by `updatedAtMillis DESC`.
- `ScanPageDao.observePages(documentId)` returns pages ordered by `pageIndex ASC`.
- Page files are stored through `DocumentStorageManager`.
- Processed output and thumbnails are generated by `DefaultPageImageProcessor`.

This means the new features should preserve local-first behavior and should extend the existing document/page pipeline rather than introduce a parallel import or grouping path.

## Target Experience

### Dashboard goals

The dashboard should become a document-first workspace.

The current `Ready for the next scan` card should be removed completely. It duplicates the purpose of the FAB and consumes the most important part of the screen. The home screen should instead show immediately useful content:

1. App title.
2. Search bar.
3. Groups section.
4. All Documents section.
5. Bottom navigation with a central scan action.

The visual style should keep the existing dark theme and bright accent color, but reduce visual noise and make the hierarchy clearer.

### Bottom navigation

Introduce a persistent bottom navigation structure:

- Home
- Groups
- Scan
- Recent
- Settings

The Scan item should be center-aligned and visually emphasized as the primary action. It should use a floating-style circular or rounded button above the navigation rail/bar.

On click, Scan opens a bottom sheet with two choices:

- Scan with camera
- Import from gallery

The sheet should not create a separate document silently. It should either:

- Use the current/latest document when the user is already inside document context.
- Ask for a document title when starting from Home without an obvious target.
- Allow adding imported/captured pages to an existing document if launched from a document detail screen.

### Home layout

Recommended home layout:

1. Header
   - `Scanly` title.
   - No settings icon in the top bar.
   - Optional compact profile/status affordance only if it has real value.

2. Search
   - Search field directly below the title.
   - Placeholder: `Search documents or groups`.
   - Search should match document title, group name, and optionally modified date labels.

3. Groups
   - Section title: `Groups`.
   - Action: `New Group`.
   - Horizontal list of group cards.
   - Each group card should show:
     - Group name.
     - Document count.
     - Total page count.
     - Stacked preview using cover thumbnails from the first few documents.
     - Last modified date.
   - Example groups: `Invoices`, `Land records`, `Receipts`.

4. All Documents
   - Section title: `All Documents`.
   - Sort control: `Date modified`.
   - Structured document cards showing:
     - Thumbnail preview.
     - Document name.
     - Group label if assigned.
     - Last modified date.
     - Page count.
     - Quick actions: view, share, more.

5. Empty states
   - If there are no groups, show a compact create-group row, not a large empty card.
   - If there are no documents, show a compact document empty state below search with the central scan action still available.

### Groups screen

The Groups tab should show a full groups management view.

Core actions:

- Create group.
- Rename group.
- Delete group.
- Open group.
- Assign documents to group.
- Remove document from group.

Group detail should show:

- Group name.
- Document count.
- Total pages.
- Last modified date.
- Search within the group.
- Documents in that group using the same document card pattern as Home.

Deleting a group should not delete its documents by default. The safer default is to ungroup documents and remove only the group record. If destructive group deletion is added later, it should be a separate explicit option.

### Recent screen

The Recent tab should show documents ordered by recent activity.

This can reuse the current `DocumentDao.observeDocuments()` ordering by `updatedAtMillis DESC`, but the UI should be distinct from Home:

- No groups rail.
- Stronger focus on recently modified documents.
- Optional filters: today, last 7 days, all.

## Feature Plan

## 1. Import Images From Gallery Or Capture Image Flow

### Current state

Camera capture exists and is document-scoped. Gallery import does not exist.

### Desired behavior

Users should be able to add pages through either:

- Camera capture.
- Gallery image import.

Both paths should end in the same document/page processing pipeline so imported images receive model detection, perspective correction, filters, thumbnails, and storage metadata just like captured pages.

### Recommended implementation

Add a shared page-ingestion path:

- Keep `PreparePageCaptureUseCase` for camera capture.
- Add a new use case such as `ImportImagesToDocumentUseCase`.
- The import use case should:
  - Accept a `documentId` and a list of selected image `Uri`s.
  - For each `Uri`, create a page draft using the same storage layout used by capture.
  - Copy the URI content into the draft raw image path.
  - Call the existing finalize/process path.
  - Emit progress per imported image.
  - Return a result containing imported count and failed count.

Suggested domain API:

```kotlin
class ImportImagesToDocumentUseCase(
    private val pageRepository: PageRepository,
)
```

Suggested repository addition:

```kotlin
suspend fun importImages(
    documentId: String,
    imageUris: List<Uri>,
): ScanlyResult<ImportImagesResult>
```

Because the domain layer should not directly depend on Android `Uri` if avoidable, a cleaner boundary is:

- UI/content layer opens and reads `Uri`.
- Repository/storage layer receives an `InputStream` provider or a small Android-specific import source type.

Practical first version:

- Keep the Android-specific URI handling in the data layer because this is an Android-only app.
- Use `ContentResolver.openInputStream(uri)` and copy into the raw image path.
- Reuse the existing image processor.

### UI entry points

Add import options in:

- Central Scan bottom sheet from Home.
- Add page action in Document Detail.
- Empty document state.

Primary scan sheet:

- `Scan with camera`: create/select document, then navigate to `ScanSessionDestination.route(documentId)`.
- `Import from gallery`: create/select document, then launch image picker.

### Image picker

Use one of:

- `ActivityResultContracts.PickMultipleVisualMedia` for modern Android photo picker.
- `ActivityResultContracts.GetMultipleContents` as a broader fallback.

The picker should support multiple images and preserve selected order where the platform provides it.

### Processing expectations

Imported images should be processed by the same model-backed path as camera captures:

- Decode image.
- Run document-corner detection when no crop exists.
- Perspective-correct if a quad is found.
- Apply default or document-preferred filter.
- Generate processed image and thumbnail.
- Add page to the document.
- Update document page count, cover thumbnail, and updated timestamp.

### Import states

Add UI states for:

- Picker opened.
- Import queued.
- Importing `x/y`.
- Partial success.
- Failed image with reason.
- Import complete, then open document detail.

### Edge cases

Handle:

- User cancels picker.
- Unsupported image URI.
- Very large images.
- Duplicate imports.
- Import into deleted/missing document.
- App process death while import is running.
- Partial failure after some pages imported.

For the first implementation, partial success is acceptable as long as the user receives a clear message.

## 2. Reorder Pages In The Document

### Current state

The data layer already supports page reorder through `MovePageUseCase` and `DefaultPageRepository.movePage`.

The UI exposes reorder as:

- `Earlier`
- `Later`

This works for small documents but becomes slow and tedious for many pages.

### Desired behavior

Users should be able to reorder pages naturally inside a document.

### Recommended implementation

Keep the existing move use case and add a better UI on top.

Add a `Reorder` mode in `DocumentDetailScreen`:

- Entry point near the `Pages` section or in a more/options menu.
- Show a vertical list or grid of page thumbnails.
- Allow drag-and-drop reorder.
- Persist final order through `MovePageUseCase` or a new batch reorder use case.

For better performance and fewer database writes, add a batch operation:

```kotlin
suspend fun reorderPages(
    documentId: String,
    orderedPageIds: List<String>,
): ScanlyResult<Unit>
```

This is better than calling `movePage` repeatedly after every drag.

### First version

If drag-and-drop is too large for the immediate sprint, improve the existing UI:

- Add a dedicated reorder screen or bottom sheet.
- Use up/down controls on every page row.
- Show page numbers and thumbnails.
- Keep the current transaction-safe reorder logic.

### Acceptance criteria

- User can change page order.
- Page indexes remain contiguous from `0`.
- Document `updatedAtMillis` changes after reorder.
- Cover thumbnail updates if the first page changes.
- Exported PDF uses the new page order.
- Reopen document and order remains correct.

## 3. Grouping Feature

### Current state

There is no grouping model. Documents are currently a flat list.

### Desired behavior

Users should be able to create named groups and place multiple scanned documents under each group.

Examples:

- `Invoices`
- `Land records`
- `Receipts`
- `Medical`
- `College documents`

A group can contain multiple documents, and each document can belong to one group in the first implementation.

### Data model

Add a group entity:

```kotlin
@Entity(
    tableName = "document_groups",
    indices = [
        Index(value = ["updatedAtMillis"]),
        Index(value = ["name"], unique = true),
    ],
)
data class DocumentGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
```

Add `groupId` to `DocumentEntity`:

```kotlin
val groupId: String?
```

Add a Room migration from version `2` to `3`:

- Create `document_groups`.
- Add nullable `groupId` column to `documents`.
- Add index for `documents(groupId)`.

Recommended foreign-key behavior:

- For a nullable `groupId`, use `onDelete = SET_NULL` if Room/entity setup supports it cleanly.
- Otherwise, explicitly ungroup documents before deleting a group.

### Domain model

Add:

```kotlin
data class DocumentGroup(
    val id: String,
    val name: String,
    val documentCount: Int,
    val pageCount: Int,
    val coverThumbnailPaths: List<String>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
```

Extend `ScanDocument` with:

```kotlin
val groupId: String?
val groupName: String?
```

Alternatively, keep `ScanDocument` pure and create a UI-specific `DocumentListItem` that joins document and group metadata. The UI-specific model is cleaner if the repository layer would otherwise become too broad.

### Repository and use cases

Add `DocumentGroupRepository` with:

- Observe groups.
- Observe group by ID.
- Create group.
- Rename group.
- Delete group.
- Assign document to group.
- Remove document from group.
- Observe documents in group.

Suggested use cases:

- `ObserveDocumentGroupsUseCase`
- `CreateDocumentGroupUseCase`
- `RenameDocumentGroupUseCase`
- `DeleteDocumentGroupUseCase`
- `AssignDocumentToGroupUseCase`
- `RemoveDocumentFromGroupUseCase`
- `ObserveGroupedDocumentsUseCase`

### UI behavior

Home shows a compact groups section at the top.

Groups tab shows full management.

Document cards should display a group label when assigned:

- `Invoices`
- `Land records`
- `Ungrouped`

Document more menu should include:

- Rename
- Move to group
- Remove from group
- Delete

Group cards should show:

- Name.
- Number of documents.
- Total pages.
- Stacked preview.
- Last modified date.

### Acceptance criteria

- User can create a group.
- User can assign existing documents to a group.
- User can create/import/scan into a selected group.
- Group cards update document count and page count.
- Deleting a group does not delete documents by default.
- Search finds group names and documents inside groups.

## 4. Dashboard Revamp

### Current state

The current dashboard is functional but scan-centric and visually heavy. The top hero card dominates the first screen, while actual documents are pushed down.

### Desired behavior

The dashboard should prioritize the user's saved content and make scan/import a clear global action.

### Remove

Remove the entire `LibraryHero` block:

- `Ready for the next scan`
- `Start a new document`
- Hero-level document/page metrics.
- Hero scan/create buttons.

Also remove settings from the top bar once bottom navigation exists.

### Add

Add:

- Search bar directly below the `Scanly` title.
- Groups section with `New Group`.
- All Documents section with sorting.
- Bottom navigation with Home, Groups, Scan, Recent, Settings.
- Central scan action sheet with camera/import choices.

### Suggested home UI state

```kotlin
data class HomeUiState(
    val documents: List<DocumentListItem> = emptyList(),
    val groups: List<DocumentGroup> = emptyList(),
    val searchQuery: String = "",
    val sortMode: DocumentSortMode = DocumentSortMode.DateModified,
    val isLoading: Boolean = true,
    val importState: ImportState = ImportState.Idle,
)
```

Suggested sort modes:

```kotlin
enum class DocumentSortMode {
    DateModified,
    DateCreated,
    Name,
    PageCount,
}
```

### Bottom navigation structure

Use a root app scaffold that owns the bottom navigation instead of embedding navigation controls inside every screen.

Recommended root layout:

- `MainActivity`
  - `ScanlyTheme`
  - `ScanlyApp`
    - `Scaffold`
      - bottom bar
      - center scan FAB
      - `ScanlyNavHost`

Navigation should treat these as primary tabs:

- Home route.
- Groups route.
- Recent route.
- Settings route.

The scan action should be modal and contextual rather than a tab destination.

### Scan action behavior

When central Scan is clicked:

Show bottom sheet:

- Scan document
- Import images

If the user has no active document context:

- Ask for document name first, then continue.
- Or show a document target picker with `New document` at the top.

If user is inside document detail:

- Add pages to the current document by default.

If user is inside group detail:

- Create/import/scan into a new document assigned to that group, or ask the user to select an existing document in that group.

### Search behavior

Search should filter:

- Document title.
- Group name.
- Group label on document cards.

First implementation can filter in memory from observed lists. If the document count grows significantly, move search into DAO queries.

### Document card behavior

Each document card should include:

- Thumbnail preview.
- Title.
- Group label.
- Modified date.
- Page count.
- Quick view action.
- Share action.
- More menu.

Quick actions:

- View opens `DocumentDestination.route(documentId)`.
- Share should reuse the existing PDF/image share use cases where practical.
- More should include rename, move to group, delete.

### Visual direction

Keep:

- Dark background.
- Existing Material 3 theme.
- Current bright accent for primary actions.
- Rounded surfaces where already used.

Improve:

- Reduce top-heavy layout.
- Make content visible earlier.
- Use smaller section headers.
- Use compact chips for metadata.
- Use consistent card spacing.
- Avoid multiple competing primary actions.
- Make scan/import the single emphasized action.

## Implementation Order

### Phase 1: Navigation shell and dashboard cleanup

1. Introduce root scaffold with bottom navigation.
2. Move Settings into bottom navigation.
3. Remove the `LibraryHero` card.
4. Add search bar under title.
5. Add All Documents section with sort control.
6. Keep current flat documents until group persistence is ready.
7. Change central scan action to open a bottom sheet with Camera and Gallery options.

This phase improves the dashboard without requiring database migration.

### Phase 2: Gallery import

1. Add multiple-image picker.
2. Add import use case and repository support.
3. Copy selected images into app-private raw page paths.
4. Reuse existing finalize/process pipeline.
5. Show import progress and partial failure messages.
6. Open document detail after import completes.

This phase gives the central scan action both required paths.

### Phase 3: Groups persistence and UI

1. Add `DocumentGroupEntity`.
2. Add nullable `groupId` to documents.
3. Add Room migration `2 -> 3`.
4. Add DAO, repository, and use cases.
5. Add groups section on Home.
6. Add Groups tab.
7. Add assign/move-to-group UI.
8. Add group labels to document cards.

This phase enables invoices, land records, and other user-defined group organization.

### Phase 4: Reorder UX upgrade

1. Keep existing earlier/later controls.
2. Add a reorder mode or screen.
3. Add batch reorder use case if drag reorder is implemented.
4. Verify export order follows reordered page indexes.
5. Add focused tests for reorder persistence.

This phase improves an already functional feature.

## Data Migration Plan

Current database version: `2`.

Next migration should be version `3`.

Migration responsibilities:

- Create `document_groups` table.
- Add `groupId` column to `documents`.
- Add index on `documents(groupId)`.
- Optionally add group name uniqueness.

No existing documents should be assigned to a group automatically. Existing documents should appear as ungrouped.

## Testing Plan

### Unit tests

Add tests for:

- Group creation validation.
- Assigning document to group.
- Deleting group leaves documents intact.
- Search filtering documents and groups.
- Sort modes.
- Batch page reorder.
- Import result aggregation.

### Repository tests

Add tests for:

- Room migration from version `2` to `3`.
- Documents remain visible after migration.
- Group counts and page counts update correctly.
- Reordered pages keep contiguous indexes.

### Manual QA

Verify:

- Home first screen shows title, search, groups, and documents without the old hero card.
- Settings opens from bottom navigation.
- Central scan button opens camera/import choices.
- Camera capture still works.
- Gallery import processes images and creates pages.
- Importing multiple images keeps expected order.
- Groups can be created, renamed, opened, and deleted.
- Documents can move into and out of groups.
- Search finds documents and groups.
- Recent tab shows updated documents.
- Reordered pages remain in order after app restart.
- Exported PDFs respect reordered pages.

## Risks And Decisions

### Group relationship

Start with one group per document. This matches the requested examples and keeps the model simple. Multi-group tagging can be added later if needed.

### Import pipeline ownership

Imported images should not bypass the model or processor. The durable path is to copy imported images into the same raw page location as camera captures and reuse existing finalization.

### Scan action target

The central scan action needs a document target. The cleanest behavior is contextual:

- From document detail: add to that document.
- From group detail: create/select document inside that group.
- From home/recent/settings: ask for target document or create a new one.

### Dashboard scope

The dashboard revamp should be split from grouping persistence. Removing the old hero card, adding search, adding bottom navigation, and changing the scan action can ship before group data exists. Groups can initially render as an empty compact section until Phase 3 lands.

## Definition Of Done

The improvement set is complete when:

- Home no longer shows the `Ready for the next scan` card.
- Search is available directly below the Scanly title.
- Bottom navigation includes Home, Groups, Scan, Recent, and Settings.
- The central Scan action opens camera/import choices.
- Gallery import processes selected images through the same model-backed page pipeline as camera capture.
- Documents can be grouped under user-defined names.
- Group cards show document count, page count, and stacked previews.
- Document cards show thumbnail, title, group label, modified date, page count, view/share/more actions.
- Pages can be reordered in a usable way beyond one-step movement.
- Existing camera capture, document detail, editor, export, and settings flows continue to work.
