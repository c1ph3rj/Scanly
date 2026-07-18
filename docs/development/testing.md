# Testing

Test coverage and how to run tests in Scanly **v1.0.12.1**.

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

**41 test files** covering core logic:

| Area | Test files |
| --- | --- |
| ML / geometry | `DocumentCornerQuadTest`, `DocumentQuadPolicyTest`, `DocumentGatePolicyTest`, `CornerRegressionDecoderTest`, `BookPageQuadAnalyzerTest`, `AutomaticDocumentModelSelectionPolicyTest`, `PerspectiveQuadMathTest`, `CropQuadEditorTest` |
| Processing | `AdaptivePageFilterTuningTest`, `PageFilterPresetTest`, `PageFilterAdjustmentsTest` |
| Camera | `CaptureStabilityTrackerTest`, `DocumentGateStabilityTrackerTest`, `StableCornerSelectorTest`, `CaptureFrameQualityAnalyzerTest`, `CameraOverlayMapperTest`, `CameraPermissionSupportTest`, `ScanSessionScreenTest` |
| UI / layout | `AdaptiveLayoutTest`, `PreviewImageSizerTest`, `ZoomableImageStateTest`, `OnboardingLayoutModeTest` |
| Formatting | `StorageFormatterTest`, `DocumentPresentationFormatterTest`, `DocumentPreviewPathResolverTest` |
| Domain models | `DocumentCornerModelTest`, `ExportDestinationTest`, `PageFilterPresetTest`, `PdfExportOptionsTest` |
| Feature logic | `LibraryUiStateTest`, `DocumentDetailSelectionResolverTest`, `PageImagePreviewSelectionResolverTest`, `SuggestDocumentTitleUseCaseTest`, `ScanlyLaunchActionTest` |
| Updates | `AppUpdateDialogCooldownTest`, `ReleaseMarkdownParserTest`, `AppVersionComparatorTest`, `GitHubAppUpdateRepositoryTest`, `PlayInAppUpdatePolicyTest` |
| Backup/export | `LibraryArchivePolicyTest`, `PdfPageLayoutResolverTest` |
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
| `OpenCvPageFilterProcessorTest.kt` | Device-side OpenCV filter processor checks |
| `ExampleInstrumentedTest.kt` | Package name smoke test |

## Coverage gaps

Areas that need more tests (prioritized):

1. **Persistence integration** — Room migrations, repository round-trips
2. **Export end-to-end** — PDF/ZIP generation with real page data; save-to-destination flow
3. **Archive end-to-end** — Backup/restore round-trip with `.scanly` validation
4. **Capture instrumented** — Camera session flow on device (gate + multi-model path)
5. **Group export** — Merged PDF and zipped PDF set
6. **Clear-all-data** — Full wipe verification
7. **Settings DataStore** — model/gate/theme preference round-trips

## What to test when contributing

| Change type | Minimum verification |
| --- | --- |
| Geometry / processing math | Unit test |
| ML policy (gate, quad, book, auto-select) | Unit test for pure policy helpers |
| ViewModel state logic | Unit test for resolvers/state |
| New use case | Unit test if non-trivial |
| UI layout | Screenshot or instrumented test for critical flows |
| Room migration | Manual test on device with old schema data |
| User-facing feature | Update docs + manual device verification |

## Related docs

- [setup.md](setup.md) — build commands
- [conventions.md](conventions.md) — code placement rules
- [../../CONTRIBUTING.md](../../CONTRIBUTING.md) — contribution expectations
