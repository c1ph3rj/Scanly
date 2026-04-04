# Sprint 7

Sprint 7 completes the multi-page review and document assembly workflow.

Implemented in this sprint:

- document detail now acts as the real review workspace for an existing document
- selected-page review card with larger preview and current page context
- persistent horizontal page strip for quickly switching among pages
- page reordering through move-left and move-right actions
- page deletion with confirmation
- page replacement/retake flow that reopens the camera in replacement mode
- replacement capture keeps the same page id and page order instead of creating a duplicate page
- add-more-pages flow for existing documents directly from document detail
- success feedback for page move and delete actions
- smaller, cleaner page thumbnails for review surfaces
- unit regression coverage for selected-page persistence as pages change

Build verification completed:

- `./gradlew.bat testDebugUnitTest --no-daemon`
- `./gradlew.bat assembleDebug --no-daemon`

Testing guidance for this sprint:

- open a document with multiple pages and tap through the page strip to confirm selection updates correctly
- move a middle page left and right and confirm the page order persists after leaving and reopening the document
- delete a selected page and confirm the next valid page becomes selected automatically
- replace an existing page from document detail and confirm the page count stays the same
- add another page to an existing document and confirm it appears at the end of the strip
- kill the app completely and reopen the document to confirm order, selection behavior, and page assets still persist

Notes:

- replacement capture intentionally reuses the existing page slot so document order stays stable
- reorder/delete logic updates both Room metadata and document cover/page-count snapshots together
- Sprint 8 can now focus on export/share because the document lifecycle from capture to final review is in place
