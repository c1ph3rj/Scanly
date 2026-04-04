# Sprint 2

Sprint 2 establishes the real local document domain for Scanly.

Implemented in this sprint:

- Room persistence for documents and future scan pages
- a `DocumentRepository` with create, rename, delete, and observe flows
- app-private document storage scaffolding under `files/documents/{documentId}/`
- generated cover thumbnails so the library has visible cards before camera capture exists
- a working library/home screen with:
  - empty state
  - document list
  - create flow
  - rename flow
  - delete flow
  - reopen flow through a detail screen
- a document detail route that proves persisted documents can be reopened after restart

Build verification completed:

- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug`

Notes:

- documents are offline-first and stored only in app-private storage
- scan pages remain in the schema even though capture is not wired yet, so Sprint 3 can attach manual capture without reshaping the database
- cover thumbnails are generated locally and are meant to be replaced later by real page thumbnails once capture exists
