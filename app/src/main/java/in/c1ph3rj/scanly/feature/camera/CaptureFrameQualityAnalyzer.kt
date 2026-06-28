package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import kotlin.math.abs
import kotlin.math.sqrt

enum class CaptureSceneIssue(val guidance: String) {
    LENS_BLOCKED("Lens may be covered. Clear it and try again."),
    TOO_DARK("Too dark. Add light or turn on the flash."),
    BLURRY("Image looks blurry. Clean the lens or tap to focus."),
}

data class CaptureFrameQuality(
    val meanLuminance: Float,
    val luminanceDeviation: Float,
    val edgeStrength: Float,
) {
    fun sceneIssue(hasDocumentCandidate: Boolean): CaptureSceneIssue? = when {
        meanLuminance < BlockedMeanLuminance && luminanceDeviation < BlockedLuminanceDeviation -> {
            CaptureSceneIssue.LENS_BLOCKED
        }
        meanLuminance < DarkMeanLuminance -> CaptureSceneIssue.TOO_DARK
        hasDocumentCandidate && edgeStrength < BlurryEdgeStrength -> CaptureSceneIssue.BLURRY
        else -> null
    }
}

object CaptureFrameQualityAnalyzer {
    fun analyze(frame: DetectionFrame): CaptureFrameQuality {
        if (frame.width <= 0 || frame.height <= 0 || frame.bytes.size < RgbaPixelStride) {
            return CaptureFrameQuality(
                meanLuminance = 0f,
                luminanceDeviation = 0f,
                edgeStrength = 0f,
            )
        }

        var luminanceSum = 0.0
        var squaredLuminanceSum = 0.0
        var sampleCount = 0
        var edgeSum = 0.0
        var edgeCount = 0

        var y = 0
        while (y < frame.height) {
            var previousLuminance: Float? = null
            var x = 0
            while (x < frame.width) {
                val luminance = frame.luminanceAt(x, y) ?: break
                luminanceSum += luminance
                squaredLuminanceSum += luminance * luminance
                sampleCount += 1

                previousLuminance?.let { previous ->
                    edgeSum += abs(luminance - previous)
                    edgeCount += 1
                }
                if (y >= SampleStep) {
                    frame.luminanceAt(x, y - SampleStep)?.let { above ->
                        edgeSum += abs(luminance - above)
                        edgeCount += 1
                    }
                }

                previousLuminance = luminance
                x += SampleStep
            }
            y += SampleStep
        }

        if (sampleCount == 0) {
            return CaptureFrameQuality(0f, 0f, 0f)
        }

        val mean = luminanceSum / sampleCount
        val variance = ((squaredLuminanceSum / sampleCount) - (mean * mean)).coerceAtLeast(0.0)
        return CaptureFrameQuality(
            meanLuminance = mean.toFloat(),
            luminanceDeviation = sqrt(variance).toFloat(),
            edgeStrength = if (edgeCount == 0) 0f else (edgeSum / edgeCount).toFloat(),
        )
    }

    private fun DetectionFrame.luminanceAt(x: Int, y: Int): Float? {
        val index = ((y * width) + x) * RgbaPixelStride
        if (index < 0 || index + 2 >= bytes.size) {
            return null
        }
        val red = bytes[index].toInt() and 0xFF
        val green = bytes[index + 1].toInt() and 0xFF
        val blue = bytes[index + 2].toInt() and 0xFF
        return (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)
    }
}

private const val RgbaPixelStride = 4
private const val SampleStep = 8
private const val BlockedMeanLuminance = 20f
private const val BlockedLuminanceDeviation = 12f
private const val DarkMeanLuminance = 48f
private const val BlurryEdgeStrength = 5f
