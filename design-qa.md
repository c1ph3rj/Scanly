# Onboarding visual QA — 7 November 2026

Selected direction: the first displayed ImageGen result, **Pip's Paper Journey**.

Source visual truth: `../design/onboarding-review/selected-concept.png` (1672 × 941 px). Each app-content frame is approximately 384 × 778 px; the generator approximated the requested 390 × 844 viewport. Comparisons fit images to equal widths and preserve aspect ratios, without stretching. The source contains no system chrome.

## Comparison history

### Initial pass — blocked

Full-view evidence: `../design/onboarding-review/comparison-initial.png` combines the selected Capture frame with the first native Android fixture.

- **[P1, test capture] Host cutout inset applied inside centered test fixtures.** The emulator's physical camera cutout consumed roughly 150 dp of an unrelated embedded viewport, pushing the header and content down and collapsing short landscape content. Corrected the fixture wrapper to consume the host's insets before measuring each content-only viewport. Production still respects actual system safe areas.
- **[P2, typography] Body copy and action labels were visibly smaller than the selected concept.** Increased body copy to 18/26 sp, button text to 18/24 sp, brand to 24/30 sp, Skip to 16 sp, and chapter labels to 14 sp. Headings remain bold 30/36 sp, or 36/42 sp on roomy tablet layouts.
- The first Android run passed 17 of 18 checks. The 2× text landscape check exposed the inset problem above. Fresh normalized captures and a rerun are required before acceptance.

### Corrected capture and typography pass

Evidence: `../design/onboarding-review/comparison-final.png` (all three selected frames and all three native phone fixtures in one comparison), plus `../design/onboarding-review/typography-comparison.png`. The corrected test run passed **18/18** checks.

- **[P2, spacing] Extra space between the illustration and copy.** A tall hero container centered a shorter image, inadvertently adding invisible bottom padding. Fit the portrait hero container to the asset's natural aspect ratio, then keep a 28 dp gap to the chapter.
- **[P2, page-to-page rhythm] Share's extra trust line moved its chapter relative to the other pages.** Give the portrait copy area a 200 dp minimum (not a maximum), allowing large text to grow while keeping default-size chapter/headline positions aligned. Fresh captures are required after this last spacing change.

### Native interaction check

`../design/onboarding-review/native/04-permission-sheet.png` shows the real sheet over Share; `05-android-camera-prompt.png` shows that only Enable camera opens the system prompt. Denying the prompt reaches Home (`06-home-after-denial.png`), and a subsequent force-stop/relaunch remained on Home. No physical-phone data or installation was changed.

## Implementation decisions

- Three native Compose pager pages: Capture → Find → Share. Skip moves to Share; it does not finish onboarding. The page survives recreation, and resizing uses available bounds rather than device labels.
- Camera permission is a modal bottom sheet over Share. It is never a fourth pager page. Permission remains optional; declining the system prompt or choosing Not now continues. Dismissing the sheet returns to Share. Existing blocked-permission Settings recovery remains in use.
- The selected illustration subjects are retained as three generated PNG assets. Scanly's existing pixel Pip is composed separately, preserving its identity, animation preferences and system reduced-motion behavior. Standard UI icons use the existing Material icons dependency.
- Compact windows stack art above copy; medium portrait windows use a centered reading column; wide windows use art and copy side by side. Scrolling copy and a separate action area accommodate short windows and large text. The sheet itself scrolls when necessary.
- Existing light, dark and AMOLED colors are preserved. No new fonts, dependencies, account flow or permission types were added.

## Artwork provenance

Built-in ImageGen was used with the selected concept as a reference. Each transparent asset's generation brief used the same pale mint organic backdrop, ivory documents, soft gray linework and deep teal accents. Capture: skewed receipt turning into a clean page with scan corners. Find: document with a mint highlighted “Meeting notes” match and a search field. Share: three stacked pages with a red PDF badge and three teal rays. All briefs excluded Pip and app chrome so the real native mascot could be placed separately. Final assets: `app/src/main/res/drawable-nodpi/onboarding_capture.png`, `onboarding_find.png`, and `onboarding_share.png`.

## Final review

No actionable P0/P1/P2 findings remain in the onboarding scope.

### Final spacing pass — passed

- Full-view comparison: `../design/onboarding-review/comparison-final-spacing.png`, pairing the three selected source frames with the final Android captures.
- Focused comparison: `../design/onboarding-review/typography-comparison.png`, with source and implementation copy regions enlarged to equal widths without stretching. This checks actual text weight, wrapping, line height and spacing rather than relying on thumbnail views.
- Modal comparison: `../design/onboarding-review/permission-comparison.png`, pairing the source overlay with the real Android modal. The native 1080 × 2400 capture is cropped to its app-content region (1080 × 2232) and proportionally fit to 390 px wide. Native drag handle, system navigation space, left-aligned sheet copy, and standard Material camera icon are intentional platform adaptations.
- Responsive evidence: `../design/onboarding-review/responsive-overview.png`; individual full-size captures remain under `../design/onboarding-review/final-fixtures/`.
- The final spacing build passed **18/18 onboarding instrumented tests**. A separate real `MainActivity` smoke test with camera already granted passed **1/1**, proving Get started bypasses the sheet and reaches Home. The native prompt and denial/persistence check above exercises the missing-permission route.

### Required fidelity surfaces

| Surface | Assessment |
| --- | --- |
| Typography | Native sans-serif and existing type system retained; bold headings, larger body copy and action labels reproduce the selected hierarchy. Exact generated font rasterization is not treated as a new font requirement. No ellipsizing or fixed text heights; headings and body wrap and scroll with 2× text. |
| Spacing/layout | 24 dp phone margins (20 dp below 360 dp), clear art-to-copy separation, stable chapter positions at normal phone text sizes, and 56 dp minimum main actions. Tablet content and actions have bounded widths. Wide windows use two panes. At small/large-text viewports, scrolling intentionally moves copy partly out of view; it remains reachable and is not truncated. |
| Colors/tokens | Existing deep-teal light theme, mint dark-theme primary, and pure-black AMOLED surfaces retained. Generated pale-mint artwork and ivory paper maintain the concept's visual identity without changing global app theming. |
| Image quality | Three real generated transparent raster illustrations preserve the selected receipt transformation, searchable page and PDF stack. Aspect ratios remain intact, with no cropping. Real native Pip replaces the generated mascot rendition, keeping its identity and motion preferences. Small differences in paper proportions, facial expression and mint silhouettes are accepted asset adaptations. |
| Copy/content | All three headings, descriptions and the privacy statement match the approved story. Only three page indicators are present. Camera access is requested only after explicit Enable camera; Not now works without permission. |

### Viewports and states verified

Content-only fixtures use **density 1 px/dp**, so capture dimensions equal viewport dimensions. The host emulator is 1600 × 2560 px at 160 dpi; its own cutout is consumed outside the centered test fixtures. This avoids applying the host's system chrome inside an unrelated miniature window. Production continues to honor safe drawing insets.

- 390 × 844: Capture, Find, Share; Back, Continue, Skip, swipe, clickable dots, completion and state restoration.
- 320 × 480: small phone; primary action remains visible and copy scrolls.
- 320 × 568 at 2× font scale: narrow split window / large text; final privacy text and completion action remain reachable.
- 640 × 360: phone landscape; two-pane composition.
- 740 × 360 at 2× font scale: landscape with large text; copy scrolls and the primary action remains visible.
- 800 × 1280: centered tablet portrait; 1280 × 800: two-pane tablet landscape.
- Live resizing from 390 × 844 → 900 × 420 → 800 × 1280 retains the selected page.
- Dark and AMOLED; saving disabled state; completion failure/retry; optional permission sheet, blocked-permission copy, Not now and back dismissal.
- Real emulator display restored to 1080 × 2400 at 420 dpi for the Android-native permission and MainActivity checks.

### Build/check results and limits

- Debug APK and Android test APK build successfully.
- JVM suite: **616 passed, 0 failed, 0 skipped**.
- Final instrumented suite: **18 onboarding + 1 MainActivity smoke test passed**.
- Lint ran and found **16 errors** in untouched existing files: 12 `UnsafeOptInUsageError` occurrences in `feature/tools/qr/QrScanScreen.kt`, and 4 `UseAppTint` occurrences in `res/layout/widget_actions.xml`. It reported **zero onboarding issues**. Whole-project lint therefore remains failing; no lint baseline or unrelated source changes were introduced.
- Verification used the emulator. No physical phone installation, data reset or release publication was performed. Foldable behavior is covered through resizable window bounds, not physical foldable hardware. TalkBack and a live blocked-permission Settings-return journey were not manually exercised; semantic headings, accessible page controls, 48 dp targets, state restoration and blocked-sheet actions are implemented/tested.

### Implementation checklist

- [x] Three illustrated native pages, swipe/Back/Skip/Continue/dots.
- [x] Optional permission modal with existing denied/blocked handling.
- [x] Small/large/short/resizable windows and scalable text.
- [x] Existing light/dark/AMOLED themes and motion-aware Pip.
- [x] Source-versus-native visual comparison and final screenshot set.
- [x] Build, unit tests and targeted instrumented tests.
- [x] Changelog and first-launch/navigation documentation updated.

Follow-up polish: no blocking visual work remains. The existing global lint errors are separate work.

final result: passed
