# Onboarding QA

The implemented Paper Atelier flow uses native, localized text and controls with three WebP paper illustrations and Scanly's live Pip. The final action is **Scan my first page**; no import-photo action appears on the final introduction page.

## Verified behavior

- Continue, swipe, Skip, Back, page selection, saved state and completion/error states.
- A contained, non-interactive search example with no clipboard control.
- Page clipping prevents adjacent artwork bleeding into the visible page.
- Pagination keeps 48 dp targets, circular focus feedback and Enter activation without losing focus while the pager settles.
- Phone, tablet, landscape, split-window, dark/AMOLED and 200% text layouts.
- Granted camera access opens scanning; Not now or denial continues to Home. Pending PDF and shortcut destinations retain priority.
- The real first-run permission-to-scanner walkthrough and five app-navigation smoke tests passed during implementation.

## Evidence and limits

Local screenshots and comparisons are retained in the sibling workspace directory `design/onboarding-concepts-2026-11-10/implemented/`. The latest component evidence is in its `component-polish/` subdirectory: `preview-comparison.png`, `focus-comparison.png` and `review.md`. These local design artifacts are outside this app repository.

The completed implementation passed the debug app/test APK builds, Android lint, 627 JVM tests and 26 onboarding UI checks. An emulator System UI error dialog temporarily intercepted device input; after clearing it, the unchanged system-Back check and full UI suite passed.

Physical-phone frame pacing remains unverified. Software-rendered emulator recordings taken during Gradle work do not establish a hardware frame-rate guarantee. Pip motion preferences and disabled system animations are respected.

## Branch cleanup

The three retired `onboarding_*.png` illustrations and ten unused onboarding string keys in all 25 languages have been removed. The current `atelier_*.webp` illustrations, localized UI, behavior and test coverage are preserved. Wildcard imports and stale implementation-history notes have been cleaned up.

Post-cleanup verification passed: debug app and test APK builds, Android lint, 627 JVM tests, and resource-reference/localization checks. All remaining onboarding strings are referenced and present across the 25 language files. The earlier 26 UI checks cover the unchanged screen behavior; no new UI behavior was introduced by this cleanup.

final result: passed
