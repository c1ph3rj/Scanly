package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.processing.PageImageProcessor
import javax.inject.Inject
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString

/**
 * Runs still-image AI document detection and returns a crop quad in rotated image space.
 */
class DetectDocumentCornersUseCase @Inject constructor(
    private val pageImageProcessor: PageImageProcessor,
) {
    suspend operator fun invoke(
        rawImagePath: String,
        rotationDegrees: Int,
    ): ScanlyResult<DocumentCornerQuad> {
        return runCatching {
            pageImageProcessor.detectDocumentCorners(
                rawImagePath = rawImagePath,
                rotationDegrees = rotationDegrees,
            ) ?: throw NoDocumentDetectedException()
        }.fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = when (throwable) {
                            is NoDocumentDetectedException ->
                                appString(R.string.detect_document_corners_use_case_could_not_detect_a_document)
                            else ->
                                throwable.message ?: appString(R.string.detect_document_corners_use_case_document_detection_failed)
                        },
                        cause = throwable,
                    ),
                )
            },
        )
    }

    private class NoDocumentDetectedException : Exception()
}
