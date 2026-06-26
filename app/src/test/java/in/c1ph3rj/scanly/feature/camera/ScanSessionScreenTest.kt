package `in`.c1ph3rj.scanly.feature.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanSessionScreenTest {
    @Test
    fun cameraPreviewAspectRatio_usesSquarePreview() {
        assertEquals(1f, cameraPreviewAspectRatio(), 0.0001f)
    }

    @Test
    fun scanSessionRoute_withoutReplacement_targetsDocumentCapture() {
        assertEquals(
            "camera/session/document-1",
            ScanSessionDestination.route(documentId = "document-1"),
        )
    }

    @Test
    fun scanSessionRoute_withReplacement_targetsPageRetakeCapture() {
        assertEquals(
            "camera/session/document-1?replacePageId=page-7",
            ScanSessionDestination.route(documentId = "document-1", replacePageId = "page-7"),
        )
    }
}
