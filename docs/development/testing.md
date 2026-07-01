# Testing

Test coverage and how to run tests in Scanly **v1.0.9**.

## Running tests

```powershell
# Unit tests (no device required)
./gradlew.bat testDebugUnitTest

# Instrumented tests (device/emulator required)
./gradlew.bat connectedDebugAndroidTest

# Lint
./gradlew.bat lintDebug
```

## Unit tests (`app/src/test/`)

Unit tests cover core logic plus the shared-library manifest format and path validation.

| Area | Test files |
| --- | --- |
| ML / geometry | `DocumentCornerQuadTest`, `PerspectiveQuadMathTest`, `CropQuadEditorTest` |
| Processing | `AdaptivePageFilterTuningTest`, `PageFilterPresetTest` |
| Camera | `CaptureStabilityTrackerTest`, `CaptureFrameQualityAnalyzerTest`, `CameraOverlayMapperTest`, `ScanSessionScreenTest` |
| UI / layout | `AdaptiveLayoutTest`, `PreviewImageSizerTest`, `OnboardingLayoutModeTest` |
| Formatting / storage | `StorageFormatterTest`, `DocumentPresentationFormatterTest`, `DocumentPreviewPathResolverTest`, `LibraryManifestFormatTest` |
| Feature logic | `LibraryUiStateTest`, `DocumentDetailSelectionResolverTest`, `PageImagePreviewSelectionResolverTest` |
| Updates | `AppUpdateDialogCooldownTest`, `ReleaseMarkdownParserTest`, `AppVersionComparatorTest`, `GitHubAppUpdateRepositoryTest`, `PlayInAppUpdatePolicyTest` |
| Scaffold | `ExampleUnitTest` |

### Placement convention

Mirror the source package:

```
app/src/test/java/in/c1ph3rj/scanly/{matching/package}/YourTest.kt
```

## Instrumented tests (`app/src/androidTest/`)

| File | Coverage |
| --- | --- |
| `OnboardingScreenTest.kt` | Compose UI test for onboarding screen |
| `ExampleInstrumentedTest.kt` | Package name smoke test |

## Coverage gaps

Areas that need more tests (prioritized):

1. **Persistence integration** — fake DocumentsProvider write-through, delta sync, and interrupted-operation recovery
2. **Export end-to-end** — PDF/ZIP generation with real page data
3. **Capture instrumented** — Camera session flow on device
4. **Group export** — Merged PDF and zipped PDF set
5. **Clear-all-data** — Full wipe verification

## What to test when contributing

| Change type | Minimum verification |
| --- | --- |
| Geometry / processing math | Unit test |
| ViewModel state logic | Unit test for resolvers/state |
| New use case | Unit test if non-trivial |
| UI layout | Screenshot or instrumented test for critical flows |
| Shared persistence | Reconnect, database rebuild, permission-loss, and uninstall/reinstall tests |
| User-facing feature | Update docs + manual device verification |

## Related docs

- [setup.md](setup.md) — build commands
- [conventions.md](conventions.md) — code placement rules
- [../../CONTRIBUTING.md](../../CONTRIBUTING.md) — contribution expectations
