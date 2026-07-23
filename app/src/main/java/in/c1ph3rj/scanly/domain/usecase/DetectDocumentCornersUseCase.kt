package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.processing.PageImageProcessor
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import javax.inject.Inject

/**
 * Runs still-image AI document detection and returns a crop quad in rotated image space.
 */
class DetectDocumentCornersUseCase @Inject constructor(
    private val pageImageProcessor: PageImageProcessor,
) {
    suspend operator fun invoke(
        rawImagePath: String,
        rotationDegrees: Int,
        scanMode: ScanMode = ScanMode.DOCUMENT,
    ): ScanlyResult<DocumentCornerQuad> {
        return runCatching {
            pageImageProcessor.detectDocumentCorners(
                rawImagePath = rawImagePath,
                rotationDegrees = rotationDegrees,
                scanMode = scanMode,
            ) ?: throw NoDocumentDetectedException()
        }.fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = when (throwable) {
                            is NoDocumentDetectedException ->
                                "Could not detect a document in this image."
                            else ->
                                throwable.message ?: "Document detection failed."
                        },
                        cause = throwable,
                    ),
                )
            },
        )
    }

    private class NoDocumentDetectedException : Exception()
}
