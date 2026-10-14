package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportDestinationTest {
    @Test
    fun `default destination uses the Downloads Scanly label`() {
        assertEquals("Downloads/Scanly", ExportDestination.DefaultDownloadsScanly.exportLabel)
    }

    @Test
    fun `custom destination uses its display name`() {
        val destination = ExportDestination.CustomTree("content://tree", "My scans/")
        assertEquals("My scans/", destination.exportLabel)
    }
}
