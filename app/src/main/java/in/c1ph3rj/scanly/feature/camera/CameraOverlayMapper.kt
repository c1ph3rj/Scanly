package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint

data class DetectionOverlayFrame(
    val width: Int,
    val height: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropRight: Int,
    val cropBottom: Int,
) {
    val cropWidth: Int = cropRight - cropLeft
    val cropHeight: Int = cropBottom - cropTop
    val isValid: Boolean = width > 0 && height > 0 && cropWidth > 0 && cropHeight > 0
}

internal fun DetectionFrame.toDetectionOverlayFrame(): DetectionOverlayFrame {
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    return when (normalizedRotation) {
        0 -> DetectionOverlayFrame(
            width = width,
            height = height,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom,
        )

        90 -> DetectionOverlayFrame(
            width = height,
            height = width,
            cropLeft = height - cropBottom,
            cropTop = cropLeft,
            cropRight = height - cropTop,
            cropBottom = cropRight,
        )

        180 -> DetectionOverlayFrame(
            width = width,
            height = height,
            cropLeft = width - cropRight,
            cropTop = height - cropBottom,
            cropRight = width - cropLeft,
            cropBottom = height - cropTop,
        )

        270 -> DetectionOverlayFrame(
            width = height,
            height = width,
            cropLeft = cropTop,
            cropTop = width - cropRight,
            cropRight = cropBottom,
            cropBottom = width - cropLeft,
        )

        else -> error("Camera frame rotation must be a multiple of 90 degrees.")
    }
}

internal fun DocumentCornerQuad.mapToPreview(
    sourceFrame: DetectionOverlayFrame,
): List<NormalizedPoint> {
    if (!sourceFrame.isValid) {
        return emptyList()
    }

    return orderedPoints().map { (_, point) ->
        NormalizedPoint(
            x = ((point.x * sourceFrame.width) - sourceFrame.cropLeft) / sourceFrame.cropWidth,
            y = ((point.y * sourceFrame.height) - sourceFrame.cropTop) / sourceFrame.cropHeight,
        )
    }
}
