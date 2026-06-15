package `in`.c1ph3rj.scanly.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageFormatterTest {
    @Test
    fun formatBytes_returnsZeroForNonPositiveValues() {
        assertEquals("0 B", StorageFormatter.formatBytes(0L))
        assertEquals("0 B", StorageFormatter.formatBytes(-10L))
    }

    @Test
    fun formatBytes_formatsCommonUnits() {
        assertEquals("512.0 B", StorageFormatter.formatBytes(512L))
        assertEquals("1.0 KB", StorageFormatter.formatBytes(1024L))
        assertEquals("1.5 MB", StorageFormatter.formatBytes(1_572_864L))
        assertEquals("1.0 GB", StorageFormatter.formatBytes(1_073_741_824L))
    }
}
