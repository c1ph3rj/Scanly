# Navigation

All routes and user flows in Scanly **v1.0.9**.

Navigation is implemented with **Navigation Compose** in `ScanlyNavHost.kt`. Route helpers follow the `*Destination` object pattern with `routePattern` and `route()` factory functions.

## Navigation shell

| Form factor | Chrome |
| --- | --- |
| Phone | `Scaffold` + bottom `NavigationBar` |
| Tablet | Persistent `NavigationRail` (92 dp) with app logo |

- **Start destination:** `home`
- **Tab switches:** no transition animation
- **Detail pushes:** 160 ms fade transition

## Top-level tabs

| Route | Screen | In bottom nav / rail |
| --- | --- | --- |
| `home` | Home dashboard | Yes |
| `library` | Full library | Yes |
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
| `editor/page/{pageId}` | `PageEditorDestination` | Page editor |
| `group/{groupId}` | `GroupDetailDestination` | Group detail |
| `legal/{documentType}` | `LegalDocumentDestination` | Privacy or licenses viewer |

### Scan session arguments

- `documentId` (required) — target document
- `replacePageId` (optional) — when set, capture replaces this page instead of adding a new one. On complete, navigates to `editor/page/{replacePageId}`.

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
  └─► Create + Scan
        └─► camera/session/{newDocId}
              └─► capture page(s)
                    └─► document/{docId}
```

### Edit a page

```
document/{docId}
  └─► preview/page/{pageId}
        └─► editor/page/{pageId}
              ├─► save edits → back to preview or detail
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
Library (Folders tab) or Home (recent groups)
  └─► group/{groupId}
        ├─► document/{docId}  (open member)
        ├─► rename / delete group
        ├─► add/remove documents
        └─► export merged PDF or zipped PDFs
```

### Export

```
document/{docId}
  └─► export sheet
        ├─► PDF → cache/exports → share or save
        └─► image ZIP → cache/exports → share

group/{groupId}
  └─► export sheet
        ├─► merged PDF
        └─► zipped PDF set
```

### Settings

```
settings
  ├─► theme change (immediate, persisted)
  ├─► legal/{PRIVACY} or legal/{LICENSES}
  ├─► check for updates → AppUpdateDialog
  └─► clear all data → confirmation → wipe → Home
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