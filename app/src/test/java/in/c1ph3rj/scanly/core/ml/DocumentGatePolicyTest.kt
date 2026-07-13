package `in`.c1ph3rj.scanly.core.ml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentGatePolicyTest {
    @Test
    fun strictPostProcessingThresholdRejectsBorderlineDocument() {
        val result = result(physical = 0.93f, screen = 0.04f, neither = 0.03f)

        assertTrue(result.acceptsPhysicalDocument(DocumentGatePolicy.LIVE_THRESHOLD))
        assertFalse(result.acceptsPhysicalDocument(DocumentGatePolicy.POST_PROCESSING_THRESHOLD))
    }

    @Test
    fun screenPredictionNeverPassesEvenWithLargePhysicalProbability() {
        val result = result(physical = 0.46f, screen = 0.52f, neither = 0.02f)

        assertFalse(result.acceptsPhysicalDocument(0.40f))
    }

    private fun result(physical: Float, screen: Float, neither: Float) = DocumentGateResult(
        probabilities = mapOf(
            DocumentGateClass.PHYSICAL_DOCUMENT to physical,
            DocumentGateClass.DIGITAL_SCREEN to screen,
            DocumentGateClass.NEITHER to neither,
        ),
        timing = DocumentGateTiming(0L, 0L, 0L, 0L),
    )
}
