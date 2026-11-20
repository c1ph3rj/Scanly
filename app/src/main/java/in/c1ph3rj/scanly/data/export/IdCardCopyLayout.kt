package `in`.c1ph3rj.scanly.data.export

import `in`.c1ph3rj.scanly.domain.model.IdCardExportLayout
import `in`.c1ph3rj.scanly.domain.model.IdCardSide
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfPageOrientation
import `in`.c1ph3rj.scanly.domain.model.PdfPageSize
import kotlin.math.min

/** One page image to export, with what the PDF layout needs to know about ID card sides. */
internal data class ExportPageSource(
    val imagePath: String,
    val idCardPairId: String? = null,
    val idCardSide: IdCardSide? = null,
)

/** One PDF page: a single scan, or the sides of one ID card laid out like a photocopy. */
internal sealed interface PdfSheet {
    data class Single(val imagePath: String) : PdfSheet

    /** Front first, then back; a card saved with its front only has one image. */
    data class IdCardCopy(val imagePaths: List<String>) : PdfSheet
}

internal object PdfSheetPlanner {
    /**
     * Pages in document order become sheets. With [IdCardExportLayout.ID_COPY], the sides of one
     * card (same pair id) share a sheet placed where the card's first side was, front before back;
     * everything else, and every page with [IdCardExportLayout.SEPARATE_PAGES], is its own sheet.
     */
    fun plan(pages: List<ExportPageSource>, layout: IdCardExportLayout): List<PdfSheet> {
        if (layout == IdCardExportLayout.SEPARATE_PAGES) return pages.map { PdfSheet.Single(it.imagePath) }
        val byPair = pages.filter { it.idCardPairId != null }.groupBy { checkNotNull(it.idCardPairId) }
        val placedPairs = mutableSetOf<String>()
        return pages.mapNotNull { page ->
            val pairId = page.idCardPairId ?: return@mapNotNull PdfSheet.Single(page.imagePath)
            if (!placedPairs.add(pairId)) return@mapNotNull null
            val sides = checkNotNull(byPair[pairId]).sortedBy { side ->
                when (side.idCardSide) {
                    IdCardSide.FRONT -> 0
                    IdCardSide.BACK -> 1
                    null -> 2
                }
            }
            PdfSheet.IdCardCopy(sides.map { it.imagePath })
        }
    }
}

/** A rectangle in PDF points, origin at the page's top left. */
internal data class PointRect(val left: Float, val top: Float, val width: Float, val height: Float)

internal data class IdCardCopyPlacement(
    val pageWidthPoints: Int,
    val pageHeightPoints: Int,
    val cards: List<PointRect>,
)

/**
 * Places the sides of an ID card at real ID-1 size (85.6 x 54 mm), stacked and centred, the way
 * a photocopy of a card looks. A page without a physical size (Auto fit) prints on A4. Cards only
 * shrink when they could not fit otherwise (e.g. two upright cards on a landscape page).
 */
internal object IdCardCopyLayout {
    /** ISO/IEC 7810 ID-1 in PDF points (1/72 in). */
    const val CARD_LONG_SIDE_POINTS = 85.60f / 25.4f * 72f
    const val CARD_SHORT_SIDE_POINTS = 53.98f / 25.4f * 72f
    const val GAP_POINTS = 36f

    /**
     * @param imageSizes width x height in pixels of each side, front first. A side taller than it
     *   is wide is an upright card and is placed upright.
     */
    fun place(
        imageSizes: List<Pair<Int, Int>>,
        options: PdfExportOptions,
        marginPoints: Int,
        footerPoints: Int,
    ): IdCardCopyPlacement {
        require(imageSizes.isNotEmpty()) { "An ID card sheet needs at least one side." }
        val size = options.pageSize.takeIf { it != PdfPageSize.FIT } ?: PdfPageSize.A4
        val portraitWidth = checkNotNull(size.portraitWidthPoints)
        val portraitHeight = checkNotNull(size.portraitHeightPoints)
        val (pageWidth, pageHeight) = if (options.orientation == PdfPageOrientation.LANDSCAPE) {
            portraitHeight to portraitWidth
        } else {
            portraitWidth to portraitHeight
        }

        val cardSizes = imageSizes.map { (width, height) ->
            if (height > width) {
                CARD_SHORT_SIDE_POINTS to CARD_LONG_SIDE_POINTS
            } else {
                CARD_LONG_SIDE_POINTS to CARD_SHORT_SIDE_POINTS
            }
        }
        val availableWidth = (pageWidth - marginPoints * 2).toFloat().coerceAtLeast(1f)
        val availableHeight = (pageHeight - marginPoints * 2 - footerPoints).toFloat().coerceAtLeast(1f)
        val stackWidth = cardSizes.maxOf { it.first }
        val stackHeight = cardSizes.sumOf { it.second.toDouble() }.toFloat() + GAP_POINTS * (cardSizes.size - 1)
        val scale = min(1f, min(availableWidth / stackWidth, availableHeight / stackHeight))

        var top = marginPoints + (availableHeight - stackHeight * scale) / 2f
        val cards = cardSizes.map { (width, height) ->
            val scaledWidth = width * scale
            val scaledHeight = height * scale
            val rect = PointRect(
                left = marginPoints + (availableWidth - scaledWidth) / 2f,
                top = top,
                width = scaledWidth,
                height = scaledHeight,
            )
            top += scaledHeight + GAP_POINTS * scale
            rect
        }
        return IdCardCopyPlacement(pageWidth, pageHeight, cards)
    }
}
