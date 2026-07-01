package `in`.c1ph3rj.scanly.domain.model

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad

data class PageCaptureDraft(
    val pageId: String,
    val documentId: String,
    val pageIndex: Int,
    val operationId: String,
    val captureFilePath: String,
    val processedWorkingPath: String,
    val thumbnailWorkingPath: String,
    val detectedCropQuad: DocumentCornerQuad? = null,
    val replacementPageId: String? = null,
) {
    val isReplacement: Boolean
        get() = replacementPageId != null
}
