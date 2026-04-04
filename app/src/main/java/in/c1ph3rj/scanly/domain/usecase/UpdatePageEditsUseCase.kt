package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import javax.inject.Inject

class UpdatePageEditsUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(
        pageId: String,
        cropQuad: DocumentCornerQuad,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        applyFilterToAllPages: Boolean,
    ): ScanlyResult<Unit> = pageRepository.updatePageEdits(
        pageId = pageId,
        cropQuad = cropQuad,
        rotationDegrees = rotationDegrees,
        filterPreset = filterPreset,
        applyFilterToAllPages = applyFilterToAllPages,
    )
}
