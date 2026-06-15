package `in`.c1ph3rj.scanly.core.ui

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Maps on-screen preview slots to decode sizes so bitmaps are never upscaled enough to look soft.
 */
enum class PreviewDisplaySize {
    /** Scan-session strip items and compact rails (~72dp). */
    COMPACT,

    /** Home, library, and folder cards (~120–160dp). */
    CARD,

    /** Full-width selected page preview in document detail. */
    DETAIL,
}

object PreviewImageSizer {
    private const val OVERSAMPLE = 1.35f
    private const val MIN_PX = 192
    private const val MAX_CARD_PX = 768
    private const val MAX_DETAIL_PX = 1_536

    fun estimateTargetPx(size: PreviewDisplaySize, density: Float): Int {
        val dp = when (size) {
            PreviewDisplaySize.COMPACT -> 72f
            PreviewDisplaySize.CARD -> 144f
            PreviewDisplaySize.DETAIL -> 320f
        }
        val maxPx = maxPxFor(size)
        return bucketTargetPx(
            requestedPx = (dp * density * OVERSAMPLE).roundToInt(),
            maxPx = maxPx,
        )
    }

    fun targetPxForContainer(
        widthPx: Int,
        heightPx: Int,
        size: PreviewDisplaySize,
        density: Float,
    ): Int {
        if (widthPx <= 0 || heightPx <= 0) {
            return estimateTargetPx(size, density)
        }
        val maxPx = maxPxFor(size)
        return bucketTargetPx(
            requestedPx = (max(widthPx, heightPx) * OVERSAMPLE).roundToInt(),
            maxPx = maxPx,
        )
    }

    fun useHighColorDepth(targetPx: Int): Boolean = targetPx >= 384

    private fun maxPxFor(size: PreviewDisplaySize): Int = when (size) {
        PreviewDisplaySize.DETAIL -> MAX_DETAIL_PX
        else -> MAX_CARD_PX
    }

    private fun bucketTargetPx(requestedPx: Int, maxPx: Int): Int {
        val clamped = requestedPx.coerceIn(MIN_PX, maxPx)
        return DecodeBuckets.firstOrNull { bucket -> bucket >= clamped && bucket <= maxPx } ?: maxPx
    }

    private val DecodeBuckets = intArrayOf(192, 256, 384, 512, 768, 1_024, 1_536)
}
