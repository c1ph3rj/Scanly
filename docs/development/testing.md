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

**26 test files** covering core logic:

| Area | Test files |
| --- | --- |
| ML / geometry | `DocumentCornerQuadTest`, `PerspectiveQuadMathTest`, `CropQuadEditorTest` |
| Processing | `AdaptivePageFilterTuningTest`, `PageFilterPresetTest` |
| Camera | `CaptureStabilityTrackerTest`, `CaptureFrameQualityAnalyzerTest`, `CameraOverlayMapperTest`, `ScanSessionScreenTest` |
| UI / layout | `AdaptiveLayoutTest`, `PreviewImageSizerTest`, `OnboardingLayoutModeTest`, `PageTextOverlayTest` |
| Formatting | `StorageFormatterTest`, `DocumentPresentationFormatterTest`, `DocumentPreviewPathResolverTest`, `RecognizedPageTextTest` |
| Feature logic | `LibraryUiStateTest`, `DocumentDetailSelectionResolverTest`, `PageImagePreviewSelectionResolverTest`, `PageImagePreviewViewModelTest` |
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
| `PageImagePreviewScreenTest.kt` | OCR Text-mode selection and copy actions |
| `ExampleInstrumentedTest.kt` | Package name smoke test |

## Coverage gaps

Areas that need more tests (prioritized):

1. **Persistence integration** — Room migrations, repository round-trips
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
| Room migration | Manual test on device with old schema data |
| User-facing feature | Update docs + manual device verification |

## Related docs

- [setup.md](setup.md) — build commands
- [conventions.md](conventions.md) — code placement rules
- [../../CONTRIBUTING.md](../../CONTRIBUTING.md) — contribution expectations
