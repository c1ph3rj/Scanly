package `in`.c1ph3rj.scanly.core.processing.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PageColorLooksTest {
    @Test
    fun sCurveAtZeroStrengthIsIdentity() {
        val table = PageColorLooks.sCurve(0.0)

        for (i in 0..255) {
            assertEquals("entry $i", i, table.unsigned(i))
        }
    }

    @Test
    fun sCurveKeepsBlackAndWhiteFixed() {
        listOf(0.05, 0.15, 0.5, 1.0).forEach { strength ->
            val table = PageColorLooks.sCurve(strength)

            assertEquals("strength $strength black", 0, table.unsigned(0))
            assertEquals("strength $strength white", 255, table.unsigned(255))
        }
    }

    @Test
    fun sCurveIsMonotonicNonDecreasing() {
        listOf(0.05, 0.15, 0.3, 0.5, 1.0).forEach { strength ->
            val table = PageColorLooks.sCurve(strength)

            for (i in 1..255) {
                assertTrue(
                    "strength $strength drops at $i: ${table.unsigned(i - 1)} -> ${table.unsigned(i)}",
                    table.unsigned(i) >= table.unsigned(i - 1),
                )
            }
        }
    }

    @Test
    fun sCurveDarkensBelowMidGrayAndBrightensAboveForPositiveStrength() {
        listOf(0.15, 0.5, 1.0).forEach { strength ->
            val table = PageColorLooks.sCurve(strength)

            for (i in 1 until 128) {
                assertTrue("strength $strength: $i -> ${table.unsigned(i)} brightened", table.unsigned(i) <= i)
            }
            for (i in 129 until 255) {
                assertTrue("strength $strength: $i -> ${table.unsigned(i)} darkened", table.unsigned(i) >= i)
            }
            assertTrue("strength $strength: 64 should darken", table.unsigned(64) < 64)
            assertTrue("strength $strength: 192 should brighten", table.unsigned(192) > 192)
        }
    }

    @Test
    fun neutralWhiteWithDarkLevelMapsInkToBlackAndPaperToNearWhiteOnEveryChannel() {
        val tables = PageColorLooks.levelsTables(
            stats(240.0, 240.0, 240.0, dark = 30.0),
            whiteBalance = 1.0,
            blackClip = 1.0,
        )

        tables.forEachIndexed { channel, table ->
            assertEquals("channel $channel: dark level 30 should map to black", 0, table.unsigned(30))
            assertEquals("channel $channel: white 240 should map to ~248", 248, table.unsigned(240))
        }
    }

    @Test
    fun tintedWhiteGainIsLimitedToFifteenPercent() {
        // Tinted white (255, 200, 150): the neutral reference is the channel average. Red is
        // pulled toward it but its gain (0.79) is clamped to 0.85; blue (gain 1.34) to 1.15.
        val average = (255.0 + 200.0 + 150.0) / 3.0
        val tables = PageColorLooks.levelsTables(
            stats(255.0, 200.0, 150.0, dark = 0.0),
            whiteBalance = 1.0,
            blackClip = 0.0,
        )
        val expectedGains = listOf(0.85, average / 200.0, 1.15)

        tables.forEachIndexed { channel, table ->
            val gain = expectedGains[channel]
            for (input in 0..150) {
                // Table = input x gain x 248 / neutral, rounded to the nearest level.
                val expected = input * gain * 248.0 / average
                assertTrue(
                    "channel $channel input $input: got ${table.unsigned(input)}, expected ~$expected",
                    abs(table.unsigned(input) - expected) <= 1.0,
                )
            }
        }
    }

    @Test
    fun zeroWhiteBalanceLeavesAllChannelTablesIdentical() {
        val tables = PageColorLooks.levelsTables(
            stats(255.0, 200.0, 150.0, dark = 40.0),
            whiteBalance = 0.0,
            blackClip = 0.8,
        )

        assertTrue("R and G tables differ", tables[0].contentEquals(tables[1]))
        assertTrue("G and B tables differ", tables[1].contentEquals(tables[2]))
    }

    @Test
    fun everyLevelsTableIsMonotonic() {
        val statSets = listOf(
            stats(240.0, 240.0, 240.0, dark = 30.0),
            stats(255.0, 200.0, 150.0, dark = 40.0),
            stats(120.0, 110.0, 100.0, dark = 90.0),
            stats(250.0, 252.0, 249.0, dark = 0.0),
            stats(200.0, 200.0, 200.0, dark = 120.0),
        )

        statSets.forEach { stats ->
            listOf(0.0, 0.5, 1.0).forEach { whiteBalance ->
                listOf(0.0, 0.5, 1.0).forEach { blackClip ->
                    PageColorLooks.levelsTables(stats, whiteBalance, blackClip).forEachIndexed { channel, table ->
                        for (i in 1..255) {
                            assertTrue(
                                "wb=$whiteBalance bc=$blackClip channel $channel drops at $i",
                                table.unsigned(i) >= table.unsigned(i - 1),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stats(red: Double, green: Double, blue: Double, dark: Double) = PageColorLooks.GlobalColorStats(
        white = doubleArrayOf(red, green, blue),
        darkLuminance = dark,
        medianLuminance = 128.0,
    )

    private fun ByteArray.unsigned(index: Int): Int = this[index].toInt() and 0xFF
}
