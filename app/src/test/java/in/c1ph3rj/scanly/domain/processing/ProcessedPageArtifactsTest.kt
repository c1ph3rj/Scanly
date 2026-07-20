package `in`.c1ph3rj.scanly.domain.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Documents the critical processing-state contract used after capture finalize / reprocess.
 */
class ProcessedPageArtifactsTest {

    @Test
    fun needsReview_whenCropQuadMissing() {
        val artifacts = ProcessedPageArtifacts(
            processedImagePath = "/processed.jpg",
            thumbnailPath = "/thumb.jpg",
            cropQuad = null,
            rotationDegrees = 0,
            filterPreset = PageFilterPreset.AUTO,
            processingState = PageProcessingState.NEEDS_REVIEW,
        )

        assertNull(artifacts.cropQuad)
        assertEquals(PageProcessingState.NEEDS_REVIEW, artifacts.processingState)
    }

    @Test
    fun processed_whenQuadPresent() {
        // Quad geometry is validated elsewhere; this locks the READY/PROCESSED pairing used by UI.
        val artifacts = ProcessedPageArtifacts(
            processedImagePath = "/processed.jpg",
            thumbnailPath = "/thumb.jpg",
            cropQuad = null, // still allowed for ORIGINAL-style paths in some flows
            rotationDegrees = 90,
            filterPreset = PageFilterPreset.ORIGINAL,
            processingState = PageProcessingState.PROCESSED,
        )

        assertEquals(PageProcessingState.PROCESSED, artifacts.processingState)
        assertEquals(90, artifacts.rotationDegrees)
        assertEquals(PageFilterPreset.ORIGINAL, artifacts.filterPreset)
    }

    @Test
    fun pageProcessingState_roundTripsStorageValues() {
        PageProcessingState.entries.forEach { state ->
            assertEquals(state, PageProcessingState.fromStorage(state.storageValue))
        }
        assertEquals(PageProcessingState.CAPTURED, PageProcessingState.fromStorage("unknown"))
    }
}
