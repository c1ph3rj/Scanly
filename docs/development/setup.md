# Setup and Build

Get Scanly building locally. For conventions see [conventions.md](conventions.md).

## Prerequisites

| Requirement | Version / notes |
| --- | --- |
| Android Studio | Recent stable with Compose support |
| JDK | 11 compile target; daemon JVM 21 (`gradle/gradle-daemon-jvm.properties`) |
| Android SDK | API 36 compile/target; min SDK 29 |
| Device / emulator | ARM (`arm64-v8a` or `armeabi-v7a`); camera needed for capture testing |

## Clone and open

```powershell
git clone https://github.com/c1ph3rj/Scanly.git
cd Scanly
```

Open the project root in Android Studio. Gradle wrapper version: **9.5.0**.

## Build commands

From repository root (Windows PowerShell):

```powershell
# Debug APK
./gradlew.bat assembleDebug

# Unit tests
./gradlew.bat testDebugUnitTest

# Lint
./gradlew.bat lintDebug

# Release APKs (require signing — see below)
./gradlew.bat assembleGithubRelease assemblePlayStoreRelease

# Instrumented tests (requires device/emulator)
./gradlew.bat connectedDebugAndroidTest
```

## Release signing

Release builds resolve signing credentials in order:

1. Gradle properties (`SCANLY_RELEASE_*`)
2. Environment variables (`SCANLY_RELEASE_*`)
3. `local.properties` entries

| Property | Purpose |
| --- | --- |
| `SCANLY_RELEASE_STORE_FILE` | Keystore path (relative to repo root) |
| `SCANLY_RELEASE_STORE_PASSWORD` | Keystore password |
| `SCANLY_RELEASE_KEY_ALIAS` | Key alias |
| `SCANLY_RELEASE_KEY_PASSWORD` | Key password |

All four must be non-blank for signed release builds. **Never commit** keystores, passwords, or `local.properties`.

## Build types

| Type | Minify | Signing | Purpose |
| --- | --- | --- | --- |
| `debug` | No | Debug | Local development |
| `githubRelease` | Yes (R8 + shrink resources) | Release (if configured or supplied by wizard) | GitHub production APK |
| `playStoreRelease` | Yes (R8 + shrink resources) | Release (if configured or supplied by wizard) | Google Play production AAB |

The generic `release` variant is disabled so Android Studio exposes only the two named production channels.

## Distribution build types

| Build type | Update behavior | Release artifact |
| --- | --- | --- |
| `githubRelease` | Compares the installed `versionName` with the latest GitHub release tag and opens that release page | APK attached to GitHub Releases |
| `playStoreRelease` | Uses Google Play In-App Updates; GitHub can enrich release notes | AAB uploaded to Google Play |

Both build types keep the same application ID, version name, version code, and signing key. In Android Studio, **Build → Generate Signed Bundle / APK** shows `githubRelease` and `playStoreRelease` directly. Select `githubRelease` for the GitHub APK and `playStoreRelease` for the Play Store bundle.

## SDK and NDK

From `app/build.gradle.kts`:

- `minSdk = 29`, `targetSdk = 36`, `compileSdk = 36`
- NDK ABI filters: `arm64-v8a`, `armeabi-v7a`
- `noCompress += "tflite"` for ML model asset

## Source location

All Kotlin sources:

```
app/src/main/java/in/c1ph3rj/scanly/
```

Package declaration:

```kotlin
package `in`.c1ph3rj.scanly
```

## Related docs

- [conventions.md](conventions.md) — code conventions and adding features
- [testing.md](testing.md) — running and writing tests
- [releasing.md](releasing.md) — release checklist
