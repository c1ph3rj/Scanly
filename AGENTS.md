# AGENTS.md

## Project Snapshot
- Single-module Android app (`:app`) using Kotlin + Jetpack Compose + Material 3.
- Entry point is `app/src/main/java/in/c1ph3rj/scanly/MainActivity.kt`; UI is currently Compose-only.
- Package name is `in.c1ph3rj.scanly`; Kotlin sources escape `in` as ``package `in`.c1ph3rj.scanly``.
- No backend/API/data layer exists yet; current flow is Activity -> theme wrapper -> composables.

## Architecture and Code Layout
- Root build config is minimal: `build.gradle.kts` + version catalog in `gradle/libs.versions.toml`.
- Android module config lives in `app/build.gradle.kts` (SDK levels, Compose enablement, dependencies).
- UI theming is centralized in `app/src/main/java/in/c1ph3rj/scanly/ui/theme/` (`Theme.kt`, `Color.kt`, `Type.kt`).
- Android app wiring is in `app/src/main/AndroidManifest.xml` (single launcher activity).

## Build and Test Workflows (Windows/PowerShell)
- Use the wrapper from repo root so Gradle 9.3.1 is used (`gradle/wrapper/gradle-wrapper.properties`).
- Daemon JVM toolchain is pinned to Java 21 via `gradle/gradle-daemon-jvm.properties`.
- Common commands:
  - `./gradlew.bat assembleDebug`
  - `./gradlew.bat testDebugUnitTest`
  - `./gradlew.bat connectedDebugAndroidTest` (requires emulator/device)
  - `./gradlew.bat lintDebug`

## Project-Specific Conventions
- Prefer adding new UI as composables under `in.c1ph3rj.scanly` and wrap screens in `ScanlyTheme`.
- Keep dependency versions in `gradle/libs.versions.toml`; reference via `libs.*` aliases in Gradle scripts.
- Compose deps use BOM (`implementation(platform(libs.androidx.compose.bom))`); keep UI libs BOM-managed.
- `targetSdk`/`compileSdk` are set to API 36 in `app/build.gradle.kts`; maintain compatibility when adding libs.
- Release minification is currently off (`isMinifyEnabled = false`); do not assume ProGuard/R8 rules are active.

## Testing Reality in This Repo
- Unit test scaffold: `app/src/test/java/in/c1ph3rj/scanly/ExampleUnitTest.kt` (JUnit4).
- Instrumented test scaffold: `app/src/androidTest/java/in/c1ph3rj/scanly/ExampleInstrumentedTest.kt`.
- No Compose UI tests or feature tests yet; if adding behavior, place tests in these existing source sets.

## Agent Guardrails
- Keep changes confined to `:app` unless build-system updates are explicitly required.
- Match existing Kotlin style (`kotlin.code.style=official` in `gradle.properties`).
- When adding new modules/dependencies, update `settings.gradle.kts` and `libs.versions.toml` together.

