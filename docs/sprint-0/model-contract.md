# Model Contract

## Current known facts

- Training task: pose estimation
- Expected semantic output: 4 ordered document corners
- Corner order from your dataset and notebook: `TL -> TR -> BR -> BL`
- Runtime target for Android validation: `.tflite`

## Contract we will enforce in app code

- Exactly 4 corner points are expected per primary detection.
- Corner order is fixed as `TL`, `TR`, `BR`, `BL`.
- Corner values used by the domain layer must be normalized to `[0, 1]`.
- The ordered quad must be clockwise in screen coordinates.
- Invalid quads are rejected before perspective correction.

## Unknowns that Sprint 0 must confirm

- exported model input tensor width and height
- input color format after export
- output tensor shape after LiteRT export
- whether the exported artifact keeps pose keypoints in the expected order
- whether post-processing is needed outside the runtime to extract the first detection

## Validation asset format

Store files here:

- `app/src/main/assets/validation/images/`
- `app/src/main/assets/validation/expected/`

Use one expected file per image with suffix `.corners.json`.

Suggested JSON shape:

```json
{
  "image": "receipt_01.jpg",
  "corners": {
    "TL": [0.121, 0.083],
    "TR": [0.891, 0.097],
    "BR": [0.864, 0.934],
    "BL": [0.138, 0.918]
  }
}
```

## Export notes

The repo now includes `tools/export_yolo_pose_to_tflite.py` as a starting point for producing the mobile artifact. If Windows export is unstable, run the export in WSL, Linux, or Colab and copy the resulting `.tflite` file into the assets location above.
