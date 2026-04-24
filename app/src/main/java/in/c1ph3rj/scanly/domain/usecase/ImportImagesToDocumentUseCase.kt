package `in`.c1ph3rj.scanly.domain.usecase

import android.net.Uri
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ImportImagesResult
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import javax.inject.Inject

class ImportImagesToDocumentUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(
        documentId: String,
        imageUris: List<Uri>,
    ): ScanlyResult<ImportImagesResult> = pageRepository.importImages(
        documentId = documentId,
        imageUris = imageUris,
    )
}
