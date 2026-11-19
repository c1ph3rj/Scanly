# Baseline profile and navigation benchmarks

This module holds device tests that drive the installed app from outside:

- `BaselineProfileGenerator` records the classes and methods used by start-up and tab navigation. Shipped inside the app, that list lets Android compile them ahead of time at install, so the first visit to a screen is not interpreted.
- `NavigationBenchmark` measures cold start and tab-navigation frame timing, with and without the profile.

Both run against `:app`'s `benchmark` build type: the GitHub release build with R8 optimisation, readable names, debug signing and `x86_64` libraries. It is never distributed.

## What you need

A connected device or emulator: Android 13+ on any device, or a rooted device/emulator image (`google_apis`, not `google_play`) on Android 10–12. Benchmark numbers are only meaningful on a physical phone.

## Generate the profile

From the `Scanly/` folder:

```powershell
./gradlew.bat :baselineprofile:connectedBenchmarkAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=in.c1ph3rj.scanly.baselineprofile.BaselineProfileGenerator"
```

```powershell
./gradlew.bat :baselineprofile:copyBaselineProfile
```

The second command writes `app/src/main/baselineProfiles/baseline-prof.txt`. Commit that file; every later build of the app includes it. Regenerate it before a release when start-up or navigation code has changed noticeably.

## Run the benchmarks

```powershell
./gradlew.bat :baselineprofile:connectedBenchmarkAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=in.c1ph3rj.scanly.baselineprofile.NavigationBenchmark"
```

Results (JSON and Perfetto traces) land in `baselineprofile/build/outputs/connected_android_test_additional_output/`. Compare `frameDurationCpuMs` and `frameOverrunMs` percentiles between runs, and `timeToInitialDisplayMs` for start-up.

On an emulator the benchmark tests refuse to run unless you add `"-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR"`; use that only to check the tests work, not for numbers.

## How the tests find things

The tests locate screens and tabs by the Compose test tags in `ScanlyTestTags` (`home_screen`, `scanly_nav_library`, ...). `MainActivity` exposes those tags as resource ids. If a tag is renamed, update `ScanlyJourneys.kt`.

The journey runs on an empty library: every tab, Library search and Back. Opening a document, the page preview and the editor are not covered yet, because the tests cannot create a document.
