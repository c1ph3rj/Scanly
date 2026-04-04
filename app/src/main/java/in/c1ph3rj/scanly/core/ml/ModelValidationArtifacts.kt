package `in`.c1ph3rj.scanly.core.ml

import java.util.Locale

object ScanlyModelAssets {
    const val modelAssetPath = "models/document_corners_float16.tflite"
    const val validationImagesDirectory = "validation/images"
    const val expectedCornersDirectory = "validation/expected"
    const val expectedCornersExtension = ".corners.json"
    val supportedImageExtensions = setOf("jpg", "jpeg", "png", "bmp", "webp")
}

object SprintZeroRuntimeDecision {
    const val primaryRuntime = "Bundled LiteRT package"
    const val validationPath = "CPU correctness first, then accelerator benchmarking"
    const val fallbackRuntime = "ONNX Runtime Mobile only if exported LiteRT output breaks"
}

enum class ArtifactReadiness {
    READY,
    PARTIAL,
    MISSING,
}

data class SprintZeroReadinessReport(
    val modelPresent: Boolean,
    val modelSizeBytes: Long?,
    val validationImageCount: Int,
    val expectedCornersCount: Int,
    val runtimeSummary: String,
    val userActions: List<String>,
) {
    val readiness: ArtifactReadiness = when {
        modelPresent && validationImageCount > 0 && expectedCornersCount > 0 -> ArtifactReadiness.READY
        modelPresent || validationImageCount > 0 || expectedCornersCount > 0 -> ArtifactReadiness.PARTIAL
        else -> ArtifactReadiness.MISSING
    }

    companion object {
        fun preview(): SprintZeroReadinessReport = SprintZeroReadinessReport(
            modelPresent = false,
            modelSizeBytes = null,
            validationImageCount = 0,
            expectedCornersCount = 0,
            runtimeSummary = "${SprintZeroRuntimeDecision.primaryRuntime} -> ${SprintZeroRuntimeDecision.validationPath}",
            userActions = listOf(
                "Export the trained YOLO pose model to a float16 .tflite artifact.",
                "Capture 20 to 30 real document photos from your target phones.",
                "Label at least 10 of those photos with TL/TR/BR/BL normalized corners.",
            ),
        )
    }
}

data class LiteRtTensorSpec(
    val index: Int,
    val dataType: String,
    val shape: List<Int>,
    val byteCount: Int,
) {
    fun asDisplayLine(): String {
        val renderedShape = shape.joinToString(prefix = "[", postfix = "]")
        return "#$index $dataType $renderedShape ${byteCount}B"
    }
}

data class LiteRtProbeReport(
    val deviceName: String,
    val androidVersion: String,
    val runtimeVersion: String,
    val schemaVersion: String,
    val inputTensors: List<LiteRtTensorSpec>,
    val outputTensors: List<LiteRtTensorSpec>,
    val smokeInferenceMillis: Double,
    val validationReport: LiteRtValidationReport?,
) {
    companion object {
        fun preview(): LiteRtProbeReport = LiteRtProbeReport(
            deviceName = "Pixel 8 Pro",
            androidVersion = "Android 15 (API 35)",
            runtimeVersion = "LiteRT 1.4.1",
            schemaVersion = "3",
            inputTensors = listOf(
                LiteRtTensorSpec(
                    index = 0,
                    dataType = "FLOAT32",
                    shape = listOf(1, 640, 640, 3),
                    byteCount = 4_915_200,
                ),
            ),
            outputTensors = listOf(
                LiteRtTensorSpec(
                    index = 0,
                    dataType = "FLOAT32",
                    shape = listOf(1, 56, 8_400),
                    byteCount = 1_881_600,
                ),
            ),
            smokeInferenceMillis = 44.7,
            validationReport = LiteRtValidationReport(
                samplesEvaluated = 25,
                detectionsFound = 25,
                averageConfidence = 0.94,
                meanCornerErrorNormalized = 0.013,
                meanCornerErrorPixels = 8.3,
                bestSample = ValidationSampleOutcome(
                    imageName = "receipt_01.jpg",
                    confidence = 0.98,
                    meanCornerErrorNormalized = 0.007,
                    meanCornerErrorPixels = 4.5,
                ),
                worstSample = ValidationSampleOutcome(
                    imageName = "receipt_17.jpg",
                    confidence = 0.83,
                    meanCornerErrorNormalized = 0.025,
                    meanCornerErrorPixels = 16.2,
                ),
            ),
        )
    }
}

data class LiteRtValidationReport(
    val samplesEvaluated: Int,
    val detectionsFound: Int,
    val averageConfidence: Double,
    val meanCornerErrorNormalized: Double,
    val meanCornerErrorPixels: Double,
    val bestSample: ValidationSampleOutcome?,
    val worstSample: ValidationSampleOutcome?,
)

data class ValidationSampleOutcome(
    val imageName: String,
    val confidence: Double,
    val meanCornerErrorNormalized: Double,
    val meanCornerErrorPixels: Double,
)

sealed interface LiteRtProbeUiState {
    data object Loading : LiteRtProbeUiState
    data class Success(val report: LiteRtProbeReport) : LiteRtProbeUiState
    data class Failure(val message: String) : LiteRtProbeUiState
}

fun Long.toReadableFileSize(): String {
    if (this < 1024) {
        return "$this B"
    }

    val units = listOf("KB", "MB", "GB")
    var value = this.toDouble()
    var unitIndex = -1
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.2f %s", value, units[unitIndex])
}
