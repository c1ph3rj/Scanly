package `in`.c1ph3rj.scanly.domain.usecase

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.domain.model.SignatureSource
import `in`.c1ph3rj.scanly.domain.repository.SignatureRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSignaturesUseCase @Inject constructor(
    private val repository: SignatureRepository,
) {
    operator fun invoke(): Flow<List<SavedSignature>> = repository.observeSignatures()
}

class SaveSignatureUseCase @Inject constructor(
    private val repository: SignatureRepository,
) {
    suspend operator fun invoke(
        inkMask: Bitmap,
        kind: SignatureKind,
        source: SignatureSource,
    ): ScanlyResult<SavedSignature> = repository.saveSignature(inkMask, kind, source)
}

class ExtractSignatureInkUseCase @Inject constructor(
    private val repository: SignatureRepository,
) {
    suspend operator fun invoke(uriString: String, sensitivity: Float): ScanlyResult<Bitmap> =
        repository.extractInk(uriString, sensitivity)
}

class DeleteSignatureUseCase @Inject constructor(
    private val repository: SignatureRepository,
) {
    suspend operator fun invoke(id: String): ScanlyResult<Unit> = repository.deleteSignature(id)
}
