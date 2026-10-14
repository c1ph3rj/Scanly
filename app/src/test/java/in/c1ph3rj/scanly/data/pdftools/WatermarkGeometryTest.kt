package `in`.c1ph3rj.scanly.data.pdftools

import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.domain.model.WatermarkSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class WatermarkGeometryTest {

    // A4 portrait in points, "CONFIDENTIAL" in Helvetica-Bold ≈ 7.56 em.
    private val a4Width = 595f
    private val a4Height = 842f
    private val confidentialWidth = 7.56f
    private val confidentialGlyphs = 12

    @Test
    fun singleStampFitsInsideThePageAtEveryAngleAndSize() {
        for (angle in listOf(0f, -38f, 45f, 90f)) {
            for (size in WatermarkSize.entries) {
                val plan = centeredPlan(angle, size)
                val (boxW, boxH) = rotatedBox(plan, capHeight = 0.72f)
                assertTrue("width overflow at $angle/$size", boxW <= a4Width + 0.5f)
                assertTrue("height overflow at $angle/$size", boxH <= a4Height + 0.5f)
                assertEquals(a4Width / 2f, plan.placements.single().x, 0.01f)
                assertEquals(a4Height / 2f, plan.placements.single().y, 0.01f)
            }
        }
    }

    @Test
    fun horizontalSingleStampNoLongerOverflowsPortraitPages() {
        // The old sizing took max(longEdge-based, width-based) and ran past both edges.
        val plan = centeredPlan(0f, WatermarkSize.LARGE)
        assertTrue(plan.textWidth <= a4Width * 0.9f)
    }

    @Test
    fun sizesAreOrderedSmallToLarge() {
        val small = centeredPlan(-38f, WatermarkSize.SMALL).fontSize
        val medium = centeredPlan(-38f, WatermarkSize.MEDIUM).fontSize
        val large = centeredPlan(-38f, WatermarkSize.LARGE).fontSize
        assertTrue(small < medium && medium < large)
    }

    @Test
    fun tilesCoverTheWholePageIncludingCorners() {
        val plan = WatermarkGeometry.plan(
            pageWidth = a4Width,
            pageHeight = a4Height,
            unitWidth = confidentialWidth,
            glyphCount = confidentialGlyphs,
            capHeight = 0.72f,
            layout = WatermarkLayout.REPEATED,
            size = WatermarkSize.MEDIUM,
            angleDegrees = -38f,
        )
        assertTrue(plan.placements.size in 6..160)
        // Every corner region has a tile centre within roughly one tile of it.
        val reach = plan.textWidth
        listOf(0f to 0f, a4Width to 0f, 0f to a4Height, a4Width to a4Height).forEach { (cx, cy) ->
            assertTrue(
                "corner ($cx,$cy) uncovered",
                plan.placements.any { abs(it.x - cx) < reach && abs(it.y - cy) < reach },
            )
        }
        // The pattern is centred: a tile sits on the page centre.
        assertTrue(plan.placements.any { abs(it.x - a4Width / 2) < 0.5f && abs(it.y - a4Height / 2) < 0.5f })
    }

    @Test
    fun longTiledStampsShrinkSoRowsStillRepeat() {
        val plan = WatermarkGeometry.plan(
            pageWidth = a4Width,
            pageHeight = a4Height,
            unitWidth = 16f, // "For KYC use only · 14 Oct 2026"
            glyphCount = 30,
            capHeight = 0.72f,
            layout = WatermarkLayout.REPEATED,
            size = WatermarkSize.LARGE,
            angleDegrees = 0f,
        )
        assertTrue(plan.textWidth <= a4Width * 0.65f)
    }

    @Test
    fun rotatedPagesMapTheVisualCentreToTheBoxCentre() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val (vw, vh) = WatermarkGeometry.visualSize(a4Width, a4Height, rotation)
            val (ux, uy) = WatermarkGeometry.toUserSpace(vw / 2, vh / 2, 10f, 20f, a4Width, a4Height, rotation)
            assertEquals(10f + a4Width / 2, ux, 0.01f)
            assertEquals(20f + a4Height / 2, uy, 0.01f)
        }
    }

    @Test
    fun rotationNinetyMapsVisualCornersOntoTheBox() {
        // Viewer turns the page clockwise: user top-left becomes visual top-right.
        val (vw, vh) = WatermarkGeometry.visualSize(a4Width, a4Height, 90)
        assertEquals(a4Height, vw, 0f)
        assertEquals(a4Width, vh, 0f)
        val (ux, uy) = WatermarkGeometry.toUserSpace(vw, vh, 0f, 0f, a4Width, a4Height, 90)
        assertEquals(0f, ux, 0.01f)
        assertEquals(a4Height, uy, 0.01f)
        assertEquals(90f, WatermarkGeometry.toUserAngle(0f, 90), 0f)
    }

    @Test
    fun rotationIsNormalised() {
        assertEquals(270, WatermarkGeometry.normalizeRotation(-90))
        assertEquals(0, WatermarkGeometry.normalizeRotation(360))
        assertEquals(90, WatermarkGeometry.normalizeRotation(450))
    }

    @Test
    fun complexScriptsAreDetected() {
        assertTrue(needsComplexShaping("ரகசியம்"))
        assertTrue(needsComplexShaping("गोपनीय"))
        assertFalse(needsComplexShaping("CONFIDENTIAL · ₹ 500 – Ünïcödé"))
        assertEquals("For KYC use only", normalizeWatermarkText("  For KYC\n use   only "))
    }

    private fun centeredPlan(angle: Float, size: WatermarkSize) = WatermarkGeometry.plan(
        pageWidth = a4Width,
        pageHeight = a4Height,
        unitWidth = confidentialWidth,
        glyphCount = confidentialGlyphs,
        capHeight = 0.72f,
        layout = WatermarkLayout.CENTERED,
        size = size,
        angleDegrees = angle,
    )

    private fun rotatedBox(plan: WatermarkGeometry.Plan, capHeight: Float): Pair<Float, Float> {
        val radians = Math.toRadians(plan.angleDegrees.toDouble())
        val c = abs(cos(radians)).toFloat()
        val s = abs(sin(radians)).toFloat()
        val h = plan.fontSize * capHeight
        return (plan.textWidth * c + h * s) to (plan.textWidth * s + h * c)
    }
}
