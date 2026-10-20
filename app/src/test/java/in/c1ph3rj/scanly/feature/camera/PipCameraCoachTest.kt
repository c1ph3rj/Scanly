package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.feature.pip.PipMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipCameraCoachTest {

    @Test
    fun sceneIssuesWinOverPhaseAndWarn() {
        CaptureSceneIssue.entries.forEach { issue ->
            val coach = pipCoachFor(AutoCapturePhase.COUNTDOWN, issue, countdownValue = 1, hasDocument = true)
            assertTrue(issue.name, coach.warning)
            assertNull(issue.name, coach.progress)
        }
        assertEquals(
            PipMood.SLEEP,
            pipCoachFor(AutoCapturePhase.SEARCHING, CaptureSceneIssue.TOO_DARK, null, false).mood,
        )
    }

    @Test
    fun progressClimbsTowardsCapture() {
        val hold = pipCoachFor(AutoCapturePhase.HOLD_STEADY, null, null, true).progress!!
        val three = pipCoachFor(AutoCapturePhase.COUNTDOWN, null, 3, true).progress!!
        val two = pipCoachFor(AutoCapturePhase.COUNTDOWN, null, 2, true).progress!!
        val one = pipCoachFor(AutoCapturePhase.COUNTDOWN, null, 1, true).progress!!
        val capturing = pipCoachFor(AutoCapturePhase.CAPTURING, null, null, true).progress!!
        assertTrue(hold < three && three < two && two < one && one < capturing)
        assertEquals(1f, capturing)
    }

    @Test
    fun pipCheersAfterACapture() {
        assertEquals(PipMood.HAPPY, pipCoachFor(AutoCapturePhase.COOLDOWN, null, null, true).mood)
        assertEquals(PipMood.HAPPY, pipCoachFor(AutoCapturePhase.CAPTURING, null, null, false).mood)
    }

    @Test
    fun manualModeSaysWhetherEdgesAreFound() {
        assertEquals(PipMood.WOW, pipCoachFor(AutoCapturePhase.OFF, null, null, hasDocument = true).mood)
        assertEquals(PipMood.IDLE, pipCoachFor(AutoCapturePhase.OFF, null, null, hasDocument = false).mood)
    }

    @Test
    fun searchingAsksForTheWholePage() {
        val coach = pipCoachFor(AutoCapturePhase.SEARCHING, null, null, false)
        assertEquals(PipMood.IDLE, coach.mood)
        assertNull(coach.progress)
        assertTrue(coach.line.contains("four corners"))
    }
}
