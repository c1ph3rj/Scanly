package `in`.c1ph3rj.scanly.feature.components

import `in`.c1ph3rj.scanly.domain.model.LibraryStartupStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryProgressPanelTest {
    @Test
    fun applyingDeltaMarksReadStepAsActive() {
        val steps = startupProgressSteps(LibraryStartupStatus.APPLYING_DELTA)

        assertEquals(LibraryProgressStepState.DONE, steps[0].state)
        assertEquals(LibraryProgressStepState.ACTIVE, steps[1].state)
        assertEquals(LibraryProgressStepState.PENDING, steps[2].state)
    }

    @Test
    fun rebuildingDatabaseMarksPrepareStepAsActive() {
        val steps = startupProgressSteps(LibraryStartupStatus.REBUILDING_DATABASE)

        assertTrue(steps[0].state == LibraryProgressStepState.DONE)
        assertTrue(steps[1].state == LibraryProgressStepState.DONE)
        assertEquals(LibraryProgressStepState.ACTIVE, steps[2].state)
    }
}
