package `in`.c1ph3rj.scanly.core.ml

import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticDocumentModelSelectionPolicyTest {
    @Test
    fun `chooses the most accurate model inside the latency budget`() {
        val selected = AutomaticDocumentModelSelectionPolicy.choose(
            medianLatencyMillis = mapOf(
                DocumentCornerModel.LITE to 8.0,
                DocumentCornerModel.STANDARD to 17.0,
                DocumentCornerModel.HIGH to 31.0,
            ),
            latencyBudgetMillis = 35.0,
        )

        assertEquals(DocumentCornerModel.HIGH, selected)
    }

    @Test
    fun `drops to standard when high exceeds the latency budget`() {
        val selected = AutomaticDocumentModelSelectionPolicy.choose(
            medianLatencyMillis = mapOf(
                DocumentCornerModel.LITE to 8.0,
                DocumentCornerModel.STANDARD to 19.0,
                DocumentCornerModel.HIGH to 48.0,
            ),
            latencyBudgetMillis = 35.0,
        )

        assertEquals(DocumentCornerModel.STANDARD, selected)
    }

    @Test
    fun `uses the fastest measured model when every model exceeds the budget`() {
        val selected = AutomaticDocumentModelSelectionPolicy.choose(
            medianLatencyMillis = mapOf(
                DocumentCornerModel.LITE to 44.0,
                DocumentCornerModel.STANDARD to 57.0,
                DocumentCornerModel.HIGH to 81.0,
            ),
            latencyBudgetMillis = 35.0,
        )

        assertEquals(DocumentCornerModel.LITE, selected)
    }

    @Test
    fun `never selects accurate automatically`() {
        val selected = AutomaticDocumentModelSelectionPolicy.choose(
            medianLatencyMillis = mapOf(
                DocumentCornerModel.ACCURATE to 1.0,
                DocumentCornerModel.STANDARD to 18.0,
            ),
            latencyBudgetMillis = 35.0,
        )

        assertEquals(DocumentCornerModel.STANDARD, selected)
    }
}
