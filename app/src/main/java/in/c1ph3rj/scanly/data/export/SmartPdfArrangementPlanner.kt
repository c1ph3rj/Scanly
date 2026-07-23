package `in`.c1ph3rj.scanly.data.export

import `in`.c1ph3rj.scanly.domain.model.IdCardSide
import `in`.c1ph3rj.scanly.domain.model.ScanMode

internal data class ExportPageSource(
    val imagePath: String,
    val scanMode: ScanMode = ScanMode.DOCUMENT,
    val idCardPairId: String? = null,
    val idCardSide: IdCardSide? = null,
    val sourcePageNumber: Int = 0,
)

internal sealed interface SmartPdfSheet {
    data class Standard(val page: ExportPageSource) : SmartPdfSheet
    data class BookSpread(val page: ExportPageSource) : SmartPdfSheet
    data class IdCardPair(
        val front: ExportPageSource,
        val back: ExportPageSource,
    ) : SmartPdfSheet
}

internal object SmartPdfArrangementPlanner {
    fun plan(pages: List<ExportPageSource>): List<SmartPdfSheet> {
        val idPagesByPair = pages
            .filter { it.scanMode == ScanMode.ID_CARD }
            .groupBy { it.idCardPairId }
        val invalidPair = idPagesByPair[null]?.firstOrNull()
        if (invalidPair != null) {
            error(incompleteIdMessage(invalidPair))
        }

        val idSheetsByPair = idPagesByPair.mapValues { (_, pairPages) ->
            val front = pairPages.singleOrNull { it.idCardSide == IdCardSide.FRONT }
            val back = pairPages.singleOrNull { it.idCardSide == IdCardSide.BACK }
            if (front == null || back == null || pairPages.size != 2) {
                error(incompleteIdMessage(pairPages.first()))
            }
            SmartPdfSheet.IdCardPair(front = front, back = back)
        }

        val emittedPairs = mutableSetOf<String>()
        return buildList {
            pages.forEach { page ->
                when (page.scanMode) {
                    ScanMode.DOCUMENT -> add(SmartPdfSheet.Standard(page))
                    ScanMode.BOOK -> add(SmartPdfSheet.BookSpread(page))
                    ScanMode.ID_CARD -> {
                        val pairId = checkNotNull(page.idCardPairId)
                        if (emittedPairs.add(pairId)) {
                            add(checkNotNull(idSheetsByPair[pairId]))
                        }
                    }
                }
            }
        }
    }

    private fun incompleteIdMessage(page: ExportPageSource): String {
        val pageLabel = page.sourcePageNumber
            .takeIf { it > 0 }
            ?.let { " on page $it" }
            .orEmpty()
        return "ID card$pageLabel is missing a complete front and back pair. " +
            "Capture both sides or use Standard layout."
    }
}
