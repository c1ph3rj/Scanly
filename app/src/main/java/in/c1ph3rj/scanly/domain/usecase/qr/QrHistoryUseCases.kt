package `in`.c1ph3rj.scanly.domain.usecase.qr

import `in`.c1ph3rj.scanly.domain.model.QrHistoryEntry
import `in`.c1ph3rj.scanly.domain.model.QrOrigin
import `in`.c1ph3rj.scanly.domain.repository.QrHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveQrHistoryUseCase @Inject constructor(
    private val repository: QrHistoryRepository,
) {
    operator fun invoke(): Flow<List<QrHistoryEntry>> = repository.observe()
}

class AddQrHistoryEntryUseCase @Inject constructor(
    private val repository: QrHistoryRepository,
) {
    suspend operator fun invoke(raw: String, origin: QrOrigin): QrHistoryEntry? =
        if (raw.isBlank()) null else repository.add(raw, origin)
}

class DeleteQrHistoryEntryUseCase @Inject constructor(
    private val repository: QrHistoryRepository,
) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}

class ClearQrHistoryUseCase @Inject constructor(
    private val repository: QrHistoryRepository,
) {
    suspend operator fun invoke() = repository.clear()
}
