package `in`.c1ph3rj.scanly.domain.model

data class FeedbackAttachment(val path: String, val sizeBytes: Long, val mimeType: String)

object FeedbackAttachmentLimits {
    const val MAX_IMAGES = 5
    const val MAX_IMAGE_BYTES = 1_000_000L
    const val MAX_TOTAL_BYTES = MAX_IMAGES * MAX_IMAGE_BYTES
    const val MAX_SOURCE_BYTES = 40_000_000L
    const val MAX_EDGE = 2048

    fun canSend(images: List<FeedbackAttachment>): Boolean = images.size <= MAX_IMAGES &&
        images.map { it.path }.distinct().size == images.size &&
        images.all { it.sizeBytes in 1..MAX_IMAGE_BYTES } &&
        images.sumOf { it.sizeBytes } <= MAX_TOTAL_BYTES
}
