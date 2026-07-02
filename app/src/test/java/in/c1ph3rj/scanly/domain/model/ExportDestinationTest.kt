package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportDestinationTest {
    @Test
    fun `default destination separates exports and backups`() {
        val destination = ExportDestination.DefaultDownloadsScanly
        assertEquals("Downloads/Scanly", destination.exportLabel)
        assertEquals("Downloads/Scanly/backup", destination.backupLabel)
    }

    @Test
    fun `custom destination owns a backup child`() {
        val destination = ExportDestination.CustomTree("content://tree", "My scans/")
        assertEquals("My scans/", destination.exportLabel)
        assertEquals("My scans/backup", destination.backupLabel)
    }
}
