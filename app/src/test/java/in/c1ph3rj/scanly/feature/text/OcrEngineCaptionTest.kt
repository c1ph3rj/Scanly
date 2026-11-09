package `in`.c1ph3rj.scanly.feature.text

import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import `in`.c1ph3rj.scanly.domain.model.OcrFallbackReason
import `in`.c1ph3rj.scanly.domain.model.PageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrEngineCaptionTest {
    private fun text(
        engine: OcrEngine?,
        fallbackReason: OcrFallbackReason? = null,
    ) = PageText(
        pageId = "p",
        documentId = "d",
        recognizedText = "hello",
        userText = null,
        blocks = emptyList(),
        sourceUpdatedAtMillis = 0,
        recognizedAtMillis = 0,
        kind = null,
        engine = engine,
        requestedEngine = OcrEngine.INDRA.takeIf { fallbackReason != null } ?: engine,
        fallbackReason = fallbackReason,
    )

    @Test
    fun plainMlKitAndUnknownPages_haveNoCaption() {
        assertNull(ocrEngineCaption(text(OcrEngine.ML_KIT)))
        assertNull(ocrEngineCaption(text(null)))
    }

    @Test
    fun indraPage_isLabelledExperimental() {
        assertEquals("Read with Indra OCR (experimental)", ocrEngineCaption(text(OcrEngine.INDRA)))
    }

    @Test
    fun fallbackPage_explainsTheFallback() {
        assertEquals(
            "Read with ML Kit (Indra OCR couldn't read this page)",
            ocrEngineCaption(text(OcrEngine.ML_KIT, OcrFallbackReason.INDRA_TIMEOUT)),
        )
    }
}
