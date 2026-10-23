# Slim OpenCV

Scanly ships a self-built OpenCV 5.0.0 that contains only the modules the app uses, instead of
the full `org.opencv:opencv:5.0.0.1` Maven AAR. Each APK gets about 32 MB smaller (both ABIs),
and the app's results and speed stay the same.

| | Official 5.0.0.1 | Slim 5.0.0 |
| --- | --- | --- |
| `libopencv_java5.so` arm64-v8a | 33.4 MB | 14.3 MB |
| `libopencv_java5.so` armeabi-v7a | 22.3 MB | 9.3 MB |
| GitHub release APK (both ABIs) | 146.2 MB | 113.9 MB |

## What is in it

- **Modules:** `core`, `imgproc`, `geometry` (`imgproc` needs it, and Indra calls
  `Geometry.getRotationMatrix2D`), `flann` (`geometry` needs it), `java`, `imgcodecs` and
  `videoio`. OpenCV's Android Java classes always include `org.opencv.android.Utils` (imports
  `Imgcodecs`) and `NativeCameraView` (imports `videoio`), so they do not compile without those two.
- **Codecs:** every image codec in `imgcodecs` is off. The app never encodes or decodes images
  through OpenCV. `videoio` on Android is only the system MediaNDK backend.
- **Same as the official build:** source tag, NDK 27.2.12479018, CMake 3.22.1, compiler flags
  and `-O3`, CPU baseline and dispatch (arm64: NEON FP16 baseline, NEON_DOTPROD / NEON_FP16 /
  NEON_BF16 dispatch), KleidiCV 26.03 and carotene acceleration, TBB 2022.1, ITT and 16 KB page
  alignment. Compare `third_party/opencv/build-info-<abi>.txt` (OpenCV's `getBuildInformation()`
  for the slim library) with the official library's build information.
- **Left out:** `dnn`, `features`, `calib`, `objdetect`, `photo`, `stitching`, `video`, `ptcloud`,
  `stereo` and `highgui`. The app uses none of them; the Java classes of those modules are absent.

## Building it

`app/build.gradle.kts` uses `third_party/opencv/opencv-5.0.0-slim.aar` when it exists and falls
back to the Maven artifact otherwise, which has the same API. Release builds stop with an error
without the slim AAR, so a release never ships the full library by accident.

Requirements: Android SDK with `ndk/27.2.12479018` and `cmake/3.22.1`, JDK 17 in `JAVA_HOME`
(OpenCV's Gradle projects use Kotlin 1.8.20, which rejects newer JVM targets), Python 3.8+ and
internet access (OpenCV source, KleidiCV, TBB, Gradle). From the repo root:

```powershell
$env:JAVA_HOME = "<path to JDK 17>"
python scripts/opencv/build_slim_opencv.py --sdk "$env:LOCALAPPDATA/Android/Sdk" --work C:/ocvb
```

The script runs OpenCV's own `platforms/android/build_sdk.py` and `build_java_shared_aar.py`
(the scripts behind the official AAR) with `scripts/opencv/slim.config.py`. A full build takes
about 40 minutes. It works on Windows, Linux and macOS; on Windows it starts Gradle through
`gradlew.bat`, which is the only change to how OpenCV's scripts run.

## Checking a new build

`OpenCvParityHarnessTest` (androidTest) runs every OpenCV code path in the app on fixed generated
pages: all filter presets at three intensities, the adjustment sliders, signature extraction,
the image analysis and Indra preprocessing. It logs a SHA-256 of every output and median timings
to logcat (tag `OcvParity`). Run it once with each library on the same phone and diff the `HASH`
lines; see the test's KDoc for the commands. It only runs when the `opencvParity` instrumentation
argument is `true`.

Results for the first slim build, on a Galaxy S20 FE (Android 13):

- All 344 output hashes were identical between the official and the slim library in every run
  (the official library itself is deterministic run to run).
- Total time of the 18 timed operations, alternating official and slim APKs: −0.1%, −0.6% and
  −0.4% for the slim library in the three pairs the phone did not start throttling in.

## Upgrading OpenCV

Change `OPENCV_VERSION` and `NDK_VERSION` in `scripts/opencv/build_slim_opencv.py` to match the
new official release's build information, rebuild, compare `build-info-<abi>.txt` with the
official library, and run the parity harness against the official AAR before switching.
