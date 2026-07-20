package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ImportImagesProgress
import `in`.c1ph3rj.scanly.domain.repository.ImageImportRepository
import javax.inject.Inject

/**
 * Imports gallery images as document pages via [ImageImportRepository].
 * URI decode and JPEG normalize live in the data layer.
 */
class ImportImagesUseCase @Inject constructor(
    private val imageImportRepository: ImageImportRepository,
) {
    suspend operator fun invoke(
        documentId: String,
        imageUriStrings: List<String>,
        onProgress: suspend (ImportImagesProgress) -> Unit = {},
    ): ScanlyResult<Unit> = imageImportRepository.importImages(
        documentId = documentId,
        imageUriStrings = imageUriStrings,
        onProgress = onProgress,
    )
}
