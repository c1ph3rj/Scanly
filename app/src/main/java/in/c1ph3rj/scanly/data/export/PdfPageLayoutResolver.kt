package `in`.c1ph3rj.scanly.data.export

import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfPageMargin
import `in`.c1ph3rj.scanly.domain.model.PdfPageNumber
import `in`.c1ph3rj.scanly.domain.model.PdfPageOrientation
import `in`.c1ph3rj.scanly.domain.model.PdfPageSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object PdfPageLayoutResolver {
    fun resolve(
        imageWidth: Int,
        imageHeight: Int,
        options: PdfExportOptions,
    ): PdfPageLayout {
        require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive." }

        val resolvedOrientation = when (options.orientation) {
            PdfPageOrientation.AUTO -> if (imageWidth > imageHeight) {
                PdfPageOrientation.LANDSCAPE
            } else {
                PdfPageOrientation.PORTRAIT
            }
            else -> options.orientation
        }
        val portraitDimensions = when (options.pageSize) {
            PdfPageSize.FIT -> fitPageDimensions(imageWidth, imageHeight)
            else -> checkNotNull(options.pageSize.portraitWidthPoints) to
                checkNotNull(options.pageSize.portraitHeightPoints)
        }
        val (pageWidthPoints, pageHeightPoints) = when (resolvedOrientation) {
            PdfPageOrientation.LANDSCAPE -> portraitDimensions.second to portraitDimensions.first
            PdfPageOrientation.AUTO,
            PdfPageOrientation.PORTRAIT,
            -> portraitDimensions
        }
        val marginPoints = when (options.margin) {
            PdfPageMargin.NONE -> 0
            PdfPageMargin.SMALL -> SmallMarginPoints
            PdfPageMargin.LARGE -> LargeMarginPoints
        }
        val footerPoints = if (options.pageNumber == PdfPageNumber.NONE) 0 else PageNumberFooterPoints

        return PdfPageLayout(
            pageWidthPoints = pageWidthPoints,
            pageHeightPoints = pageHeightPoints,
            marginPoints = marginPoints,
            footerPoints = footerPoints,
        )
    }

    private fun fitPageDimensions(imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        val shortSide = min(imageWidth, imageHeight).toFloat()
        val longSide = max(imageWidth, imageHeight).toFloat()
        val fittedShortSide = (FitPageLongSidePoints * (shortSide / longSide))
            .roundToInt()
            .coerceAtLeast(MinimumFitPageShortSidePoints)
        return fittedShortSide to FitPageLongSidePoints
    }

    private const val FitPageLongSidePoints = 842
    private const val MinimumFitPageShortSidePoints = 144
    private const val SmallMarginPoints = 24
    private const val LargeMarginPoints = 48
    private const val PageNumberFooterPoints = 28
}

internal data class PdfPageLayout(
    val pageWidthPoints: Int,
    val pageHeightPoints: Int,
    val marginPoints: Int,
    val footerPoints: Int,
) {
    val contentWidthPoints: Int
        get() = (pageWidthPoints - marginPoints * 2).coerceAtLeast(1)

    val contentHeightPoints: Int
        get() = (pageHeightPoints - marginPoints * 2 - footerPoints).coerceAtLeast(1)
}
