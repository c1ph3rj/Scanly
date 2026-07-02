package `in`.c1ph3rj.scanly.data.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArchivePolicyTest {
    @Test
    fun `small backup reserves sixteen MiB`() {
        val oneMiB = 1024L * 1024L
        assertEquals(17L * oneMiB, LibraryArchivePolicy.backupRequiredBytes(oneMiB))
    }

    @Test
    fun `restore reserves two copies plus headroom`() {
        val tenMiB = 10L * 1024L * 1024L
        assertEquals(52L * 1024L * 1024L, LibraryArchivePolicy.restoreRequiredBytes(tenMiB))
    }

    @Test
    fun `negative and overflowing sizes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            LibraryArchivePolicy.backupRequiredBytes(-1L)
        }
        assertThrows(ArithmeticException::class.java) {
            LibraryArchivePolicy.restoreRequiredBytes(Long.MAX_VALUE)
        }
    }

    @Test
    fun `archive paths reject traversal absolute and windows separators`() {
        assertTrue(LibraryArchivePolicy.isSafeArchivePath("documents/id/raw/page_001.jpg"))
        assertFalse(LibraryArchivePolicy.isSafeArchivePath("../outside"))
        assertFalse(LibraryArchivePolicy.isSafeArchivePath("documents/id/../outside"))
        assertFalse(LibraryArchivePolicy.isSafeArchivePath("/absolute/path"))
        assertFalse(LibraryArchivePolicy.isSafeArchivePath("C:/absolute/path"))
        assertFalse(LibraryArchivePolicy.isSafeArchivePath("documents\\id\\..\\outside"))
    }

    @Test
    fun `merge titles are copied without overwriting`() {
        val used = mutableSetOf("invoice")
        assertEquals("Invoice (Restored)", LibraryArchivePolicy.uniqueRestoredTitle("Invoice", used))
        assertEquals("Invoice (Restored 2)", LibraryArchivePolicy.uniqueRestoredTitle("Invoice", used))
    }
}
