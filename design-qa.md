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

# Tools tactile artwork QA — 2026-11-12

The approved target is `../design/tools-icon-concepts-2026-11-12/04-soft-studio-untinted.png`, with the user's requested smaller artwork, extra breathing room and rounded icon-area borders with transparent interiors. This pass covers the existing Android Tools hub, rather than a new prototype.

## Findings and resolution

- The first integration removed the icon-area outline along with the tint. Restored a 1 dp rounded outline around every icon stage, retaining a transparent interior. The post-fix native captures confirm the correction in light, dark and pure-black themes.
- No remaining P0/P1/P2 issue in the scoped portrait icon implementation. Original tool ordering, native labels, captions, card actions and bottom navigation are preserved.

## Fidelity surfaces

- **Typography and copy:** existing native type sizes, weights, localized tool names and captions are preserved. Text remains accessible through the existing semantics; decorative images add no duplicate accessibility announcements. No labels are baked into the artwork.
- **Spacing:** the feature-card icon stage is 84 dp tall, with art using 78% of its height plus normalized transparent margins. The 8 dp card inset and 10 dp gap before the title leave visible air around the smaller illustrations. The wider Merge/Split compositions remain fully contained.
- **Color:** icon containers draw only a theme-aware outline, with no background or glow. Their interiors show the parent card's neutral surface. The Scan hero is neutral too. Existing selected navigation colors remain unchanged.
- **Images:** fourteen individually generated, transparent tactile illustrations match the selected materials and action metaphors. Lossless WebP resources share 512 x 384 art boards and total 1,033,910 bytes. White documents and colored arrows are legible on all three themes; the enlarged comparisons show clean edges and the correct two-to-one Merge / one-to-two Split directions.
- **Layout and behavior:** the phone grid remains two columns and utilities retain the same card sizes. The real app's Tools navigation and QR tile visibility pass. The isolated screen also verifies Merge/Split route callbacks under all three themes.

## Evidence

`../design/tools-icon-concepts-2026-11-12/implemented/` contains nine current emulator screen captures (`light`, `dark`, `amoled`, each with top/document/more states), the actual application shell (`native-top.png`, `native-more.png`), a four-view `tools-preview.png`, and `reference-comparison.png` with source and rendered views in the same image. `document-actions-comparison.png` enlarges matching Merge/Split rows at equal widths to inspect arrows, spacing, transparent edges and outlines. The deliberate size/spacing and border changes follow the user's corrections; the generated board's OS-free presentation is not app-owned device chrome to recreate.

## Verification and limits

- Debug app and instrumented-test APK builds passed after restoring the outlines.
- Two targeted Android tests passed after the correction: the three-theme artwork/action fixture and the real application Tools navigation check. App data was retained during installation; direct instrumentation did not clear package data.
- All fourteen resource files have RGBA transparency, 512 x 384 dimensions and fully transparent corner pixels. `git diff --check` passed.
- Final `:app:lintDebug` passed with zero errors. The build and lint tasks completed successfully after the outline correction; existing Gradle/dependency and test-API deprecation warnings are non-blocking.
- The new captures are from the existing 1080 x 2400 emulator at density 420, not a physical phone. No physical-device or new release claim is made. Broad onboarding/JVM results above belong to the earlier onboarding work, and were not rerun for this artwork change.

final result: passed
