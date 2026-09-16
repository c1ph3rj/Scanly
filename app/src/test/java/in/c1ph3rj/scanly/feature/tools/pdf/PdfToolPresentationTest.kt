package `in`.c1ph3rj.scanly.feature.tools.pdf

import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.domain.model.PdfPasswordMode
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfToolPresentationTest {
    @Test
    fun mergeCopyUsesDocumentLanguageNotPrototypeCaps() {
        assertEquals("No files", formatMergeFileCount(0))
        assertEquals("1 PDF", formatMergeFileCount(1))
        assertEquals("3 PDFs", formatMergeFileCount(3))
        assertEquals("1", formatMergeFileOrdinal(0))
        assertEquals("2", formatMergeFileOrdinal(1))
        assertEquals(
            "Add at least one more PDF to combine.",
            formatMergeReadyHint(1),
        )
        assertEquals(
            "Files merge in this order. The first PDF starts the document.",
            formatMergeReadyHint(2),
        )
    }

    @Test
    fun documentMetaJoinsPagesSizeAndProtection() {
        assertEquals("1 page", formatPdfPageCount(1))
        assertEquals("12 pages", formatPdfPageCount(12))
        assertEquals("Protected", formatPdfProtectionLabel(true))
        assertEquals("Unlocked", formatPdfProtectionLabel(false))
        assertEquals(
            "4 pages  ·  ${StorageFormatter.formatBytes(2048L)}  ·  Protected",
            formatPdfDocumentMeta(pageCount = 4, sizeBytes = 2048L, isEncrypted = true),
        )
    }

    @Test
    fun compressComparisonAndPasswordActionsAreSentenceCase() {
        assertEquals("Protect PDF", formatPasswordModeActionLabel(PdfPasswordMode.Protect))
        assertEquals("Remove password", formatPasswordModeActionLabel(PdfPasswordMode.Remove))
        assertNull(formatCompressSizeComparison(null, 10L, 10f))
        assertEquals(
            "${StorageFormatter.formatBytes(1000L)} → ${StorageFormatter.formatBytes(400L)}  ·  saved 60%",
            formatCompressSizeComparison(1000L, 400L, 60f),
        )
    }

    @Test
    fun watermarkHintsStayReadable() {
        assertEquals(
            "Repeats across the full page, including the edges.",
            formatWatermarkLayoutHint(WatermarkLayout.REPEATED),
        )
        assertEquals(
            "One large stamp sized to dominate the page.",
            formatWatermarkLayoutHint(WatermarkLayout.CENTERED),
        )
        assertEquals("Opacity 24%", formatWatermarkOpacityPercent(0.24f))
        assertEquals("Opacity 0%", formatWatermarkOpacityPercent(-1f))
        assertEquals("Opacity 100%", formatWatermarkOpacityPercent(2f))
    }
}
