package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

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
    fun constrainedCameraPreviewSize_usesFullWidthWithoutOverflowInPortrait() {
        val result = constrainedCameraPreviewSize(
            maxWidth = 360f,
            maxHeight = 600f,
            aspectRatio = 3f / 4f,
        )

        assertEquals(360f, result.width, 0.0001f)
        assertEquals(480f, result.height, 0.0001f)
    }

    @Test
    fun constrainedCameraPreviewSize_usesFullHeightWithoutOverflowInLandscape() {
        val result = constrainedCameraPreviewSize(
            maxWidth = 700f,
            maxHeight = 360f,
            aspectRatio = 4f / 3f,
        )

        assertEquals(480f, result.width, 0.0001f)
        assertEquals(360f, result.height, 0.0001f)
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

    @Test
    fun replacementCaptureCompletion_returnsToEditorForCapturedPage() {
        val event = replacementCompletionEvent(
            draft = captureDraft(replacementPageId = "page-7"),
            capturedPageId = "page-7",
        )

        assertEquals("page-7", event?.pageId)
    }

    @Test
    fun regularCaptureCompletion_keepsScanSessionOpen() {
        assertNull(
            replacementCompletionEvent(
                draft = captureDraft(replacementPageId = null),
                capturedPageId = "page-8",
            ),
        )
    }

    @Test
    fun copyRgbaPlane_removesRowPaddingWithBulkCopies() {
        val source = ByteBuffer.wrap(
            byteArrayOf(
                1, 2, 3, 4, 5, 6, 7, 8, 99, 99, 99, 99,
                9, 10, 11, 12, 13, 14, 15, 16, 88, 88, 88, 88,
            ),
        )

        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
            copyRgbaPlane(source, width = 2, height = 2, rowStride = 12, pixelStride = 4),
        )
    }

    private fun captureDraft(replacementPageId: String?) = PageCaptureDraft(
        pageId = replacementPageId ?: "page-8",
        documentId = "document-1",
        pageIndex = 0,
        operationId = "operation-1",
        captureFilePath = "raw.jpg",
        processedWorkingPath = "processed.jpg",
        thumbnailWorkingPath = "thumbnail.jpg",
        replacementPageId = replacementPageId,
    )
}
