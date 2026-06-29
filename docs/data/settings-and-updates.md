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
| `GitHubReleaseUpdateRepository` | Fetches latest GitHub release |
| `DefaultAppUpdatePromptRepository` | Stores dialog cooldown timestamp |
| `CheckForAppUpdateUseCase` | Compares remote vs installed version |
| `AppUpdateViewModel` | Orchestrates automatic and manual checks |
| `AppUpdateDialog` | UI overlay with release notes |
| `AppUpdateDialogCooldown` | 6-hour rate limit |
| `AppVersionComparator` | Semver-style version comparison |
| `ReleaseMarkdown` | Parses release body for dialog display |

### API endpoint

```
GET https://api.github.com/repos/c1ph3rj/Scanly/releases/latest
```

Parses: tag name, release body (markdown), HTML URL, optional APK asset metadata.

### Cooldown DataStore: `scanly_update_prompt`

| Key | Type | Purpose |
| --- | --- | --- |
| `last_update_dialog_shown_at_millis` | Long | Timestamp of last dialog display |

Dialog shown at most once every **6 hours** (since v1.0.7).

### Update check triggers

| Trigger | When |
| --- | --- |
| Automatic | `MainActivity` `ON_START`, only after onboarding complete |
| Manual | Settings "Check for updates" button |

### Download action

Since v1.0.8: opens `release.htmlUrl` in the browser. No in-app APK download or `REQUEST_INSTALL_PACKAGES` permission.

### Flow diagram

```
ON_START / Settings button
  → CheckForAppUpdateUseCase
    → GitHubReleaseUpdateRepository.fetchLatestRelease()
    → AppVersionComparator.isRemoteNewer()
  → If update available AND cooldown OK
    → AppUpdateDialog
  → User taps Download
    → Open browser at GitHub release page
```

## Related docs

- [../architecture/screens.md](../architecture/screens.md) — SettingsViewModel mapping
- [../releases.md](../releases.md) — version policy
- [../overview/features.md](../overview/features.md) — user-facing settings features