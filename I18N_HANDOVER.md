# Handover: Scanly multi-language support (i18n)

> Implementation and verification completed on 2026-11-09. The owner approved one combined commit including the existing search-improvements WIP.
> Date: 2026-11-09 · Branch: `feature/multi-language-support` · Repo: `C:\Users\jeeva\Projects\scanly-projects\Scanly`

## 1. Goal

Make the Scanly Android app (Kotlin + Jetpack Compose + Material 3, single module `:app`, package `` `in`.c1ph3rj.scanly ``) fully usable in **25 languages**, with natural, meaning-based translations (not word-for-word) and a proper UI for picking the language:

English (default), Spanish, Portuguese (Brazil), French, German, Indonesian, Italian, Turkish, Vietnamese, Polish, Dutch, Hindi, Tamil, Bengali, Telugu, Marathi, Kannada, Malayalam, Gujarati, Russian, Ukrainian, Japanese, Korean, Chinese (Simplified), Thai.

## 2. Current state at a glance

| Area | State |
| --- | --- |
| Branch | `feature/multi-language-support`, created from `feature/search-improvements` |
| Commit scope | Owner approved committing the complete working tree together, including existing WIP |
| English resources | 1,699 translatable strings + 27 plurals, plus the non-translatable `app_name` |
| Locale switching | Updates in place, retaining the activity/window, theme, navigation and list state; verified on Android 11 / API 30 and Android 16 / API 36 |
| Language picker | Settings › Preferences › Language; seven representative languages exercised through the real picker |
| Translations | **192/192 parts passing** (24 languages × 8 parts) |
| Shipped resources | **24 complete `values-*/strings.xml` catalogs**, matching every translatable English key |
| Debug builds | `assembleDebug` and `assembleDebugAndroidTest` passed |
| Unit tests | **625 passed**, no failures, errors or skips |
| Lint | Passed; **0 errors**, no `MissingTranslation` or `StringFormat*` findings |
| Device tests | All 4 `AppLocaleDeviceTest` tests passed on each emulator; final API 36 full-suite and API 30 focused rechecks passed (see §10) |
| Cold-start persistence | Tamil selected through the UI survived force-stop/relaunch on both Android versions; original settings and language restored |

**Combined scope:** the owner explicitly approved including the earlier `feature/search-improvements` work (including the first extraction pass, `AppStrings.kt`, onboarding images and `SettingsTextRecognitionScreen.kt`) together with this completion work. That work was preserved throughout verification.

## 3. What was built

### 3.1 Locale infrastructure (new files)

| File | Purpose |
| --- | --- |
| `app/src/main/java/in/c1ph3rj/scanly/core/common/AppLanguage.kt` | Enum of the 25 languages: BCP-47 `tag`, `nativeName` (autonym, e.g. "தமிழ்"), `displayName(locale)`, `from(Locale)` matching (handles legacy `in` for Indonesian, maps any `pt` → pt-BR, only Simplified Chinese matches `zh`; Traditional falls back to default), and `stampsInOwnScript` (see §6). |
| `.../core/common/AppLocales.kt` | Applies the app language. **Android 13+**: `LocaleManager.applicationLocales` (system persists it and shows it in system Settings › Apps › Scanly › Language). **Android 10–12**: choice stored in SharedPreferences `scanly_app_locale/language_tag`, plus `Locale.setDefault`. Both paths notify `AppLocaleProvider` to update resources in the existing composition. `null` = follow phone language. Locale changes do not recreate MainActivity. |
| `.../core/common/AppLocaleProvider.kt` | Supplies localized Compose context, resources, configuration and layout direction in place, with the Activity retained in the context-wrapper chain. |
| `.../core/common/StampDates.kt` | `formatStampDate()` for dates drawn into PDFs/scans; uses the app locale only if the script is PDF-drawable, else English. |
| `.../feature/settings/SettingsLanguageScreen.kt` | Language picker: "System default" row (shows phone language) + list of 25 languages (native name, with the name in the current UI language as subtitle), check-circle selection, note that document names/OCR text keep their own language. |
| `app/src/main/res/xml/locales_config.xml` | Locale list for the platform (referenced by `android:localeConfig` in the manifest). |
| `app/src/test/java/in/c1ph3rj/scanly/core/common/AppLanguageTest.kt` | Tests: 25 languages, matching rules, Indonesian legacy code, zh-Hans vs zh-Hant, stamp dates, `locales_config.xml` ↔ enum stay in sync. |

### 3.2 Changes to existing files

- `MainActivity.kt`: hosts `AppLocaleProvider`, which supplies localized context, resources, configuration and layout direction without replacing the composition. Its themed context retains the Activity wrapper chain for permissions and Hilt.
- `ScanlyApplication.kt`: `AppLocales.applyDefaultLocale(this)` before `AppStrings.init(this)`; configuration callbacks refresh the locale provider and non-Compose resource cache, including changes made in Android Settings.
- `core/common/AppStrings.kt` (non-Compose string lookups via `appString()` / `appPluralString()`): now resolves through a localized context (`AppLocales.wrap`), cache cleared by `AppStrings.onLocaleChanged()`.
- `AndroidManifest.xml`: `android:localeConfig="@xml/locales_config"`; MainActivity handles `locale|layoutDirection|screenLayout` configuration changes in place; the "Open in Scanly" intent labels now use `@string/intent_open_in_scanly`.
- Navigation: `SettingsLanguageDestination` (`settings/language`) in `navigation/ScanlyDestination.kt`; composable added in `navigation/ScanlyNavHost.kt`; new Language row (icon `Icons.Filled.Language`) in the Preferences group of `feature/settings/SettingsScreen.kt` (`onOpenLanguage`).
- **57 user-facing error messages** from `require`/`error`/exceptions in the data/domain layer (they reach snackbars via `ScanlyError.message`) moved to `error_*` strings. Developer-only invariants (ML/OpenCV internals) intentionally left in English.
- **Grammar fixes (no more English word-splicing):**
  - `SignatureKind` (`domain/model/SignModels.kt`) now has whole phrases: `addLabel`, `deleteLabel`, `deleteQuestion`, `openOptionsLabel`, `savedLabel`, `showFirstLabel` (strings `sign_kind_*`). Old templates like `"Add %1$s"` + `label.lowercase()` were removed.
  - Folder delete dialog uses one plural `folder_delete_what_should_happen` instead of splicing "it/them".
  - Watermark style summary uses localized "Diagonal/Horizontal", `pdf_watermark_opacity_percent`, joined with " · ".
  - Watermark presets `CONFIDENTIAL/DRAFT/COPY` are now strings `pdf_watermark_preset_*`.
- **Locale-aware formatting:** `uppercase()`/`lowercase()` use `Locale.getDefault()` (Turkish dotted i); `StorageFormatter` and PDF-to-images sizes use the default locale (decimal comma); update dialog date uses `FormatStyle.MEDIUM`; library sections use translated Today/Yesterday/"Previous 7 days", `TextStyle.FULL_STANDALONE` month names (correct nominative in ru/uk/pl) and `library_section_month_year` (`"%1$s %2$d"`, so CJK can write `%2$d年%1$s`).
- `app_name` marked `translatable="false"`.
- Docs: `CHANGELOG.md` (Unreleased › Added), `AGENTS.md` (string/translation rule + route), `docs/architecture/navigation.md` (route table).

## 4. Translation pipeline (`.i18n-work/`)

`.i18n-work/` is in the repo root but **git-ignored** (listed in `.git/info/exclude`). It only exists on the owner's machine.

| Path | Content |
| --- | --- |
| `.i18n-work/STYLE_GUIDE.md` | **Read this first.** App description, workflow, escaping rules, plural rules, untranslatable terms, register per language (tú/você/vous/du/…/आप/நீங்கள்/です・ます/해요체), length limits, PDF-stamp rule, required terminology list. |
| `.i18n-work/en/part01.xml … part08.xml` | English source split into 8 parts (~215 entries each, with screen comments). |
| `.i18n-work/<lang>/partNN.xml` | Translations, one folder per language code. |
| `.i18n-work/i18n_tool.py` | Splitter / validator / merger. |

Commands (run from the Scanly repo root):

```bash
python .i18n-work/i18n_tool.py status            # parts present / passing per language
python .i18n-work/i18n_tool.py check <lang> [N]  # validate all parts, or part N
python .i18n-work/i18n_tool.py merge <lang>      # writes app/src/main/res/values-<qualifier>/strings.xml (refuses if checks fail)
```

> ⚠️ Do **not** run `split` again unless `values/strings.xml` changes. It rebuilds `en/` and would shift entries between parts, so existing translated parts would no longer match.

The validator enforces: the same names as the English part, no missing or extra entries, well-formed XML, escaped `'`/`"`/`&`/`<`, no leading `@`/`?`, identical placeholders (`%1$s`, `%d`, …; reordering positional ones is allowed), stray `%` in formatted strings, and exactly the required CLDR plural quantities per language:

- one, other: de, nl, tr, hi, ta, bn, te, mr, kn, ml, gu
- one, many, other: es, pt-BR, fr, it
- one, few, many, other: ru, uk, pl
- other only: id, vi, ja, ko, zh-CN, th

Language code → resource folder: `es`→`values-es`, `pt-BR`→`values-pt-rBR`, `id`→`values-in` (Android's legacy code), `zh-CN`→`values-zh-rCN`, all others `values-<code>`.

## 5. Translation completion

All 24 languages have all eight parts, and every part passes the validator. All parts have been merged into the shipped Android resource folders. Do not restart the translation draft jobs: they were temporary tools for this completion pass and may overwrite subsequent wording corrections.

The remaining 61 parts were initially drafted through Google's translation endpoint. The completion pass then corrected context-sensitive terms, count grammar (including Russian/Ukrainian/Polish forms), language-picker and onboarding wording, PDF stamp exceptions, search operators, title-format examples, and English remnants in Malayalam/Gujarati. Existing translations were retained with focused corrections. **Independent native-speaker review has not been performed.**

The source split was kept stable. New count/progress/error resources were appended to `en/part08.xml` and each translated part 8; that last part now has 221 entries. Do not re-run `split` merely because this exceeds the original 215-entry target.

## 6. Known constraints and decisions

1. **PDF stamps:** watermark text and signature/date stamps are drawn with Roboto/Helvetica via `PdfFontResolver`, which can't draw Devanagari, Tamil, Bengali, Telugu, Kannada, Malayalam, Gujarati, CJK or Thai. For those languages:
   - `pdf_watermark_preset_confidential`, `_draft`, `_copy` and `pdf_watermark_for_kyc_use_only` stay **in English**. The style guide tells translators this.
   - `formatStampDate()` falls back to English month names.
   - Proper fix later: embed Noto fonts for each script.
2. **Lint `MissingTranslation` is an error.** Every `values-xx/strings.xml` must contain every translatable key. Any new English string must be added to all 24 files, or the build's lint fails.
3. On **Android 10–12**, home-screen widgets and app shortcuts (`res/xml/shortcuts.xml`) use the device language, because they're rendered outside the localized composition. Android 13+ is fully covered.
4. Traditional Chinese isn't shipped; `zh-TW`/`zh-HK` users get English.
5. Digits stay ASCII inside strings; the format functions handle locale decimals.

## 7. Approved commit scope and optional follow-ups

The implementation, merge, build, lint and emulator verification are complete. The owner selected **“Commit all work together”** on 2026-11-09, authorizing one commit containing both the earlier WIP and the i18n completion. `.i18n-work/`, `local.properties`, keystores and build outputs remain excluded.

The original handover requests this trailer when the combined contribution is committed:
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

Optional follow-ups:

- Independent native-speaker review of the translations.
- Noto font embedding for PDF stamps in non-Latin scripts.
- Localized Play Store listings and documentation.
- Ensure CI runs the tracked `AppTranslationsTest`; it validates the shipped catalogs without depending on the ignored translation work directory.

## 8. Rules for future string changes

- Never hard-code user-visible text. Use `stringResource(R.string.x)` in composables and `appString(R.string.x)` / `appPluralString(...)` elsewhere, resolved when shown and not cached in enums or `object`s.
- Write whole sentences with positional placeholders (`%1$s`); never concatenate translated fragments or `lowercase()` a label to splice it into another sentence.
- Use `<plurals>` for anything with a count.
- After adding strings to `values/strings.xml`, add translations to all 24 `values-*` files (lint enforces this).

## 9. Completion changes and verification record (2026-11-09)

### Additional issues fixed during completion

- Replaced English-only count branches in Home/Library cards, document/page selection, Image to text, Settings, trash retention and PDF tools with plural resources. Added ten count groups and a whole progress phrase to all languages.
- Localized 29 error fallbacks and remaining known image/storage exceptions, plus the two app-shell PDF/link toast messages.
- Corrected the local merger to carry `formatted="false"` from English into every translation. Added `AppTranslationsTest` to check complete key sets, resource types, plural quantities, format arguments, formatting metadata and PDF-stamp exceptions.
- Replaced non-observable Compose locale reads with configuration-aware locale/resources access.
- Visual review found fixed-height clipping. Home cards now accommodate two-line labels; tool cards grow with translated titles; empty-state actions flow onto another row; navigation labels allow two lines and account for font scale.
- Resolved the existing CameraX transform opt-in lint errors. Kept `android:tint` for framework `RemoteViews` widget images, with a targeted, explained `UseAppTint` suppression.

### Checks

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

All passed. The complete final unit-suite run reports 625 tests with no failures/errors/skips. Lint reports zero errors and no missing-translation/string-format findings; it retains advisory warnings, including the legacy Indonesian `values-in` / canonical `id` locale-config alias warning. `id-ID` resolution was verified on both emulator versions.

`AppLocaleDeviceTest` passed all three tests on **API 30** (`scanly_i18n_api30`, `emulator-5556`) and **API 36** (`scanly_audit`, `emulator-5554`):

- Real Android resource resolution for all 25 languages, formatting every string with source argument types, representative plural counts, regional Indonesian/Portuguese/Simplified-Chinese matching and unsupported/Traditional-Chinese English fallback.
- Language-picker switching, non-Compose `appString` refresh, activity recreation, Home/Library/Tools/PDF-reader entry screens, and vertical text-layout checks for Home controls and the Library secondary action.
- All three onboarding pages in Spanish, German, Hindi, Tamil, Malayalam, Japanese and Russian.

The resource test was repeated successfully on both versions after the final Russian/Ukrainian terminology corrections. The seven-language screen flow also passed again on the final API 36 build. A separate UI-driven check selected **Tamil**, force-stopped and relaunched the app, and verified Tamil Home controls on both versions. It restored the original language and exact preference backups afterward. The explicit English test choice remaining after the early native emulation crash was subsequently cleared; both emulators were left on System default.

The UI fixtures temporarily disable automatic model calibration to avoid the repository's documented ARM-only LiteRT crash under x86 ARM translation; the setting is restored. These are localization checks, not ML/camera validation. No physical device or release build was validated, and large-font/tablet layouts were not separately exercised.

### Local evidence

All paths below are under the ignored `.i18n-work/` folder unless stated otherwise:

- `verification-layout.log`: full build, instrumentation build, unit suite and lint success.
- `final-wording.log`, `final-unit-suite.log`: final terminology build/contract checks and complete 625-test recheck.
- `device-api30-layout.log`, `device-api36-layout.log`: all three device tests passing on each version.
- `device-api30-final-resources.log`, `device-api36-final-resources.log`: final resource-only rechecks. `device-api36-final-ui.log`: final seven-language screen-flow recheck.
- `cold-start-final.log`, `cold-start-api30/`, `cold-start-api36/`: process-restart checks, UI dumps, screenshots and restoration backups.
- `qa-api30/`, `qa-api36/`: 56 screenshots per version, plus contact sheets. Reviewed the seven representative languages; secondary descriptive captions/search placeholders can still ellipsize normally, while action labels can wrap or be reached by scrolling.
- Standard Gradle reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`.


## 10. Smooth language-switch follow-up (2026-11-09)

The earlier locale setters recreated the activity, replacing the window and rebuilding its composition. Language selection now updates the existing composition through `AppLocaleProvider`. Navigation, picker scroll position and the active theme stay alive. Android 13+ still uses the platform's per-app language setting; Android 10–12 still persists the private preference and updates `Locale.getDefault`.

The API 36 emulator reported locale configuration flags **0x2104** (`locale`, `layoutDirection`, `screenLayout`). MainActivity handles all three, so the first change from System default also stays in place. Other configuration changes retain their existing recreation behavior. Application configuration callbacks refresh the non-Compose resource cache and rebind widgets, replacing the rebind that previously occurred during locale-driven `onCreate`.

On Android 10–12 the Activity's base context remains in the device language. Compose reads `stringResource`/`LocalResources` from the provider; non-Compose callers use `appString`/`appPluralString` through `AppLocales.wrap`.

Verification:

- Final `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` and `lintDebug` succeeded: **625 JVM tests passed; 0 lint errors**. Log: `.i18n-work/smooth-final-build.log`.
- All four locale device tests passed on API 30 and API 36. The picker regression asserts the **same Activity and Window instances** and retained list scroll state while switching seven languages, then verifies explicit recreation, navigation and translated text. The additional test covers System default and platform-originated locale changes. The final APK's full API 36 suite and focused API 30 follow-System-default check passed: `smooth-final-api36.log`, `smooth-final-api30.log`; initial full API 30 log: `smooth-device-5556.log`.
- Six screenrecord clips cover English → Tamil in **light, dark and pure-black** themes across both Android versions. All captured content frames contained the UI; none were blank. Screenrecord uses variable frame rates; this is transition verification, not a frame-rate benchmark. Clips and analysis: `.i18n-work/smooth-api30/`, `.i18n-work/smooth-api36/`; recording logs: `smooth-video-30.log`, `smooth-video-36.log`.
- A UI-selected Tamil language survived force-stop/relaunch on the final APK on both versions. Original language and preference backups were restored afterward. Logs: `smooth-cold-30.log`, `smooth-cold-36.log`.
- The locale tests and recording fixtures temporarily disable automatic model calibration to avoid the documented x86/ARM LiteRT emulation crash, then restore it. Production model behavior is unchanged. No physical-phone installation or performance check was performed.
