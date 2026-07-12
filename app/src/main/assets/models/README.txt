Document-corner model assets:

- document_corners_float16.tflite: legacy YOLO-pose model
- document_corners_lite.tflite: 224 px corner-regression model
- document_corners_standard.tflite: 288 px corner-regression model
- document_corners_accurate.tflite: 384 px corner-regression model

The three corner-regression models use RGB [-1, 1] input, RGB 114
letterboxing, TL/TR/BR/BL corner output, and a separate presence score.
