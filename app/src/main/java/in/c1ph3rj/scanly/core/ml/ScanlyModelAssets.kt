package `in`.c1ph3rj.scanly.core.ml

import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel

internal data class ScanlyModelContract(
    val assetPath: String,
    val presenceThreshold: Float,
    val outputKind: OutputKind,
) {
    enum class OutputKind { LEGACY_POSE, CORNERS_AND_PRESENCE }
}

internal object ScanlyModelAssets {
    fun contract(model: DocumentCornerModel): ScanlyModelContract = when (model) {
        DocumentCornerModel.LEGACY -> ScanlyModelContract(
            assetPath = "models/document_corners_float16.tflite",
            presenceThreshold = LiteRtPoseConstants.DETECTION_CONFIDENCE_THRESHOLD,
            outputKind = ScanlyModelContract.OutputKind.LEGACY_POSE,
        )
        DocumentCornerModel.LITE -> ScanlyModelContract(
            assetPath = "models/document_corners_lite.tflite",
            presenceThreshold = 0.875f,
            outputKind = ScanlyModelContract.OutputKind.CORNERS_AND_PRESENCE,
        )
        DocumentCornerModel.STANDARD -> ScanlyModelContract(
            assetPath = "models/document_corners_standard.tflite",
            presenceThreshold = 0.95f,
            outputKind = ScanlyModelContract.OutputKind.CORNERS_AND_PRESENCE,
        )
        DocumentCornerModel.ACCURATE -> ScanlyModelContract(
            assetPath = "models/document_corners_accurate.tflite",
            presenceThreshold = 0.725f,
            outputKind = ScanlyModelContract.OutputKind.CORNERS_AND_PRESENCE,
        )
    }
}
