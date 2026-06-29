# Settings and Updates

User preferences, bundled support content, and the optional app update flow.

## DataStore: `scanly_settings`

Managed by `DefaultSettingsRepository` (`data/settings/`).

| Key | Type | Default | Purpose |
| --- | --- | --- | --- |
| `theme_mode` | String | `SYSTEM` | `SYSTEM`, `LIGHT`, or `DARK` |
| `onboarding_completed` | Boolean | `false` | First-run gate flag |

### Theme flow

```
SettingsViewModel.setThemeMode()
  → SetThemeModeUseCase → SettingsRepository
  → DataStore write

AppSettingsViewModel (MainActivity)
  → ObserveThemeModeUseCase → Flow
  → ScanlyTheme(darkTheme = resolved)
```

## Bundled assets

| Asset | Purpose |
| --- | --- |
| `assets/settings/faqs.json` | FAQ entries for Settings screen |
| `assets/settings/licenses.json` | Third-party license disclosures |
| `assets/models/document_corners_float16.tflite` | ML corner detection model |
| `assets/models/README.txt` | Model placement instructions |
| `assets/adi-registration.properties` | Model registration metadata |

`LoadSettingsContentUseCase` parses FAQ and license JSON into `SettingsContent` domain model.

## Settings screen sections

| Section | Data source |
| --- | --- |
| Appearance | DataStore `theme_mode` |
| About | `PackageManager.versionName` from `DefaultSettingsRepository` |
| Support | `faqs.json`, `licenses.json` |
| Storage | `GetAppStorageUsageUseCase` |
| Data management | `ClearAllAppDataUseCase` |
| Updates | Manual trigger → `AppUpdateViewModel` |

## App update flow

### Components

| Class | Role |
| --- | --- |
| `PlayStoreAppUpdateRepository` | Checks Google Play for update availability |
| `DefaultPlayInAppUpdateCoordinator` | Starts, resumes, and completes Play in-app updates |
| `GitHubReleaseUpdateRepository` | Fetches release notes for the update dialog |
| `DefaultAppUpdatePromptRepository` | Stores dialog cooldown timestamp |
| `CheckForAppUpdateUseCase` | Delegates to `AppUpdateRepository` |
| `AppUpdateViewModel` | Orchestrates automatic and manual checks |
| `AppUpdateDialog` | UI overlay with release notes |
| `FlexibleUpdateSnackbarHost` | Prompts restart after flexible update download |
| `AppUpdateDialogCooldown` | 6-hour rate limit |
| `PlayInAppUpdatePolicy` | Chooses flexible vs immediate update type |
| `ReleaseMarkdown` | Parses release body for dialog display |

### Update sources

| Source | Purpose |
| --- | --- |
| Google Play In-App Update API (`app-update` 2.1.0) | Availability, download, and install |
| GitHub Releases API | Optional release notes enrichment |

### Cooldown DataStore: `scanly_update_prompt`

| Key | Type | Purpose |
| --- | --- | --- |
| `last_update_dialog_shown_at_millis` | Long | Timestamp of last dialog display |

Dialog shown at most once every **6 hours** for automatic checks (since v1.0.7).

### Update check triggers

| Trigger | When |
| --- | --- |
| Automatic | `MainActivity` `ON_START`, only after onboarding complete |
| Manual | Settings "Check for updates" button |

### Update types

| Type | When used |
| --- | --- |
| Flexible | Default optional updates; downloads in background and prompts restart |
| Immediate | High-priority updates (`inAppUpdatePriority >= 4`) or stalled immediate flows |

High-priority immediate updates launched automatically on `ON_START` skip the custom dialog and open the Play Store flow directly.

### Download action

User taps **Update** in the dialog or Settings row. Scanly launches the Google Play in-app update flow. Flexible updates show a restart snackbar after download completes.

### Flow diagram

```
ON_START / Settings button
  → CheckForAppUpdateUseCase
    → PlayStoreAppUpdateRepository.checkForUpdate()
      → DefaultPlayInAppUpdateCoordinator.refreshAvailability()
      → GitHubReleaseUpdateRepository.fetchLatestReleaseNotes() (optional)
  → If update available AND cooldown OK
    → AppUpdateDialog
  → User taps Update
    → Play in-app update flow
  → ON_RESUME
    → Resume stalled immediate updates or show flexible restart snackbar
```

### Testing notes

In-app updates only work for builds installed from Google Play (internal, closed, open, or production tracks). Debug sideloads and emulator installs without Play Store distribution will report no update available.

## Related docs

- [../architecture/screens.md](../architecture/screens.md) — SettingsViewModel mapping
- [../releases.md](../releases.md) — version policy
- [../overview/features.md](../overview/features.md) — user-facing settings features