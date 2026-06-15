package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveGroupDocumentsUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    operator fun invoke(groupId: String): Flow<List<ScanDocument>> =
        groupRepository.observeGroupDocuments(groupId)
}
