package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetectionFrameBitmapTest {
    @Test
    fun copiesOpaqueRgbaBytesAndKeepsRotationDirection() {
        val frame = DetectionFrame(
            width = 3,
            height = 1,
            rotationDegrees = 90,
            bytes = byteArrayOf(
                -1, 0, 0, -1,
                0, -1, 0, -1,
                0, 0, -1, -1,
            ),
        )
        val bitmap = frame.toOrientedBitmap()
        try {
            assertEquals(1, bitmap.width)
            assertEquals(3, bitmap.height)
            assertEquals(Color.RED, bitmap.getPixel(0, 0))
            assertEquals(Color.GREEN, bitmap.getPixel(0, 1))
            assertEquals(Color.BLUE, bitmap.getPixel(0, 2))
        } finally {
            bitmap.recycle()
        }
    }
}
