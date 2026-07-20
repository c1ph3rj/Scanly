package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCaptureDraftTest {

    @Test
    fun isReplacement_trueOnlyWhenReplacementPageIdSet() {
        val draft = PageCaptureDraft(
            pageId = "p1",
            documentId = "d1",
            pageIndex = 2,
            rawImagePath = "/r",
            processedImagePath = "/p",
            thumbnailPath = "/t",
        )
        assertFalse(draft.isReplacement)
        assertTrue(draft.copy(replacementPageId = "p1").isReplacement)
    }
}
