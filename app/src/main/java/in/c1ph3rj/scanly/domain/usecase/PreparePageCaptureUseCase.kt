package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.IdCardSide
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import javax.inject.Inject

class PreparePageCaptureUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    /**
     * Reserves files for a new page captured in [scanMode]. ID card pages also carry the card
     * they belong to ([idCardPairId]) and which side they are ([idCardSide]).
     */
    suspend operator fun invoke(
        documentId: String,
        scanMode: ScanMode = ScanMode.DOCUMENT,
        idCardPairId: String? = null,
        idCardSide: IdCardSide? = null,
    ): ScanlyResult<PageCaptureDraft> = when (val prepared = pageRepository.prepareCapture(documentId)) {
        is ScanlyResult.Success -> ScanlyResult.Success(
            prepared.value.copy(
                scanMode = scanMode,
                idCardPairId = idCardPairId.takeIf { scanMode == ScanMode.ID_CARD },
                idCardSide = idCardSide.takeIf { scanMode == ScanMode.ID_CARD },
            ),
        )
        is ScanlyResult.Failure -> prepared
    }
}
