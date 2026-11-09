package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachment
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachmentLimits
import `in`.c1ph3rj.scanly.domain.repository.FeedbackAttachmentRepository
import javax.inject.Inject

class FeedbackAttachmentsUseCase @Inject constructor(private val repository: FeedbackAttachmentRepository) {
    suspend fun prepare(uri: String) = repository.prepare(uri)
    suspend fun restore(paths: List<String>) = repository.restore(paths.take(FeedbackAttachmentLimits.MAX_IMAGES))
    suspend fun validate(images: List<FeedbackAttachment>) =
        FeedbackAttachmentLimits.canSend(images) && repository.validate(images)
}
