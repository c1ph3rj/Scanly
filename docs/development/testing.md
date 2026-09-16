# Testing

Test coverage and how to run tests in Scanly **v1.0.16**.

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

**50 test classes** covering core logic, plus in-memory repository fakes under `testing/`:

| Area | Test files |
| --- | --- |
| ML / geometry | `DocumentCornerQuadTest`, `DocumentQuadPolicyTest`, `DocumentGatePolicyTest`, `CornerRegressionDecoderTest`, `BookPageQuadAnalyzerTest`, `AutomaticDocumentModelSelectionPolicyTest`, `PerspectiveQuadMathTest`, `CropQuadEditorTest` |
| Processing | `AdaptivePageFilterTuningTest`, `PageFilterStrengthControllerTest`, `PageFilterPresetTest`, `PageFilterAdjustmentsTest` |
| Camera | `CaptureStabilityTrackerTest`, `DocumentGateStabilityTrackerTest`, `StableCornerSelectorTest`, `CaptureFrameQualityAnalyzerTest`, `CameraOverlayMapperTest`, `CameraPermissionSupportTest`, `ScanSessionScreenTest` |
| UI / layout | `AdaptiveLayoutTest`, `PreviewImageSizerTest`, `ZoomableImageStateTest`, `OnboardingLayoutModeTest` |
| Formatting | `StorageFormatterTest`, `DocumentPresentationFormatterTest`, `DocumentPreviewPathResolverTest` |
| Domain models | `DocumentCornerModelTest`, `ExportDestinationTest`, `PageFilterPresetTest`, `PdfExportOptionsTest` |
| Feature logic | `LibraryUiStateTest`, `DocumentDetailSelectionResolverTest`, `PageImagePreviewSelectionResolverTest`, `SuggestDocumentTitleUseCaseTest`, `ScanlyLaunchActionTest`, `LibraryCardPresentationTest`, `DocumentPageTilePresentationTest` |
| Flow / use case | `DocumentLibraryFlowTest`, `ScanSessionAndPageEditFlowTest`, `ExportArchiveAndClearDataFlowTest`, `ToolsSettingsAndLaunchActionFlowTest` |
| Updates | `AppUpdateDialogCooldownTest`, `ReleaseMarkdownParserTest`, `AppVersionComparatorTest`, `GitHubAppUpdateRepositoryTest`, `PlayInAppUpdatePolicyTest` |
| Backup/export | `LibraryArchivePolicyTest`, `PdfPageLayoutResolverTest` |
| Tools presentation | `PdfReaderPresentationTest`, `PdfToolPresentationTest`, `QrToolPresentationTest` |

### Placement convention

Mirror the source package:

```
app/src/test/java/in/c1ph3rj/scanly/{matching/package}/YourTest.kt
```

## Instrumented tests (`app/src/androidTest/`)

| File | Coverage |
| --- | --- |
| `ScanlyDeviceUiTest.kt` | Real `MainActivity`: onboarding → Home, Library / Tools / Settings chrome, QR generate preview |
| `ScanlyDeviceUiRobot.kt` | Shared Compose/UiAutomator helpers for the device UI suite |
| `OnboardingScreenTest.kt` | Compose UI test for onboarding screen |
| `OpenCvPageFilterProcessorTest.kt` | Device-side OpenCV filter engine checks (flatten, color marks, preview/save parity) |
| `ExampleInstrumentedTest.kt` | Package name smoke test |

Device UI tests use Android Test Orchestrator and clear app data between cases. Full run/install instructions: [running-and-testing.md](running-and-testing.md).

## JVM flow tests (`domain/usecase/`)

These call shipped use cases with in-memory fakes (no device):

| File | Coverage |
| --- | --- |
| `DocumentLibraryFlowTest` | Document/group lifecycle, library visibility, page reorder/delete |
| `ScanSessionAndPageEditFlowTest` | Scan add vs retake routes, import cap, crop/filter/adjust |
| `ExportArchiveAndClearDataFlowTest` | PDF/ZIP export, backup restore Replace vs Merge, clear-all-data |
| `ToolsSettingsAndLaunchActionFlowTest` | QR, PDF toolkit, prefs/onboarding, launch-action redirects |

## Coverage gaps

Still not covered on-device or with real I/O (prioritized):

1. **Persistence integration** — Room migrations, repository round-trips
2. **Export end-to-end** — PDF/ZIP bytes from the real exporter and save-to-destination
3. **Archive end-to-end** — `.scanly` zip round-trip (JVM tests cover Replace vs Merge via in-memory fakes)
4. **Capture instrumented** — Camera session on device (gate + multi-model path)
5. **Settings DataStore** — preference round-trips against real DataStore (JVM tests cover use-case persistence against an in-memory settings store)

## What to test when contributing

| Change type | Minimum verification |
| --- | --- |
| Geometry / processing math | Unit test |
| ML policy (gate, quad, book, auto-select) | Unit test for pure policy helpers |
| ViewModel state logic | Unit test for resolvers/state |
| New use case | Unit test if non-trivial |
| UI layout | Screenshot or instrumented test for critical flows |
| Shell navigation / onboarding | `ScanlyDeviceUiTest` on a connected device |
| Room migration | Manual test on device with old schema data |
| User-facing feature | Update docs + manual device verification |

## Related docs

- [running-and-testing.md](running-and-testing.md) — install the app, emulator/adb, JVM and device UI suites
- [setup.md](setup.md) — build commands
- [conventions.md](conventions.md) — code placement rules
- [../../CONTRIBUTING.md](../../CONTRIBUTING.md) — contribution expectations
