package `in`.c1ph3rj.scanly.data.text.indra

import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndraAvailabilityTest {
    @Test
    fun indraIsTheDefaultOnlyWhenItsModelsShip() {
        assertEquals(OcrEngine.INDRA, IndraAvailability.defaultEngineFor(indraBundled = true))
        assertEquals(OcrEngine.ML_KIT, IndraAvailability.defaultEngineFor(indraBundled = false))
    }

    @Test
    fun everyModelAndVocabularyMustBePresent() {
        val all = IndraAvailability.REQUIRED_FILES
        assertTrue(IndraAvailability.hasAllRequiredFiles(all))
        // Licence files and the optional order model don't matter either way.
        assertTrue(IndraAvailability.hasAllRequiredFiles(all + setOf("NOTICE.txt", "order.tflite")))
        assertFalse(IndraAvailability.hasAllRequiredFiles(emptySet()))
        all.forEach { missing ->
            assertFalse("missing $missing", IndraAvailability.hasAllRequiredFiles(all - missing))
        }
    }
}
