package `in`.c1ph3rj.scanly.feature.pip

import `in`.c1ph3rj.scanly.domain.model.PipPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipBitmapTest {

    @Test
    fun everyMoodIsSixteenBySixteen() {
        PipMood.entries.forEach { mood ->
            val rows = PipBitmap.frame(mood)
            assertEquals(mood.name, PipBitmap.SIZE, rows.size)
            rows.forEach { row -> assertEquals(mood.name, PipBitmap.SIZE, row.length) }
        }
    }

    @Test
    fun blinkClosesTheEyesOnlyWhenIdle() {
        val open = PipBitmap.frame(PipMood.IDLE)
        val blink = PipBitmap.frame(PipMood.IDLE, blink = true)
        assertTrue('E' in open[6])
        assertFalse('E' in blink[6])
        assertEquals(PipBitmap.frame(PipMood.WOW), PipBitmap.frame(PipMood.WOW, blink = true))
    }

    @Test
    fun talkingOpensTheMouthOnAlternateFrames() {
        assertNotEquals(
            PipBitmap.frame(PipMood.TALK, mouthOpen = false),
            PipBitmap.frame(PipMood.TALK, mouthOpen = true),
        )
    }

    @Test
    fun lookShiftsEyesOneColumnWithoutTouchingTheOutline() {
        val ahead = PipBitmap.frame(PipMood.IDLE)[6]
        val left = PipBitmap.frame(PipMood.IDLE, look = PipLook.LEFT)[6]
        val right = PipBitmap.frame(PipMood.IDLE, look = PipLook.RIGHT)[6]
        assertEquals(ahead.indexOf('E') - 1, left.indexOf('E'))
        assertEquals(ahead.indexOf('E') + 1, right.indexOf('E'))
        assertEquals(ahead.count { it == 'k' }, left.count { it == 'k' })
    }

    @Test
    fun drawnFacesIgnoreLook() {
        listOf(PipMood.HAPPY, PipMood.SLEEP, PipMood.DIZZY).forEach { mood ->
            assertEquals(PipBitmap.frame(mood), PipBitmap.frame(mood, look = PipLook.RIGHT))
        }
    }

    @Test
    fun runsCoverEveryFilledCellExactlyOnce() {
        val rows = PipBitmap.frame(PipMood.HAPPY)
        val filled = rows.sumOf { row -> row.count { it != '.' } }
        assertEquals(filled, PipBitmap.runs(rows).sumOf { it.length })
    }

    @Test
    fun homeTipRotatesDailyAndHandlesAnyDay() {
        assertEquals(PipLines.homeTipFor(0), PipLines.homeTipFor(PipLines.homeTips.size.toLong()))
        assertNotEquals(PipLines.homeTipFor(0), PipLines.homeTipFor(1))
        assertTrue(PipLines.homeTipFor(-3) in PipLines.homeTips)
    }

    @Test
    fun longSearchQueriesAreShortenedInPipsLine() {
        val line = PipLines.searchNoResults("a".repeat(60))
        assertTrue(line.contains("…"))
        assertTrue(line.length < 70)
    }

    @Test
    fun masterSwitchGatesEveryHelper() {
        val off = PipPreferences(enabled = false)
        assertFalse(off.showHomeTips)
        assertFalse(off.showCameraCoaching)
        assertFalse(off.showCelebrations)
        assertTrue(PipPreferences().showCameraCoaching)
    }
}
