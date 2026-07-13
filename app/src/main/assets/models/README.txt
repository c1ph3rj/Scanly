Document-corner model assets:

- document_corners_lite.tflite: Lite — 224 px corner-regression model
- document_corners_standard.tflite: Standard — 288 px corner-regression model
- document_corners_accurate.tflite: High — 384 px corner-regression model
- document_corners_float16.tflite: Accurate — YOLO-pose model (formerly Legacy)
- scanly_document_gate_float16.tflite: 160 px physical-document/screen/neither classifier

The three corner-regression models (Lite / Standard / High) use RGB [-1, 1]
input, RGB 114 letterboxing, TL/TR/BR/BL corner output, and a separate
presence score.

Accurate uses YOLO-pose output and remains available as the highest-accuracy
manual option and compatibility fallback.

The document gate uses RGB [-1, 1] input with RGB 114 letterboxing and
outputs physical_document, digital_screen, neither probabilities. Live
preview uses a 0.90 physical-document threshold plus two consecutive frames;
post-processing uses 0.95.
