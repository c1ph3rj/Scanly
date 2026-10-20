package `in`.c1ph3rj.scanly.navigation

import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfToolSourceArgumentTest {

    @Test
    fun everySourceKindSurvivesTheRoute() {
        listOf(
            PdfToolSource.AppFile("/data/cache/incoming_pdf/1_Lease & terms.pdf", "Lease & terms"),
            PdfToolSource.DeviceUri("content://com.android.providers/document/42?x=1", "Invoice.pdf"),
            PdfToolSource.LibraryDocument("doc-123", "Scan 20-Oct-2026 3:30 pm"),
        ).forEach { source ->
            assertEquals(source, decodePdfToolSource(encodePdfToolSource(source)))
        }
    }

    @Test
    fun garbageDecodesToNothing() {
        assertNull(decodePdfToolSource(""))
        assertNull(decodePdfToolSource("nope"))
        assertNull(decodePdfToolSource("zip\u001Fa\u001Fb"))
    }
}
