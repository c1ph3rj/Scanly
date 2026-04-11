# Benchmark Results

Archived note: this file records the original Sprint 0 runtime probe and should be treated as historical context.

## Current conclusion

- the exported model loaded successfully on Android with LiteRT during the Sprint 0 investigation
- the deployed input size was `320 x 320`
- the deployed output tensor was `FLOAT32 [1, 17, 2100]`
- the TensorFlow/LiteRT export used normalized decoded keypoints
- after fixing the Android-side remap logic, validation quality became reasonable enough to continue

## Device results

### Samsung SM-G781B

- Android version: Android 13 (API 33)
- LiteRT runtime: `2.20.0-dev0+selfbuilt`
- schema version: `3`
- input tensor: `FLOAT32 [1, 320, 320, 3]`
- output tensor: `FLOAT32 [1, 17, 2100]`
- smoke inference: `64.2 ms`
- validation samples evaluated: `25`
- detections found: `23`
- average confidence: `0.784`
- mean corner error: `0.0386` normalized
- mean corner error: `24.73 px`
- best sample: `6kAgwu_1_png_jpg.rf.b9ba04f9480279b3e324be2bd0617515.jpg` (`8.82 px`)
- worst sample: `6vBDqx_4_png_jpg.rf.35ab3ac00379a76c919f4d7c49acb7ff.jpg` (`83.63 px`)

Assessment:

- acceptable for Sprint 0
- strong enough to move into Sprint 1 with manual fallback and editor-first correction paths
- still not good enough to trust auto-capture without more work in later sprints

### Motorola Edge 40

- Android version: Android 15 (API 35)
- LiteRT runtime: `2.20.0-dev0+selfbuilt`
- schema version: `3`
- input tensor: `FLOAT32 [1, 320, 320, 3]`
- output tensor: `FLOAT32 [1, 17, 2100]`
- smoke inference: `64.2 ms`

Assessment:

- runtime compatibility confirmed
- validation summary should be re-run using the fixed decoder build before treating this device as fully signed off

## Sprint 0 status

Sprint 0 goals now considered achieved:

- mobile runtime path chosen and integrated
- model artifact loads on physical Android devices
- input/output tensor contract discovered and documented
- validation dataset wired into the app
- first on-device corner accuracy signal measured

Remaining follow-up, but no longer a Sprint 0 blocker:

- re-run validation summary on the second phone using the fixed decoder build
- improve worst-case sample behavior in later scanner and editor sprints
