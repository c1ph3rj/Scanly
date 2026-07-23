package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanModeTest {
    @Test
    fun scanModesRoundTripThroughStorage() {
        ScanMode.entries.forEach { mode ->
            assertEquals(mode, ScanMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun missingOrUnknownModeFallsBackToDocument() {
        assertEquals(ScanMode.DOCUMENT, ScanMode.fromStorage(null))
        assertEquals(ScanMode.DOCUMENT, ScanMode.fromStorage("future_mode"))
    }

    @Test
    fun idSidesAreOptionalAndStrictlyDecoded() {
        assertEquals(IdCardSide.FRONT, IdCardSide.fromStorage("front"))
        assertEquals(IdCardSide.BACK, IdCardSide.fromStorage("back"))
        assertNull(IdCardSide.fromStorage(null))
        assertNull(IdCardSide.fromStorage("sideways"))
    }
}
