package `in`.c1ph3rj.scanly.feature.tools.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrToolPresentationTest {
    @Test
    fun webLinksAreDetectedWithoutReimplementingTheScreen() {
        assertTrue(isQrWebLink("https://scanly.app"))
        assertTrue(isQrWebLink("  HTTP://example.com/path  "))
        assertFalse(isQrWebLink("WIFI:T:WPA;S:office;;"))
        assertFalse(isQrWebLink("hello"))
    }

    @Test
    fun resultCopyDistinguishesLinkAndText() {
        assertEquals("Link", formatQrScanResultTitle("https://scanly.app"))
        assertEquals("Open in a browser or copy", formatQrScanResultSubtitle("https://scanly.app"))
        assertEquals("Text", formatQrScanResultTitle("Invoice 1042"))
        assertEquals("Copy to the clipboard", formatQrScanResultSubtitle("Invoice 1042"))
    }

    @Test
    fun generatePlaceholderFollowsContent() {
        assertEquals("Preview appears as you type", formatQrGeneratePlaceholderHint(true))
        assertEquals("Generating preview…", formatQrGeneratePlaceholderHint(false))
    }

    @Test
    fun landscapePermissionGateKeepsModeSelectorReachable() {
        assertTrue(qrPermissionGateShowsModeSelector(twoPane = true))
        assertFalse(qrPermissionGateShowsModeSelector(twoPane = false))
    }
}
