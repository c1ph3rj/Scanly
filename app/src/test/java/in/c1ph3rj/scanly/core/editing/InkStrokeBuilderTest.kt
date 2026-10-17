package `in`.c1ph3rj.scanly.core.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class InkStrokeBuilderTest {

    private val style = InkStrokeBuilder.Style(baseWidth = 6f)

    private val wave: List<Pair<Float, Float>> = (0..40).map { i -> (i * 3f) to (50f * sin(i * 0.3f)) }

    @Test
    fun emptyStrokeProducesNoSamplesAndNoBounds() {
        assertTrue(InkStrokeBuilder.samples(InkStroke(emptyList()), style).isEmpty())
        assertNull(InkStrokeBuilder.bounds(listOf(InkStroke(emptyList())), style))
        assertNull(InkStrokeBuilder.bounds(emptyList(), style))
    }

    @Test
    fun singlePointOrIdenticalPointsProduceOneDot() {
        val single = InkStrokeBuilder.samples(stroke(listOf(10f to 20f)), style)
        assertEquals(1, single.size)
        assertSample(single.single(), 10f, 20f, 6f)

        val identical = InkStrokeBuilder.samples(stroke(listOf(10f to 20f, 10f to 20f, 10f to 20f)), style)
        assertEquals(1, identical.size)
        assertSample(identical.single(), 10f, 20f, 6f)
    }

    @Test
    fun consecutiveSamplesNeverExceedSampleSpacing() {
        val samples = InkStrokeBuilder.samples(stroke(wave), style)
        assertTrue(samples.size > wave.size)
        samples.zipWithNext().forEach { (a, b) ->
            assertTrue("gap ${distanceOf(a, b)}", distanceOf(a, b) <= style.sampleSpacing + 0.01f)
        }
    }

    @Test
    fun firstAndLastSamplesMatchCapturedEndpoints() {
        val samples = InkStrokeBuilder.samples(stroke(wave), style)
        assertEquals(wave.first().first, samples.first().x, 0.001f)
        assertEquals(wave.first().second, samples.first().y, 0.001f)
        assertEquals(wave.last().first, samples.last().x, 0.001f)
        assertEquals(wave.last().second, samples.last().y, 0.001f)
    }

    @Test
    fun widthsStayWithinClampBounds() {
        val low = style.baseWidth * style.minWidthFactor
        val high = style.baseWidth * style.maxWidthFactor
        val strokes = listOf(
            stroke(wave, stepMillis = 1L),
            stroke(wave, stepMillis = 200L),
            stroke(wave, pressure = 0f, isStylus = true),
            stroke(wave, pressure = 3f, isStylus = true),
        )
        strokes.forEach { stroke ->
            InkStrokeBuilder.samples(stroke, style).forEach { sample ->
                assertTrue("width ${sample.width}", sample.width >= low - 1e-3f && sample.width <= high + 1e-3f)
            }
        }
    }

    @Test
    fun fastStrokesAreThinnerThanSlowStrokes() {
        val slow = (0..40).map { i -> i * 1f to 0f }
        val fast = (0..40).map { i -> i * 20f to 0f }
        val slowAverage = InkStrokeBuilder.samples(stroke(slow, stepMillis = 50L), style).map { it.width }.average()
        val fastAverage = InkStrokeBuilder.samples(stroke(fast, stepMillis = 2L), style).map { it.width }.average()
        assertTrue("slow $slowAverage vs fast $fastAverage", slowAverage > fastAverage)
    }

    @Test
    fun stylusFullPressureIsWiderThanLightPressure() {
        val heavy = InkStrokeBuilder.samples(stroke(wave, pressure = 1f, isStylus = true), style).map { it.width }.average()
        val light = InkStrokeBuilder.samples(stroke(wave, pressure = 0.1f, isStylus = true), style).map { it.width }.average()
        assertTrue("heavy $heavy vs light $light", heavy > light)
    }

    @Test
    fun boundsIncludeHalfWidthPadding() {
        val dot = InkStrokeBuilder.bounds(listOf(stroke(listOf(10f to 20f))), style)
        assertNotNullBounds(dot)
        assertEquals(7f, dot!!.left, 0.001f)
        assertEquals(17f, dot.top, 0.001f)
        assertEquals(13f, dot.right, 0.001f)
        assertEquals(23f, dot.bottom, 0.001f)

        val line = stroke(listOf(0f to 0f, 100f to 0f), stepMillis = 30L)
        val maxHalf = InkStrokeBuilder.samples(line, style).maxOf { it.width } / 2f
        val lineBounds = InkStrokeBuilder.bounds(listOf(line), style)
        assertNotNullBounds(lineBounds)
        assertEquals(-maxHalf, lineBounds!!.top, 0.01f)
        assertEquals(maxHalf, lineBounds.bottom, 0.01f)
    }

    private fun stroke(
        points: List<Pair<Float, Float>>,
        stepMillis: Long = 16L,
        pressure: Float = 1f,
        isStylus: Boolean = false,
    ) = InkStroke(
        points = points.mapIndexed { i, (x, y) -> InkPoint(x, y, i * stepMillis, pressure) },
        isStylus = isStylus,
    )

    private fun assertSample(sample: InkSample, x: Float, y: Float, width: Float) {
        assertEquals(x, sample.x, 0.001f)
        assertEquals(y, sample.y, 0.001f)
        assertEquals(width, sample.width, 0.001f)
    }

    private fun assertNotNullBounds(bounds: InkBounds?) {
        assertTrue("expected bounds", bounds != null)
    }

    private fun distanceOf(a: InkSample, b: InkSample): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
