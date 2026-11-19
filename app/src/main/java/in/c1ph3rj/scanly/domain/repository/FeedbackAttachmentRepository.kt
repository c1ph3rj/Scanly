package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachment

interface FeedbackAttachmentRepository {
    suspend fun prepare(uri: String): ScanlyResult<FeedbackAttachment>
    suspend fun restore(paths: List<String>): List<FeedbackAttachment>
    suspend fun validate(images: List<FeedbackAttachment>): Boolean
}
