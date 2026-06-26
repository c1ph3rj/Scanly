package `in`.c1ph3rj.scanly.feature.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanSessionScreenTest {
    @Test
    fun cameraPreviewAspectRatio_usesPortraitPreviewInPortrait() {
        assertEquals(3f / 4f, cameraPreviewAspectRatio(isLandscape = false), 0.0001f)
    }

    @Test
    fun cameraPreviewAspectRatio_usesLandscapePreviewInLandscape() {
        assertEquals(4f / 3f, cameraPreviewAspectRatio(isLandscape = true), 0.0001f)
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
