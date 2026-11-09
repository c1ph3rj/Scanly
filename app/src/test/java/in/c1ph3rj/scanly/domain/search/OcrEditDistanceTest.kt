package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrEditDistanceTest {
    private fun distance(a: String, b: String, max: Double = 2.0) = OcrEditDistance.distance(a, b, max)

    @Test
    fun lookAlikeCharactersCostLessThanATypo() {
        assertEquals(OcrConfusions.LOOK_ALIKE_COST, distance("invoice", "lnvoice"), 1e-9)
        assertEquals(OcrConfusions.LOOK_ALIKE_COST, distance("total", "t0tal"), 1e-9)
        assertEquals(1.0, distance("invoice", "invoace"), 1e-9)
    }

    @Test
    fun oneGlyphReadAsTwoEitherWay() {
        assertEquals(OcrConfusions.SPLIT_GLYPH_COST, distance("model", "rnodel"), 1e-9)
        assertEquals(OcrConfusions.SPLIT_GLYPH_COST, distance("rnodel", "model"), 1e-9)
        assertEquals(OcrConfusions.SPLIT_GLYPH_COST, distance("date", "clate"), 1e-9)
    }

    @Test
    fun indicVowelSignLookAlikes() {
        assertEquals(OcrConfusions.SPLIT_GLYPH_COST, distance("मिल", "मील"), 1e-9)
        assertEquals(OcrConfusions.SPLIT_GLYPH_COST, distance("குடும்பம்", "கூடும்பம்"), 1e-9)
    }

    @Test
    fun plainEditsStillCountOneEach() {
        assertEquals(1.0, distance("webiste", "website"), 1e-9)
        assertEquals(2.0, distance("agremnent", "agreement"), 1e-9)
    }

    @Test
    fun stopsEarlyPastTheLimit() {
        assertTrue(distance("invoice", "zebra", max = 1.0) > 1.0)
        assertEquals(0.0, distance("same", "same"), 0.0)
    }

    @Test
    fun firstLetterLookAlikesIncludeSplitGlyphs() {
        assertTrue('l' in OcrConfusions.firstCharsFor('i'))
        assertTrue('r' in OcrConfusions.firstCharsFor('m'))
        assertTrue('m' in OcrConfusions.firstCharsFor('r'))
        assertTrue('i' in OcrConfusions.firstCharsFor('i'))
    }
}
