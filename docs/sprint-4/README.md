# Sprint 4

Sprint 4 layers live document detection and conservative auto-capture on top of the manual CameraX baseline from Sprint 3.

Implemented in this sprint:

- a production `DocumentCornerDetector` backed by the validated LiteRT model asset
- shared pose preprocessing and keypoint decoding between runtime validation and live inference
- CameraX `ImageAnalysis` integration using RGBA preview frames
- a blue live polygon overlay mapped onto the preview surface
- a conservative capture stability tracker with:
  - confidence gating
  - quad validity checks
  - area/aspect sanity checks
  - frame-to-frame jitter checks
  - stable-duration countdown
  - capture cooldown and scene-change re-arm
- an auto-capture toggle that can be disabled instantly
- scan-session UI updates for live status, countdown, and detection timing
- duplicate-capture protection layered on top of the existing manual flow

Build verification completed:

- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug`

Testing guidance for this sprint:

- verify manual capture still works exactly like Sprint 3 when auto-capture is off
- verify the blue overlay appears when a full document is visible
- verify the countdown only starts when the document is stable
- verify auto-capture stops re-firing on the same page until the document moves away or changes
- verify the shutter button still works even when live detection is unstable or unavailable

Notes:

- this sprint intentionally keeps processing non-destructive and manual-first; the final scan-quality crop/enhancement pipeline belongs to Sprint 5
- preview analysis is throttled implicitly by a single in-flight inference gate plus CameraX `STRATEGY_KEEP_ONLY_LATEST`
- the overlay and auto-capture both rely on the same decoder path that was validated in Sprint 0
