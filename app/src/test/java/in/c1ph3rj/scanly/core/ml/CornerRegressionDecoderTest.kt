package `in`.c1ph3rj.scanly.core.ml

import java.nio.ByteBuffer
import org.junit.Test
import org.junit.Assert.assertEquals

class CornerRegressionDecoderTest {
    @Test
    fun `maps letterboxed corner regression back to original normalized coordinates`() {
        val prepared = PreparedImage(
            inputBuffer = ByteBuffer.allocateDirect(0),
            scale = 2f,
            padX = 0f,
            padY = 50f,
        )

        val quad = decodeRegressionQuad(
            values = floatArrayOf(
                0.10f, 0.30f,
                0.90f, 0.30f,
                0.90f, 0.70f,
                0.10f, 0.70f,
            ),
            prepared = prepared,
            inputWidth = 200,
            inputHeight = 200,
            originalWidth = 100,
            originalHeight = 50,
        )

        assertEquals(0.10f, quad.topLeft.x, 0.0001f)
        assertEquals(0.10f, quad.topLeft.y, 0.0001f)
        assertEquals(0.90f, quad.bottomRight.x, 0.0001f)
        assertEquals(0.90f, quad.bottomRight.y, 0.0001f)
    }
}
