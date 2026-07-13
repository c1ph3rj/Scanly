# Settings and Updates

User preferences, bundled support content, export destination, and the optional app update flow.

## DataStore: `scanly_settings`

Managed by `DefaultSettingsRepository` (`data/settings/`).

| Key | Type | Default | Purpose |
| --- | --- | --- | --- |
| `theme_mode` | String | `"system"` | `SYSTEM`, `LIGHT`, or `DARK` (`ThemeMode.storageValue`) |
| `pure_black_enabled` | Boolean | `false` | Pure black Material 3 surfaces when dark theme is active (AMOLED) |
| `onboarding_completed` | Boolean | `false` | First-run gate flag |
| `export_tree_uri` | String? | null | Persisted SAF tree URI for custom export/backup base |
| `export_tree_label` | String? | null | Display name for custom export folder |
| `live_detection_model` | String | `"legacy"` | Manual live-preview corner model (`lite` / `standard` / `accurate`=High / `legacy`=Accurate) |
| `post_processing_model` | String | `"legacy"` | Manual captured-image corner model (same storage values) |
| `automatic_document_model_selection` | Boolean | `true` | Calibrate Lite/Standard/High on device; lock manual selectors |
| `document_gate_enabled` | Boolean | `true` | Run or bypass the physical-document semantic gate in both pipelines |

When `export_tree_uri` and `export_tree_label` are both set, `ExportDestination.CustomTree` is used; otherwise `ExportDestination.DefaultDownloadsScanly` (`Downloads/Scanly`).

### Theme / look & feel flow

```
SettingsViewModel.setThemeMode() / setPureBlackEnabled()
  → SetThemeModeUseCase / SetPureBlackEnabledUseCase
  → SettingsRepository → DataStore write

AppSettingsViewModel (MainActivity)
  → ObserveThemeModeUseCase + ObservePureBlackEnabledUseCase
  → ScanlyTheme(darkTheme = resolved, pureBlack = preference)
```

Pure black only changes surface colors when dark theme is active (Dark mode, or System when the OS is dark). The preference can still be toggled while Light is selected and takes effect later.

### Document detection preferences

| Control | Behavior |
| --- | --- |
| Automatic model selection | Benchmarks Lite/Standard/High once per process; assigns live and post models under latency budgets |
| Live preview model | Manual pick when automatic is off; chip + bottom-sheet picker in Settings |
| Post-processing model | Independent manual pick for capture/import finalize |
| Physical-document gate | Enable/bypass gate inference for live + post pipelines |
| Model benchmark | Temporary local image run; does not persist results |

## Bundled assets

| Asset | Purpose |
| --- | --- |
| `assets/settings/faqs.json` | FAQ entries for Settings FAQ sub-screen |
| `assets/settings/licenses.json` | Third-party license disclosures |
| `assets/models/document_corners_lite.tflite` | Lite corner model (224) |
| `assets/models/document_corners_standard.tflite` | Standard corner model (288) |
| `assets/models/document_corners_accurate.tflite` | High corner model (384 regression) |
| `assets/models/document_corners_float16.tflite` | Accurate corner model (YOLO-pose; formerly Legacy) |
| `assets/models/scanly_document_gate_float16.tflite` | Physical-document semantic gate |
| `assets/models/README.txt` | Model placement and contract notes |
| `assets/adi-registration.properties` | Model registration metadata |

`LoadSettingsContentUseCase` parses FAQ and license JSON into `SettingsContent` domain model.

## Settings screen sections

| Section | Route / action | Data source |
| --- | --- | --- |
| Look & feel | `settings` | DataStore `theme_mode`, `pure_black_enabled` |
| Storage & backup | `settings/storage` | Storage usage, export destination, archive work |
| Document detection | `settings` | Model preferences, device calibration, and gate toggle |
| Model benchmark | `settings/model-benchmark` | Temporary per-image and aggregate local measurements |
| About | `settings` | `PackageManager.versionName` |
| Support | `settings` | Email, project website links |
| FAQs | `settings/faq` | `faqs.json` |
| Legal | `legal/{documentType}` | Bundled WebView content |
| Licenses | `settings/licenses` | `licenses.json` |
| Updates | `settings` | Manual trigger → `AppUpdateViewModel` |

Clear-all-data and backup/restore live on the **Storage & backup** sub-screen, not the main settings list.

## App update flow

### Components

| Class | Role |
| --- | --- |
| `GitHubAppUpdateRepository` | Compares the installed version with the latest GitHub release |
| `PlayStoreAppUpdateRepository` | Checks Google Play for update availability |
| `DefaultPlayInAppUpdateCoordinator` | Starts, resumes, and completes Play in-app updates |
| `NoOpPlayInAppUpdateCoordinator` | No-op coordinator for debug/githubRelease builds |
| `GitHubReleaseUpdateRepository` | Fetches the latest GitHub release and notes |
| `DistributionAppUpdateModule` | Build-type-specific binding that selects the authoritative update repository |
| `DefaultAppUpdatePromptRepository` | Stores dialog cooldown timestamp |
| `CheckForAppUpdateUseCase` | Delegates to `AppUpdateRepository` |
| `AppUpdateViewModel` | Orchestrates automatic and manual checks |
| `AppUpdateDialog` | UI overlay with release notes |
| `FlexibleUpdateSnackbarHost` | Prompts restart after flexible update download |
| `AppUpdateDialogCooldown` | 6-hour rate limit |
| `PlayInAppUpdatePolicy` | Chooses flexible vs immediate update type |
| `ReleaseMarkdown` | Parses release body for dialog display |

### Flavor-specific source sets

| Source set | Update binding |
| --- | --- |
| `app/src/debug/` | GitHub updates + no-op Play coordinator |
| `app/src/githubRelease/` | GitHub updates + no-op Play coordinator |
| `app/src/playStoreRelease/` | Play Store updates + real Play coordinator |

### Update sources

| Source | Purpose |
| --- | --- |
| `githubRelease` build type | GitHub Releases API is authoritative for availability and release notes |
| `playStoreRelease` build type | Google Play is authoritative for availability/download/install; GitHub optionally enriches notes |

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

### Distribution channels

| Variant | Availability check | Update action |
| --- | --- | --- |
| `githubRelease` | Compare installed `versionName` with the latest GitHub release tag | Open the GitHub release page |
| `playStoreRelease` | Google Play In-App Update API | Flexible or immediate Play update flow |

Both variants use the same application ID and release version. `BuildConfig.UPDATE_CHANNEL` and build-type-specific Hilt modules keep the updater fixed to the artifact that was built.

### Play Store update types

| Type | When used |
| --- | --- |
| Flexible | Default optional updates; downloads in background and prompts restart |
| Immediate | High-priority updates (`inAppUpdatePriority >= 4`) or stalled immediate flows |

High-priority immediate updates launched automatically on `ON_START` skip the custom dialog and open the Play Store flow directly.

### Download action

User taps **Update** in the dialog. The GitHub build opens the corresponding GitHub release page. The Play Store build launches the Google Play in-app update flow; flexible updates show a restart snackbar after download completes.

### Flow diagram

```
ON_START / Settings button
  → CheckForAppUpdateUseCase
    → Build-type-specific AppUpdateRepository
      → github: GitHubAppUpdateRepository
        → GitHubReleaseUpdateRepository.fetchLatestReleaseNotes()
        → compare installed version with release tag
      → playStore: PlayStoreAppUpdateRepository
        → DefaultPlayInAppUpdateCoordinator.refreshAvailability()
        → GitHubReleaseUpdateRepository.fetchLatestReleaseNotes() (optional)
  → If update available AND cooldown OK
    → AppUpdateDialog
  → User taps Update
    → github: open GitHub release page
    → playStore: Play in-app update flow
  → playStore ON_RESUME
    → resume stalled immediate update or show flexible restart snackbar
```

### Testing notes

Play in-app updates only work for `playStoreRelease` builds installed from Google Play (internal, closed, open, or production tracks). Sideloaded `githubRelease` builds use GitHub Releases and do not call Play update lifecycle operations.

## Related docs

- [../architecture/screens.md](../architecture/screens.md) — SettingsViewModel mapping
- [library-backup.md](library-backup.md) — backup destination and format
- [../releases.md](../releases.md) — version policy
- [../overview/features.md](../overview/features.md) — user-facing settings features
