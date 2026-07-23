package `in`.c1ph3rj.scanly.data.export

import kotlin.math.min

/**
 * Lays out front + back ID scans stacked on a PDF content box, maximizing size
 * while preserving each image's aspect ratio and keeping equal card widths.
 */
internal object IdCardPairPdfLayout {
    data class Placement(
        val frontLeft: Float,
        val frontTop: Float,
        val frontWidth: Float,
        val frontHeight: Float,
        val backLeft: Float,
        val backTop: Float,
        val backWidth: Float,
        val backHeight: Float,
    )

    fun place(
        contentWidth: Float,
        contentHeight: Float,
        contentLeft: Float,
        contentTop: Float,
        frontWidth: Float,
        frontHeight: Float,
        backWidth: Float,
        backHeight: Float,
        gap: Float = DefaultGapPoints,
    ): Placement {
        require(contentWidth > 0f && contentHeight > 0f) { "Content box must be positive." }
        require(frontWidth > 0f && frontHeight > 0f) { "Front image must be positive." }
        require(backWidth > 0f && backHeight > 0f) { "Back image must be positive." }
        require(gap >= 0f) { "Gap must be non-negative." }

        val frontAspect = frontWidth / frontHeight
        val backAspect = backWidth / backHeight
        // Shared width so both cards align; height follows each image's aspect.
        // Choose the largest width that still fits stacked with [gap] between them.
        val heightPerWidth = (1f / frontAspect) + (1f / backAspect)
        val maxWidthByHeight = if (heightPerWidth > 0f) {
            (contentHeight - gap).coerceAtLeast(1f) / heightPerWidth
        } else {
            contentWidth
        }
        val cardWidth = min(contentWidth, maxWidthByHeight).coerceAtLeast(1f)
        val frontCardHeight = (cardWidth / frontAspect).coerceAtLeast(1f)
        val backCardHeight = (cardWidth / backAspect).coerceAtLeast(1f)
        val blockHeight = frontCardHeight + gap + backCardHeight
        val blockLeft = contentLeft + (contentWidth - cardWidth) / 2f
        val blockTop = contentTop + (contentHeight - blockHeight).coerceAtLeast(0f) / 2f

        return Placement(
            frontLeft = blockLeft,
            frontTop = blockTop,
            frontWidth = cardWidth,
            frontHeight = frontCardHeight,
            backLeft = blockLeft,
            backTop = blockTop + frontCardHeight + gap,
            backWidth = cardWidth,
            backHeight = backCardHeight,
        )
    }

    const val DefaultGapPoints = 16f
}
