package `in`.c1ph3rj.scanly.data.text.indra

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndraPureLogicTest {
    private fun logits(classes: Int, vararg frames: Pair<Int, Float>): FloatArray {
        val out = FloatArray(frames.size * classes) { -10f }
        frames.forEachIndexed { t, (id, value) -> out[t * classes + id] = value }
        return out
    }

    @Test
    fun ctcCollapsesRepeatsAndDropsBlanks() {
        // ids: 1 1 0 1 2 2 -> tokens 1, 1, 2 (a blank separates the repeated 1)
        val data = logits(4, 1 to 10f, 1 to 10f, 0 to 10f, 1 to 10f, 2 to 10f, 2 to 10f)
        val line = IndraCtc.decode(data, 0, 6, 4)
        assertArrayEquals(intArrayOf(1, 1, 2), line.ids)
        assertArrayEquals(intArrayOf(0, 3, 4), line.steps)
        assertTrue(line.conf > 0.99f)
    }

    @Test
    fun ctcRespectsOffsetAndLengthAndEmptyConfIsZero() {
        val data = logits(3, 1 to 10f, 2 to 10f, 0 to 10f)
        val onlyBlank = IndraCtc.decode(data, 2 * 3, 1, 3)
        assertEquals(0, onlyBlank.ids.size)
        assertEquals(0f, onlyBlank.conf, 0f)
        val clipped = IndraCtc.decode(data, 0, 1, 3)
        assertArrayEquals(intArrayOf(1), clipped.ids)
    }

    @Test
    fun ctcConfidenceIsMeanTopProbabilityAndLowConfFallsBelowMinimum() {
        // Two classes equally likely among 3: top prob = e^0 / (e^0 + e^0 + e^-10) ~ 0.5
        val data = floatArrayOf(-10f, 0f, 0f)
        val line = IndraCtc.decode(data, 0, 1, 3)
        assertEquals(0.5f, line.conf, 0.01f)
        val weak = floatArrayOf(-10f, 0f, -0.2f)
        assertTrue(IndraCtc.decode(weak, 0, 1, 3).conf > IndraCtc.MIN_CONF)
        val veryWeak = floatArrayOf(0f, 0.01f, 0.0f, 0.0f)
        assertTrue(IndraCtc.decode(veryWeak, 0, 1, 4).conf < IndraCtc.MIN_CONF)
    }

    @Test
    fun baseVocabInsertsSpaceAfterLatexCommandBeforeLetter() {
        val vocab = IndraVocab(listOf("\\frac", "x", "\\alpha", "(", "a", "b", "\\"))
        // \frac x -> "\frac x"; \alpha ( -> no space; \alpha a -> space
        assertEquals("\\frac x", vocab.decodeBase(intArrayOf(1, 2)))
        assertEquals("\\alpha(", vocab.decodeBase(intArrayOf(3, 4)))
        assertEquals("\\alpha ab", vocab.decodeBase(intArrayOf(3, 5, 6)))
        assertEquals("\\", vocab.decodeBase(intArrayOf(7)))
    }

    @Test
    fun groupVocabCollapsesWhitespaceAndComposes() {
        val vocab = IndraVocab(listOf("a", " ", "e", "́", "b"))
        // "a  e" + combining acute + " b" -> single spaces, NFC
        assertEquals("a é b", vocab.decodeGroup(intArrayOf(1, 2, 2, 3, 4, 2, 5)))
    }

    @Test
    fun detDecodeBuildsOneBoxWithSpecFormulas() {
        val mapH = 10
        val mapW = 40
        val out = FloatArray(mapH * mapW * 3) { if (it % 3 == 0) -5f else 0f }
        for (row in 3..4) {
            for (col in 5..20) {
                val base = (row * mapW + col) * 3
                out[base] = 5f
                out[base + 1] = 3f
                out[base + 2] = 3f
            }
        }
        // An isolated single pixel must be dropped (area < 2).
        out[(8 * mapW + 30) * 3] = 5f
        val boxes = IndraDetDecoder.decode(out, mapH, mapW)
        assertEquals(1, boxes.size)
        val box = boxes[0]
        // ys = 7 and 9 -> mean 8; y0 = 8 - 3, y1 = 8 + 3
        assertEquals(5f, box.y0, 1e-4f)
        assertEquals(11f, box.y1, 1e-4f)
        // lh = 6, ex = min(0.9, 3) = 0.9; x0 = 5*2 - ex, x1 = (5 + 16)*2 + ex
        assertEquals(9.1f, box.x0, 1e-4f)
        assertEquals(42.9f, box.x1, 1e-4f)
        assertTrue(box.score > 0.99f)
    }

    @Test
    fun detDecodeUsesFourConnectivity() {
        val out = FloatArray(4 * 4 * 3) { if (it % 3 == 0) -5f else 1f }
        // Two diagonal cells are not connected under 4-connectivity: both areas are 1 -> dropped.
        out[(0 * 4 + 0) * 3] = 5f
        out[(1 * 4 + 1) * 3] = 5f
        assertEquals(0, IndraDetDecoder.decode(out, 4, 4).size)
    }

    @Test
    fun adaptiveSecondPassOnlyForManyTinyLines() {
        val tiny = List(10) { IndraBox(0f, 0f, 100f, 6f) }
        assertEquals(2048, IndraDetDecoder.adaptiveLongSide(tiny))
        assertNull(IndraDetDecoder.adaptiveLongSide(List(10) { IndraBox(0f, 0f, 100f, 20f) }))
        assertNull(IndraDetDecoder.adaptiveLongSide(tiny.take(5)))
    }

    @Test
    fun xyCutReadsTwoColumnsLeftThenRight() {
        // Left column x 0..100, right column x 160..260, three lines each, interleaved by y.
        val boxes = listOf(
            IndraBox(160f, 0f, 260f, 20f), // 0 right 1
            IndraBox(0f, 0f, 100f, 20f), // 1 left 1
            IndraBox(0f, 30f, 100f, 50f), // 2 left 2
            IndraBox(160f, 30f, 260f, 50f), // 3 right 2
            IndraBox(160f, 60f, 260f, 80f), // 4 right 3
            IndraBox(0f, 60f, 100f, 80f), // 5 left 3
        )
        assertEquals(listOf(1, 2, 5, 0, 3, 4), IndraLayout.xyCut(boxes))
    }

    @Test
    fun xyCutKeepsFullWidthTitleFirstAndMirroredRtlColumnsRightFirst() {
        val boxes = listOf(
            IndraBox(0f, 100f, 100f, 120f), // left column
            IndraBox(0f, 0f, 260f, 30f), // title spanning both
            IndraBox(160f, 100f, 260f, 120f), // right column
        )
        assertEquals(listOf(1, 0, 2), IndraLayout.xyCut(boxes))
        val mirrored = IndraLayout.mirror(boxes, 260f)
        assertEquals(listOf(1, 2, 0), IndraLayout.xyCut(mirrored))
    }

    @Test
    fun blocksSplitOnLargeGapAndHeightChange() {
        val ordered = listOf(
            IndraBox(0f, 0f, 200f, 20f),
            IndraBox(0f, 22f, 190f, 42f),
            IndraBox(0f, 120f, 200f, 140f), // big gap -> new block
            IndraBox(0f, 142f, 100f, 182f), // taller line -> new block
        )
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3)), IndraLayout.groupBlocks(ordered))
    }

    @Test
    fun rtlFlipTurnsVisualHebrewWithDigitsIntoLogicalOrder() {
        val shalom = "שלום" // logical: shin lamed vav mem
        val visual = "123 " + shalom.reversed()
        assertEquals("$shalom 123", IndraRtl.flip(visual, baseRtl = true))
        assertEquals("$shalom 123", IndraRtl.flip(visual, baseRtl = null))
    }

    @Test
    fun rtlFlipKeepsLatinRunsAndLeavesLtrTextAlone() {
        val shalom = "שלום"
        val olam = "עולם"
        // logical "shalom Scanly olam" shows as "mlo Scanly mlohs" left-to-right.
        val visual = olam.reversed() + " Scanly " + shalom.reversed()
        assertEquals("$shalom Scanly $olam", IndraRtl.flip(visual, baseRtl = true))
        assertEquals("Hello 123", IndraRtl.flip("Hello 123", baseRtl = true))
    }

    @Test
    fun pageDirectionComparesRtlLettersWithTheRest() {
        assertTrue(IndraRtl.isPageRtl(listOf("שלום עולם", "ab")))
        assertTrue(!IndraRtl.isPageRtl(listOf("hello world", "ש")))
    }

    @Test
    fun cropGeometryMatchesSpecPadding() {
        val box = IndraBox(100f, 50f, 300f, 70f) // h = 20
        val one = IndraGeometry.cropGeometry(box, 1)
        assertEquals(94, one.x0) // 100 - 0.3*20
        assertEquals(46, one.y0) // 50 - 0.2*20
        assertEquals(306, one.x1)
        assertEquals(74, one.y1)
        val three = IndraGeometry.cropGeometry(box, 3)
        assertEquals(18, three.y0) // 50 - 1.6*20
        assertEquals(102, three.y1)
        assertEquals(Math.round(212.0 * 96 / 84).toInt(), three.outWidth)
    }

    @Test
    fun darkLineDetectionUsesPercentileRule() {
        val light = IntArray(256).also { it[240] = 900; it[20] = 100 }
        assertTrue(!IndraGeometry.isDarkLine(light))
        val dark = IntArray(256).also { it[30] = 900; it[240] = 100 }
        assertTrue(IndraGeometry.isDarkLine(dark))
    }

    @Test
    fun widthBatchesGroupSimilarWidthsNarrowestFirst() {
        val batches = IndraGeometry.widthBatches(intArrayOf(500, 100, 110, 520, 130), maxLines = 16)
        assertEquals(2, batches.size)
        assertArrayEquals(intArrayOf(1, 2, 4), batches[0])
        assertArrayEquals(intArrayOf(0, 3), batches[1])
    }

    @Test
    fun pageTransformMapsQuarterTurnsBackToTheOriginal() {
        // Original 200 wide x 100 tall; clockwise quarter turn gives 100 x 200.
        val transform = IndraPageTransform(200, 100, 1, 0.0)
        // Original point (30, 10) -> turned (100 - 10, 30) = (90, 30).
        val (x, y) = transform.toOriginal(90f, 30f)
        assertEquals(30f, x, 1e-3f)
        assertEquals(10f, y, 1e-3f)
        val three = IndraPageTransform(200, 100, 3, 0.0)
        // Counter-clockwise turn: (30, 10) -> (10, 200 - 30) = (10, 170).
        val (x3, y3) = three.toOriginal(10f, 170f)
        assertEquals(30f, x3, 1e-3f)
        assertEquals(10f, y3, 1e-3f)
    }

    @Test
    fun leaderLinesAreDroppedButRealDashesKept() {
        assertTrue(IndraLayout.isLeaderLine("........", IndraBox(0f, 0f, 200f, 10f)))
        assertTrue(!IndraLayout.isLeaderLine("-", IndraBox(0f, 0f, 10f, 10f)))
        assertTrue(!IndraLayout.isLeaderLine("Total", IndraBox(0f, 0f, 100f, 10f)))
    }
}
