# OCR engines (page text)

Scanly reads the text on each page on the phone and stores it for search, the Text screen and export. Two engines can do that work.

| Engine | Code | Notes |
| --- | --- | --- |
| ML Kit (default) | `data/text/MlKitPageTextRecognizer.kt` | Google ML Kit text recognition, bundled with the app. |
| Indra OCR (experimental) | `data/text/indra/` | LiteRT detection, orientation, script and recognition models with OpenCV preprocessing, from `assets/models/indra/`. Multi-script (base, CJK, right-to-left and world recognizers). Slower than ML Kit. |

The engine is chosen in Settings (`ocr_engine` DataStore key, default `ML_KIT`). `SelectablePageTextRecognizer` is the only recognizer the repository sees. It reads the selected engine for every page, so a change applies to the next page read, and it runs the fallback rules below. `DefaultPageTextRepository.recognizeSource` stores the result and its provenance.

## Fallback ladder

Reading a page never fails because of Indra. When Indra is selected, the first matching rule decides the page:

1. **ML Kit selected:** read with ML Kit. No fallback reason.
2. **Crash guard suspended** (Indra stopped unexpectedly twice): ML Kit, reason `INDRA_SUSPENDED`. Indra is not called.
3. **Session breaker open** (see below): ML Kit, reason `INDRA_UNAVAILABLE`.
4. **Page poisoned** (this exact image crashed the process while Indra read it): ML Kit, reason `INDRA_FAILED`.
5. **Run Indra:** mark the page in flight (see the crash guard), then read it under a 60 s limit.
   - Limit reached: ML Kit, reason `INDRA_TIMEOUT`. Indra's models are released.
   - Indra throws (including out-of-memory or a failed native link): ML Kit, reason `INDRA_FAILED`. Indra's models are released.
   - Indra models or OpenCV cannot load: ML Kit, reason `INDRA_UNAVAILABLE`. The breaker opens at once.
6. **Indra returns blank text:** run ML Kit on the page. If ML Kit finds text, store ML Kit's text with reason `INDRA_EMPTY`. If it does not, store Indra's blank result as an Indra read.
7. **Indra returns text:** store it as an Indra read.
8. **ML Kit fails on a fallback path:** the error propagates, the repository stores the page as "no text", and Indra is not retried for that page.

A cancelled read (the worker stops, or the screen goes away) is not a failure. It clears the in-flight mark and is rethrown.

## Crash guard

A native crash inside Indra cannot be caught with `try`. `IndraCrashGuard` (implemented by `SharedPreferencesIndraCrashGuard`, preferences file `indra_crash_guard`) therefore writes a page key before each Indra read, using a synchronous `commit()`, and clears it when Indra returns or throws a Kotlin exception. The key is the image path's hash plus the file length, so a retaken or re-filtered page is a new page.

If the key is still there on the next launch, the app asks Android how the previous process ended (`ApplicationExitInfo`, Android 11+):

| How the previous process ended | Page poisoned | Counts toward pause |
| --- | --- | --- |
| Crash, native crash, or ANR | Yes | Yes |
| Low memory or excessive resource use | Yes | No |
| Swiped away, force-stopped, updated, or ended normally | No | No |
| No record (Android 10, or not found) | Yes | Yes |

- Two counted crashes pause Indra. Pages then go to ML Kit with `INDRA_SUSPENDED`, and Settings shows a warning under the engine selector.
- Selecting Indra OCR in Settings resets the crash count and reopens the session breaker, so Indra is tried again. Poisoned pages stay on ML Kit until their image changes.
- At most 50 poisoned page keys are kept.

## Session breaker

- Three consecutive Indra failures (exceptions or time limits) open the breaker for the rest of the process. A model or OpenCV load failure opens it immediately. While it is open, Indra is not called.
- Any successful Indra read resets the failure count.
- Selecting Indra OCR again closes the breaker (see the crash guard).

## Time limit and idle release

- Each Indra read has a 60 s limit (`INDRA_PAGE_TIMEOUT_MS`).
- Indra's models stay loaded between pages. Once no Indra read has run for 30 s (`INDRA_IDLE_RELEASE_MS`), they are released and load again on the next Indra read. ML Kit is not affected.

## Provenance

Each `page_texts` row records how its text was read (Room schema v11):

| Column | Meaning |
| --- | --- |
| `engine` | Engine whose text is stored: `ML_KIT` or `INDRA`. NULL when recognition threw or the page had no image. |
| `requestedEngine` | Engine selected when the page was read. |
| `fallbackReason` | Why ML Kit read a page that asked for Indra (`INDRA_FAILED`, `INDRA_EMPTY`, `INDRA_TIMEOUT`, `INDRA_UNAVAILABLE`, `INDRA_SUSPENDED`). NULL when the requested engine read the page. |

`MIGRATION_10_11` adds the columns and marks existing non-blank rows as ML Kit reads, since every earlier read used ML Kit. Blank rows stay NULL.

The Text screen shows a caption only when it is useful: "Read with Indra OCR (experimental)" for an Indra read, or "Read with ML Kit (Indra OCR couldn't read this page)" when a fallback happened. ML Kit pages show no caption.

## Re-reading existing pages

- **Re-read existing pages** appears under the engine selector in Settings when live pages have a text row that the selected engine did not produce (read by the other engine, or nothing recognized). It is disabled while the indexer is working ("Reading text… N pages left").
- Pages that the selected engine already failed on or found empty (`INDRA_FAILED`, `INDRA_EMPTY`) are skipped, because reading them again would fall back again. Pages skipped because of a pause or time limit are still offered.
- Tapping it asks for confirmation. Confirming runs `rereadPagesNotReadWith`, which marks the matching rows stale (`sourceUpdatedAtMillis = -1`) and enqueues `TextIndexWorker` through `TextIndexScheduler`. The worker runs only while the battery is not low.
- Switching the engine shows the same confirmation once. Nothing is re-read without confirmation.
- The re-read uses the normal indexing pipeline: text the user edited (`userText`) is kept, and the action is one-shot, so a page that falls back to ML Kit is not retried automatically.
- For one page, **Read again** on the Text screen recognizes that page with the selected engine. It is offered when the page changed or no text was found.

## Model assets

- `app/src/main/assets/models/indra/` holds the `det`, `orient`, `script`, `rec`, `rec_cjk`, `rec_rtl` and `rec_world` `.tflite` models and the `vocab_*.json` files. `order.tflite` is not bundled. The folder is git-ignored: copy the models in locally before building. A build without them still runs, and every page is read with ML Kit.
- After replacing any `.tflite` model, run `python -I scripts/indra_patch_input_width.py app/src/main/assets/models/indra/*.tflite` before building. The converted models declare an input width placeholder of 1, and LiteRT sizes tensors when the interpreter is created; a width of 1 collapses to 0 inside the network, so creation fails before `resizeInput` can run. The script sets that default width to 64 and leaves the dynamic dimensions alone.

## Code map

| File | Role |
| --- | --- |
| `data/text/PageTextRecognizer.kt` | Recognizer interface |
| `data/text/MlKitPageTextRecognizer.kt` | ML Kit engine; `RecognizedPage` is defined alongside it |
| `data/text/indra/IndraPageTextRecognizer.kt` | Indra pipeline and model loading |
| `data/text/SelectablePageTextRecognizer.kt` | Engine choice, fallback ladder, breaker, time limit, idle release |
| `data/text/IndraCrashGuard.kt` | Crash guard rules and SharedPreferences store |
| `data/text/DefaultPageTextRepository.kt` | Stores results and provenance; re-read API |
| `data/text/TextIndexWorker.kt`, `TextIndexScheduler.kt` | Background indexing |
| `data/text/OcrEngineSource.kt` | Reads the selected engine for each page |

## Related docs

- [overview.md](overview.md) — layers and connection maps
- [../data/database.md](../data/database.md) — `page_texts` columns and migrations
- [../data/settings-and-updates.md](../data/settings-and-updates.md) — `ocr_engine` setting
