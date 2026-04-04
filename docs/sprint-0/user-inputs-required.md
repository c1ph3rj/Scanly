# Your Inputs Required

These are the remaining items that only you can provide for Sprint 0.

## 1. Exported model artifact

Provide:

- `document_corners_float16.tflite`

Place it here:

- `app/src/main/assets/models/document_corners_float16.tflite`

## 2. Validation images

Provide:

- 20 to 30 real photos from the Android phones you care about

Try to include:

- white paper on dark surface
- receipt on textured surface
- low light
- strong shadow
- skewed angle
- partial document in frame

Place them here:

- `app/src/main/assets/validation/images/`

## 3. Labeled expected corners

Provide:

- at least 10 labeled images with normalized `TL/TR/BR/BL` coordinates

Place them here:

- `app/src/main/assets/validation/expected/`

File naming:

- `receipt_01.corners.json`

## 4. Product and legal confirmation

Confirmed direction:

- target a commercial-grade app
- keep an open-source release path in mind from the beginning

This means build quality, testing, privacy, and reliability should be held to production expectations.

It also means model licensing must be handled carefully because Ultralytics documents describe AGPL-3.0 and Enterprise licensing options for YOLO use.

## 5. Device targets

Please tell me:

- one mid-range Android device you want as the minimum acceptable benchmark target
- one high-end device if you have one

That lets us turn the performance budget into a pass/fail gate.
