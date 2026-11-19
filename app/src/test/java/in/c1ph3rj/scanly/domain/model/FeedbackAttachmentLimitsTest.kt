package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.*
import org.junit.Test

class FeedbackAttachmentLimitsTest {
    @Test fun acceptsEmptyDraftAndExactFiveMegabyteBoundary() {
        assertTrue(FeedbackAttachmentLimits.canSend(emptyList()))
        assertTrue(FeedbackAttachmentLimits.canSend((1..5).map { FeedbackAttachment("$it", 1_000_000, "image/png") }))
    }

    @Test fun rejectsExcessCountOversizeEmptyAndDuplicateFiles() {
        val valid = FeedbackAttachment("one", 999_999, "image/jpeg")
        assertFalse(FeedbackAttachmentLimits.canSend((1..6).map { valid.copy(path = "$it") }))
        assertFalse(FeedbackAttachmentLimits.canSend(listOf(valid.copy(sizeBytes = 1_000_001))))
        assertFalse(FeedbackAttachmentLimits.canSend(listOf(valid.copy(sizeBytes = 0))))
        assertFalse(FeedbackAttachmentLimits.canSend(listOf(valid, valid)))
    }
}
