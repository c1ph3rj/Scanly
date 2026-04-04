package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDocumentsUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
) {
    operator fun invoke(): Flow<List<ScanDocument>> =
        documentRepository.observeDocuments()
}
