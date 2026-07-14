# Navigation

All routes and user flows in Scanly **v1.0.10**.

Navigation is implemented with **Navigation Compose** in `ScanlyNavHost.kt`. Route helpers follow the `*Destination` object pattern with `routePattern` and `route()` factory functions.

## Navigation shell

| Form factor | Chrome |
| --- | --- |
| Phone | `Scaffold` + bottom `NavigationBar` |
| Tablet | Persistent `NavigationRail` (92 dp) with app logo |

- **Start destination:** `home`
- **Tab switches:** no transition animation
- **Detail pushes:** 160 ms fade transition
- **Top inset:** each screen applies status-bar padding once (not doubled by the activity shell)

## Top-level tabs

| Route | Screen | In bottom nav / rail |
| --- | --- | --- |
| `home` | Home dashboard | Yes |
| `library` | Full library | Yes |
| `tools` | Tools hub (capture, QR, PDF utilities) | Yes |
| `settings` | Settings | Yes |

## Legacy placeholder routes

These top-level routes still exist but show `FeaturePlaceholderScreen` — they are sprint-era stubs, not active flows:

| Route | Note |
| --- | --- |
| `camera` | Use `camera/session/{documentId}` instead |
| `review` | Review happens in document detail and page preview |
| `editor` | Use `editor/page/{pageId}` instead |

## Typed routes (active flows)

| Route pattern | Helper object | Screen |
| --- | --- | --- |
| `document/{documentId}` | `DocumentDestination` | Document detail |
| `camera/session/{documentId}?replacePageId={pageId}` | `ScanSessionDestination` | Scan session |
| `preview/page/{pageId}` | `PageImagePreviewDestination` | Page preview |
| `editor/page/{pageId}` | `PageEditorDestination` | Page editor (preview, filters/adjust overlays, retake, delete) |
| `crop/page/{pageId}` | `PageCropDestination` | AI detect, rotate, four-point crop, reset, apply |

**Editor overlays (not NavHost routes):** `FilterPickerScreen` and `FilterCustomizeScreen` share `PageEditorViewModel` and replace the editor content in place (same pattern as a full-screen mode, not a bottom sheet).
| `group/{groupId}` | `GroupDetailDestination` | Group detail |
| `legal/{documentType}` | `LegalDocumentDestination` | Privacy or terms viewer |
| `settings/faq` | `SettingsFaqDestination` | FAQ sub-screen |
| `settings/licenses` | `SettingsLicensesDestination` | Open-source licenses |
| `settings/storage` | `SettingsStorageDestination` | Storage & backup |
| `settings/model-benchmark` | `SettingsModelBenchmarkDestination` | Temporary local model comparison |
| `tools/qr` | `ToolsQrDestination` | QR scan + generate |
| `tools/pdf/reader?filePath={filePath}&fileName={fileName}` | `ToolsPdfReaderDestination` | PDF reader; optional app-owned result file opens directly |
| `tools/pdf/merge` | `ToolsPdfMergeDestination` | PDF merge |
| `tools/pdf/compress` | `ToolsPdfCompressDestination` | PDF compress |
| `tools/pdf/password` | `ToolsPdfPasswordDestination` | PDF password protect/remove |
| `tools/pdf/watermark` | `ToolsPdfWatermarkDestination` | PDF text watermark |

### Scan session arguments

- `documentId` (required) — target document
- `replacePageId` (optional) — when set, capture replaces this page instead of adding a new one. On complete, navigates to `editor/page/{replacePageId}`.

### Settings sub-screen ViewModel sharing

`settings/faq`, `settings/licenses`, and `settings/storage` share `SettingsViewModel` via the parent `settings` back stack entry. The model benchmark owns a separate ViewModel because its runs and results are temporary.

## User flow diagrams

### App startup

```
Launch
  └─► Onboarding required?
        ├─ Yes → OnboardingScreen → complete → Home
        └─ No  → Home
              └─► (background) update check if cooldown expired
```

### Create and scan

```
Home / Library
  └─► Create + Scan (optional Suggest name)
        └─► camera/session/{newDocId}
              └─► capture page(s)
                    └─► document/{docId}
```

### Edit a page

```
document/{docId}
  └─► preview/page/{pageId}
        ├─► overflow: Share / Edit / Retake / Delete
        └─► editor/page/{pageId}
              ├─► Filters  → full-screen FilterPickerScreen (live preview + presets; Done → editor)
              ├─► Adjust   → full-screen FilterCustomizeScreen (sliders + compare; Done → editor)
              ├─► crop/page/{pageId}
              │     ├─► AI Detect / rotate / handles / Reset
              │     └─► Done → reprocess crop+rotation → navigateUp → editor
              ├─► editor Done → reprocess filter + adjustments → back to preview/detail
              └─► retake → camera/session/{docId}?replacePageId={pageId}
                              └─► editor/page/{pageId}  (replacement complete)
```

### Gallery import

```
Home (new doc) or document/{docId} (add pages)
  └─► photo picker (≤10 images)
        └─► import pipeline (same as capture finalize)
              └─► document/{docId}
```

### Group workflow

```
Library (Folders filter) or Home (recent groups)
  └─► group/{groupId}
        ├─► document/{docId}  (open member)
        ├─► rename / delete group
        ├─► add/remove documents
        └─► export merged PDF or zipped PDFs
```

### Export

```
document/{docId} or group/{groupId}
  └─► export sheet (PDF options: password, page numbers, orientation, size, margins)
        ├─► Save → configured Downloads/SAF destination (unique filenames)
        └─► Share → cache/exports → FileProvider share sheet
```

### Library backup and restore

```
settings → settings/storage
  ├─► Back up library → WorkManager foreground job → {destination}/backup/*.scanly
  └─► Restore → pick .scanly in backup folder → Replace or Merge as copies
```

### Settings

```
settings
  ├─► Look & feel: theme mode + pure black (AMOLED) toggle
  ├─► settings/storage (usage, destination, backup/restore, clear data)
  ├─► Document detection: automatic or manual live/post models
  ├─► Document detection: physical-document gate on/off
  ├─► settings/model-benchmark (gate + four corner models on local images)
  ├─► settings/faq
  ├─► legal/{PRIVACY} or legal/{TERMS}
  ├─► settings/licenses
  └─► check for updates → AppUpdateDialog (channel-specific)
```

## Onboarding gate

`MainActivity` uses `AnimatedContent` to switch between onboarding states:

| State | UI |
| --- | --- |
| `LOADING` | Blank / loading |
| `REQUIRED` | `OnboardingScreen` |
| `COMPLETE` | `ScanlyNavHost` |

Automatic update checks run on `ON_START` only when onboarding is `COMPLETE`.

## Related docs

- [screens.md](screens.md) — screen and ViewModel mapping
- [../overview/user-guide.md](../overview/user-guide.md) — user-facing walkthrough
- [overview.md](overview.md) — architecture layers
