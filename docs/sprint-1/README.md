# Sprint 1

Sprint 1 turns the Sprint 0 runtime spike into a real application shell.

Implemented in this sprint:

- clean package boundaries for `core`, `data`, `domain`, `feature`, `di`, and `navigation`
- Hilt-based dependency injection for app-wide services and ViewModels
- a navigation shell with stable routes for:
  - home
  - library
  - camera
  - review
  - editor
  - readiness
- a repository and use-case wrapper around the Sprint 0 diagnostics flow
- a home screen that acts as the launch pad for the delivery roadmap
- placeholder destinations for future feature slices so route contracts stop moving every sprint

Build notes:

- this project currently uses Android's built-in Kotlin support
- Hilt code generation is wired through KSP
- `gradle.properties` now includes `android.disallowKotlinSourceSets=false` because KSP-generated sources are still blocked by the stricter default in the current AGP toolchain

Verification completed:

- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug`

Known follow-up:

- revisit the experimental `android.disallowKotlinSourceSets=false` flag when the built-in Kotlin + KSP path becomes cleaner in the Android/Gradle toolchain
- Sprint 2 can now focus on the real domain work: Room entities, repositories, and document library persistence
