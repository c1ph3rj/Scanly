package `in`.c1ph3rj.scanly.data.export

import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfPageMargin
import `in`.c1ph3rj.scanly.domain.model.PdfPageNumber
import `in`.c1ph3rj.scanly.domain.model.PdfPageOrientation
import `in`.c1ph3rj.scanly.domain.model.PdfPageSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageLayoutResolverTest {

    @Test
    fun autoOrientationFollowsEachSourcePage() {
        val options = PdfExportOptions(
            orientation = PdfPageOrientation.AUTO,
            pageSize = PdfPageSize.A4,
        )

        val portrait = PdfPageLayoutResolver.resolve(1_200, 1_800, options)
        val landscape = PdfPageLayoutResolver.resolve(1_800, 1_200, options)

        assertEquals(595, portrait.pageWidthPoints)
        assertEquals(842, portrait.pageHeightPoints)
        assertEquals(842, landscape.pageWidthPoints)
        assertEquals(595, landscape.pageHeightPoints)
    }

    @Test
    fun fixedPageSizeUsesRealPdfPointDimensions() {
        val layout = PdfPageLayoutResolver.resolve(
            imageWidth = 1_200,
            imageHeight = 1_800,
            options = PdfExportOptions(
                orientation = PdfPageOrientation.LANDSCAPE,
                pageSize = PdfPageSize.A3,
            ),
        )

        assertEquals(1_191, layout.pageWidthPoints)
        assertEquals(842, layout.pageHeightPoints)
    }

    @Test
    fun autoFitPreservesTheSourceAspectRatioWithinAStandardLongEdge() {
        val layout = PdfPageLayoutResolver.resolve(
            imageWidth = 1_200,
            imageHeight = 1_800,
            options = PdfExportOptions(pageSize = PdfPageSize.FIT),
        )

        assertEquals(561, layout.pageWidthPoints)
        assertEquals(842, layout.pageHeightPoints)
    }

    @Test
    fun pageNumberReservesFooterWithoutOverlappingTheScan() {
        val withoutNumber = PdfPageLayoutResolver.resolve(
            1_200,
            1_800,
            PdfExportOptions(pageSize = PdfPageSize.A4),
        )
        val withNumber = PdfPageLayoutResolver.resolve(
            1_200,
            1_800,
            PdfExportOptions(
                pageSize = PdfPageSize.A4,
                pageNumber = PdfPageNumber.BOTTOM_CENTER,
            ),
        )

        assertEquals(842, withoutNumber.contentHeightPoints)
        assertEquals(814, withNumber.contentHeightPoints)
        assertEquals(28, withNumber.footerPoints)
    }

    @Test
    fun marginsReduceContentAreaOnAllSides() {
        val layout = PdfPageLayoutResolver.resolve(
            1_200,
            1_800,
            PdfExportOptions(
                pageSize = PdfPageSize.A4,
                margin = PdfPageMargin.SMALL,
            ),
        )

        assertEquals(24, layout.marginPoints)
        assertEquals(547, layout.contentWidthPoints)
        assertEquals(794, layout.contentHeightPoints)
    }
}
