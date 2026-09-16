# Running and testing Scanly

Everything needed to install the debug app, use it on a device or emulator, and run the automated suites. Companion pages: [setup.md](setup.md) (SDK, signing, variants) and [testing.md](testing.md) (coverage inventory).

All Gradle commands below are from the **Scanly repository root** (`Scanly/`) in PowerShell.

## What you need

| Requirement | Notes |
| --- | --- |
| Android Studio | Recent stable with Jetpack Compose |
| JDK | Compile target **11**; Gradle daemon **21** (`gradle/gradle-daemon-jvm.properties`) |
| Android SDK | Compile API **37**, target API **36**, min API **29** |
| Gradle wrapper | **9.6.0** (`gradlew.bat` — do not install Gradle separately) |
| Device or emulator | Android 10+ (API 29). Camera is optional at install time; scanning needs a camera |

Native libraries ship for **`arm64-v8a`** and **`armeabi-v7a` only**. A physical ARM phone is the most reliable target. An x86_64 emulator still runs (ARM translation) but LiteRT model calibration can SIGSEGV; the connected test suite isolates that so one crash does not abort the rest.

Confirm the SDK path in `local.properties` (`sdk.dir=...`). That file is local and must not be committed.

## Open the project

```powershell
cd Scanly
```

Open this folder in Android Studio (File → Open). Let Gradle sync. The only Android module is `:app`. Application id: `in.c1ph3rj.scanly`.

## Run the app

### Android Studio

1. Select the **debug** variant (not `githubRelease` / `playStoreRelease`).
2. Plug in a phone with USB debugging, or start an emulator (API 29+).
3. Run `app` (green play). Entry point is `MainActivity`.

First launch shows **onboarding**. Tap **Get started** to reach Home (Scan / Import / Folder). Later launches skip onboarding.

### Command line (install debug APK)

```powershell
# Build
./gradlew.bat assembleDebug

# See devices (status must be "device", not unauthorized / offline)
adb devices -l

# Install and launch
./gradlew.bat installDebug
adb shell am start -n in.c1ph3rj.scanly/.MainActivity
```

If `adb devices` is empty, Android Studio did not start a device, USB debugging is off, or the SDK `platform-tools` are not on `PATH`. Use the SDK copy:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

### First-run permissions

| Permission | When |
| --- | --- |
| Camera | Scan session / QR scan |
| Notifications | Backup/restore progress on Android 13+; declining does not block the job |
| Internet | Optional update check only |

Grant camera when you want to scan. Gallery import uses the system photo picker (no storage permission on current SDKs).

### Create an emulator if you have none

Android Studio: Device Manager → Create device → Pixel 7 (or similar) → a system image API 29+.

CLI example (adjust the system-image package to one you already have under `Sdk\system-images`):

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
& "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd `
  -n scanly_ui_test `
  -k "system-images;android-36.1;google_apis_playstore;x86_64" `
  -d pixel_7 --force

# Keep this process running
& "$sdk\emulator\emulator.exe" -avd scanly_ui_test -no-audio -no-boot-anim
```

In another terminal, wait until the serial shows `device` and boot is complete:

```powershell
adb wait-for-device
adb shell getprop sys.boot_completed   # expect 1
```

## Manual smoke path

After Home is visible:

1. **Home** — Scan / Import / Folder actions.
2. **Library** — All / Folders / Documents, search.
3. **Tools** — QR (scan or generate), PDF workspace.
4. **Settings** — Appearance, Storage & backup, Document detection.

Live camera, the system photo picker, SAF folder pick, share sheet, widgets, and Play in-app updates are manual-only (not in the automated UI suite).

## Automated tests

Two layers:

| Layer | Command | Needs device? | What it proves |
| --- | --- | --- | --- |
| JVM unit | `./gradlew.bat testDebugUnitTest` | No | Domain use cases, routing helpers, library visibility, export/archive/QR/PDF/settings flows with in-memory fakes |
| Instrumented | `./gradlew.bat connectedDebugAndroidTest` | Yes (`adb` status `device`) | Real `MainActivity` UI, onboarding Compose, OpenCV filters, package smoke |

Also useful:

```powershell
./gradlew.bat lintDebug
./gradlew.bat assembleDebugAndroidTest   # compile instrumented tests without a device
```

### JVM unit tests (no device)

```powershell
./gradlew.bat testDebugUnitTest
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`.

Flow tests that drive **shipped use cases** (not mocked UI) live under `app/src/test/java/in/c1ph3rj/scanly/domain/usecase/`:

| Class | Coverage |
| --- | --- |
| `DocumentLibraryFlowTest` | Create / rename / delete documents and groups, duplicate-safe titles, membership, Library All/Folders/Documents search and sort, page reorder/delete |
| `ScanSessionAndPageEditFlowTest` | Add vs retake scan routes, gallery import cap of 10, crop / filter / adjust persist |
| `ExportArchiveAndClearDataFlowTest` | Document PDF + image ZIP, group merged PDF + zipped PDFs, backup then restore Replace vs Merge, clear-all-data |
| `ToolsSettingsAndLaunchActionFlowTest` | QR generate, PDF inspect/merge/compress/password/watermark, appearance and detection prefs, onboarding flag, SCAN/IMPORT/QR/LIBRARY redirects |

Run one class:

```powershell
./gradlew.bat testDebugUnitTest --tests "in.c1ph3rj.scanly.domain.usecase.DocumentLibraryFlowTest"
```

These tests use contract-complete in-memory repositories behind the existing interfaces. They do **not** exercise CameraX, Room, DataStore, WorkManager, or OpenCV on the JVM.

### Instrumented / device UI tests

```powershell
adb devices -l    # at least one line ending in "device"
./gradlew.bat connectedDebugAndroidTest
```

If Gradle reports `No connected devices!` while `adb devices` lists a phone, Gradle is not using the same adb. Set the SDK and serial, then retry:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_SERIAL = "<serial from adb devices>"   # e.g. emulator-5554
./gradlew.bat connectedDebugAndroidTest
```

Optional: turn off system animations (the build already sets `animationsDisabled` for the test runner):

```powershell
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

Reports: `app/build/reports/androidTests/connected/debug/index.html`.

#### What the device UI suite does

`ScanlyDeviceUiTest` starts the **real** `MainActivity` (Hilt app process), not an isolated composable. Android Test Orchestrator clears app data before each test, so onboarding runs again. Camera and (on API 33+) notifications are granted; leftover system dialogs are dismissed.

| Test | Actions and assertions |
| --- | --- |
| `onboardingCompletesThenHomeIdentifyingUiIsVisible` | Taps **Get started** if shown; Home with Scan and Folder is visible |
| `libraryDestinationShowsIdentifyingUi` | Taps Library; Folders and Documents tabs visible |
| `toolsDestinationShowsIdentifyingUi` | Taps Tools; PDF workspace and QR CODE visible |
| `settingsDestinationShowsIdentifyingUi` | Taps Settings; Appearance and Storage & backup visible |
| `generatingAQrCodeShowsAPreview` | Tools → Generate a code → types a URL → **QR code preview** is shown |

Helpers live in `ScanlyDeviceUiRobot.kt`. Selectors are `ScanlyTestTags` in `app/src/main/.../core/ui/ScanlyTestTags.kt` (nav tabs and screen roots).

Also still run on device:

- `OnboardingScreenTest` — Compose onboarding layouts (setContent)
- `OpenCvPageFilterProcessorTest` — filter engine on device
- `ExampleInstrumentedTest` — package name

Run only the shell UI class:

```powershell
./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=in.c1ph3rj.scanly.ScanlyDeviceUiTest
```

#### What device UI tests do not cover

Live CameraX capture, LiteRT corner/gate on a real camera, auto-capture, torch, system photo picker, SAF folder picker, Android share sheet, widget pin, Play in-app update UI, and a single NavHost marathon of every route.

### Lint

```powershell
./gradlew.bat lintDebug
```

## Troubleshooting

| Symptom | What to try |
| --- | --- |
| `adb devices` empty | Unlock the phone, accept the RSA prompt, or start an emulator. Use SDK `platform-tools\adb.exe`. |
| `DeviceException: No connected devices!` | Same adb as Gradle: set `ANDROID_HOME` / `ANDROID_SDK_ROOT` to `sdk.dir`. Set `ANDROID_SERIAL`. Status must be `device`, not `unauthorized` or `offline`. |
| Device is `unauthorized` | Re-plug USB, tap Allow USB debugging. |
| Instrumented compile only | `./gradlew.bat assembleDebugAndroidTest` — no device needed. |
| Emulator LiteRT crash during Settings | Known on x86_64 + ARM translation. Orchestrator continues other tests. Prefer a physical ARM device for ML/camera. |
| Onboarding skipped in a manual session | DataStore already completed onboarding. App info → Clear storage to see it again. Automated UI tests clear data themselves. |
| Release build unsigned | Set `SCANLY_RELEASE_*` (see [setup.md](setup.md)). Debug does not need a keystore. |

## Related docs

- [setup.md](setup.md) — SDK levels, variants, signing
- [testing.md](testing.md) — unit-test inventory and contribution expectations
- [../overview/user-guide.md](../overview/user-guide.md) — product workflows
- [../architecture/navigation.md](../architecture/navigation.md) — routes
- [../../README.md](../../README.md) — project overview
