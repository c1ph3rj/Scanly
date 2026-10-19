package `in`.c1ph3rj.scanly.feature.tools.pdf

import `in`.c1ph3rj.scanly.domain.model.PdfImageExport
import `in`.c1ph3rj.scanly.domain.model.PdfImageFormat
import `in`.c1ph3rj.scanly.domain.model.PdfImageOptions
import `in`.c1ph3rj.scanly.domain.model.PdfImageResolution
import `in`.c1ph3rj.scanly.domain.model.PdfPageImage
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfToImagesPresentationTest {
    @Test
    fun formatImageBytesUsesWholeKbThenOneDecimalMbAndGb() {
        assertEquals("0 KB", formatImageBytes(0L))
        assertEquals("0 KB", formatImageBytes(-5L))
        assertEquals("1 KB", formatImageBytes(500L))
        assertEquals("220 KB", formatImageBytes(220L * 1024))
        assertEquals("999 KB", formatImageBytes(999L * 1024))
        assertEquals("1.0 MB", formatImageBytes(1000L * 1024))
        // 1.3 MB expressed in bytes (1.3 * 1024 * 1024 ~= 1363149).
        assertEquals("1.3 MB", formatImageBytes(1_363_149L))
        assertEquals("2.0 GB", formatImageBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun formatImageCountIsSingularOnlyForOne() {
        assertEquals("0 images", formatImageCount(0))
        assertEquals("1 image", formatImageCount(1))
        assertEquals("6 images", formatImageCount(6))
    }

    @Test
    fun exportLabelShowsEstimatedTotalWhenKnown() {
        assertEquals("Choose pages to export", formatPdfToImagesExportLabel(0, 220L * 1024))
        assertEquals("Export 6 images", formatPdfToImagesExportLabel(6, null))
        assertEquals("Export 3 images", formatPdfToImagesExportLabel(3, 0L))
        assertEquals(
            "Export 6 images · ~1.3 MB",
            formatPdfToImagesExportLabel(6, 220L * 1024),
        )
        assertEquals(
            "Export 1 image · ~220 KB",
            formatPdfToImagesExportLabel(1, 220L * 1024),
        )
    }

    @Test
    fun pagesLabelDescribesAllSingleAndPartialSelections() {
        assertEquals("All 6 pages", formatPdfToImagesPagesLabel(6, 6))
        assertEquals("1 page", formatPdfToImagesPagesLabel(1, 1))
        assertEquals("3 of 6 pages", formatPdfToImagesPagesLabel(3, 6))
        assertEquals("Pages", formatPdfToImagesPagesLabel(0, 0))
    }

    @Test
    fun doneTitleCountsSavedImages() {
        assertEquals("1 image saved", formatPdfToImagesDoneTitle(1))
        assertEquals("3 images saved", formatPdfToImagesDoneTitle(3))
    }

    @Test
    fun doneSubtitleJoinsDestinationFormatWidthAndTotal() {
        val export = PdfImageExport(
            images = listOf(
                PdfPageImage(
                    pageIndex = 0,
                    fileName = "page_1.jpg",
                    filePath = "/cache/page_1.jpg",
                    contentUri = "content://media/1",
                    sizeBytes = 600L * 1024,
                ),
                PdfPageImage(
                    pageIndex = 1,
                    fileName = "page_2.jpg",
                    filePath = "/cache/page_2.jpg",
                    contentUri = "content://media/2",
                    sizeBytes = 700L * 1024,
                ),
            ),
            destinationLabel = "Pictures/Scanly",
            options = PdfImageOptions(
                format = PdfImageFormat.JPG,
                resolution = PdfImageResolution.SHARING,
            ),
        )

        assertEquals(
            "In Pictures/Scanly · JPG · 1240 px · 1.3 MB in all",
            formatPdfToImagesDoneSubtitle(export),
        )
    }

    @Test
    fun shareLabelReflectsSelectionAndTotal() {
        assertEquals("Share all 6", formatPdfToImagesShareLabel(0, 6))
        assertEquals("Share 3", formatPdfToImagesShareLabel(3, 6))
        assertEquals("Share all 6", formatPdfToImagesShareLabel(6, 6))
        assertEquals("Share image", formatPdfToImagesShareLabel(0, 1))
        assertEquals("Share image", formatPdfToImagesShareLabel(1, 1))
    }

    @Test
    fun toggledAddsOrRemovesIndexWithoutMutatingOriginal() {
        val original = setOf(1, 2)

        assertEquals(setOf(1), original.toggled(2))
        assertEquals(setOf(1, 2, 3), original.toggled(3))
        assertEquals(setOf(0), emptySet<Int>().toggled(0))
        assertEquals(setOf(1, 2), original)
    }

    @Test
    fun cellLabelShowsOneBasedPageAndSize() {
        val image = PdfPageImage(
            pageIndex = 2,
            fileName = "Lease agreement – page 3.jpg",
            filePath = "/cache/page_3.jpg",
            contentUri = "content://media/3",
            sizeBytes = 344L * 1024,
        )

        assertEquals("Page 3 · 344 KB", formatPdfToImagesCellLabel(image))
    }
}
