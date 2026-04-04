# Sprint 6

Sprint 6 delivers the first real per-page editor with a manual 4-point cropper, rotation controls, and filter selection.

Implemented in this sprint:

- real page editor navigation from document detail into a dedicated page route
- page-level observe/update contracts in the repository and use-case layer
- non-destructive page reprocessing from the raw original after edits
- a black editor workspace inspired by the provided scanner references
- draggable 4-point crop handles over the page preview
- rotate left / rotate right support
- filter selection for:
  - original
  - enhanced
  - grayscale
  - black and white
- reset-crop action back to the last detected/default quad
- persisted page edit metadata:
  - crop quad
  - rotation
  - selected filter
  - refreshed processed image and thumbnail
- sharper live-detection overlay corners in the camera preview

Build verification completed:

- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug`

Testing guidance for this sprint:

- open a page from document detail and confirm the editor loads the page preview correctly
- drag each crop handle and confirm the polygon updates without collapsing into an invalid shape
- rotate left and right and confirm both preview and saved result follow the new orientation
- switch among all filter presets and confirm the saved thumbnail updates after tapping Done
- reopen the edited page from document detail and confirm crop/filter/rotation changes persist

Notes:

- the editor always reprocesses from the raw original instead of stacking edits on top of the previously processed file
- preview filter rendering is intentionally lightweight; the final saved asset still goes through the real processing pipeline
- a magnifier bubble and richer page-to-page editing workflow can be refined further after this baseline
