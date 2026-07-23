# Camera scan-mode selector design QA

## Source visual truth

- Portrait source: `C:\Users\jeeva\AppData\Local\Temp\codex-clipboard-44e24552-bac0-420b-ae9f-f7ee8eada3a7.png`
- Landscape source: `C:\Users\jeeva\AppData\Local\Temp\codex-clipboard-f369b788-1b21-4bc1-917a-c18537a51a70.png`
- Focused failure source: `C:\Users\jeeva\AppData\Local\Temp\codex-clipboard-9a85e17b-a0bb-441e-a309-54ea5e17a528.png`

## Rendered implementation

- Portrait, Book selected: `C:\tmp\scanly-mode-pill-portrait-book.png`
- Landscape, Book selected: `C:\tmp\scanly-mode-pill-landscape-book.png`
- Landscape, ID card selected: `C:\tmp\scanly-mode-rail-landscape-id.png`
- Landscape, Document selected: `C:\tmp\scanly-mode-rail-landscape-document.png`

## Viewport and normalization

- Device: Samsung SM-G781B
- Device size: 1080 × 2400 portrait / 2400 × 1080 landscape physical pixels
- Density: 450 dpi override, approximately 2.8125 px per dp
- Logical viewport: approximately 384 × 853 dp portrait / 853 × 384 dp landscape
- Source pixels: 332 × 740 portrait and 740 × 332 landscape
- Implementation pixels: 1080 × 2400 portrait and 2400 × 1080 landscape
- Normalization: each source and implementation image was proportionally fitted into equal halves of the comparison canvas. Focused selector crops were compared separately so label wrapping and selected-state treatment remained readable.
- State: dark camera session, Book selected for primary comparison

## Comparison evidence

- Full landscape comparison: `artifacts/design-qa/scanly-mode-selector-landscape-comparison.png`
- Full portrait comparison: `artifacts/design-qa/scanly-mode-selector-portrait-comparison.png`
- Focused selector comparison: `artifacts/design-qa/scanly-mode-selector-focused-comparison.png`

## Findings and iteration history

1. **P1 — landscape label collapse**
   - Earlier evidence: the horizontal portrait pill was squeezed into the 184 dp landscape dock and rendered “Book” one character per line.
   - Impact: the active mode was hard to read and the last target became visually fragile.
   - Fix: landscape now uses a dedicated vertical mode rail with one-line labels, standard Document / ID card / Book icons, and 48 dp rows.
   - Post-fix evidence: all three labels remain on one line in the focused comparison and UI hierarchy. Document, ID card, and Book were each selected successfully on the physical device.

2. **P2 — weak selected-state contrast**
   - Earlier evidence: the selected portrait segment used a subtle gray fill that was close to the black container.
   - Fix: both layouts now use Scanly’s teal primary color with `onPrimary` text and icons.
   - Post-fix evidence: Book is immediately identifiable in both full-view comparisons without changing the surrounding camera chrome.

3. **P2 — overlapping landscape regions risk**
   - Risk: a taller selector could collide with the centered shutter if it remained absolutely aligned.
   - Fix: the landscape dock now lays out the mode rail, shutter, and recent-capture target with `SpaceBetween`.
   - Post-fix evidence: the 384 dp-high device viewport shows clear separation between all three regions.

## Required fidelity surfaces

- Typography: existing Material typography is preserved; labels are explicitly single-line, medium weight when idle, and bold when selected.
- Spacing and layout: portrait remains a compact segmented pill. Landscape uses the available vertical rhythm and full dock width with 48 dp targets.
- Colors and tokens: the black translucent container, outline, and Scanly primary teal align with existing camera controls and shutter ring.
- Icons and assets: Material outlined icons are used; no custom vectors, text glyphs, or placeholder assets were introduced.
- Copy: portrait retains `Document`, `ID`, and `Book`; landscape expands `ID` to `ID card` for clarity.
- Accessibility: options expose radio-button role and selected semantics; all landscape options meet the intended 48 dp target height.

## Interactions tested

- Selected Book in portrait.
- Rotated the live camera session between portrait and landscape.
- Selected Book, ID card, and Document in landscape.
- Confirmed the shutter and recent-capture regions remain separately accessible.

## Automated verification

- `:app:compileDebugKotlin`: passed
- `:app:compileDebugAndroidTestKotlin`: passed
- Focused `ScanModeSelectorTest` on Samsung SM-G781B: 2 tests passed
- Added a narrow-width vertical selector Compose test.

final result: passed
