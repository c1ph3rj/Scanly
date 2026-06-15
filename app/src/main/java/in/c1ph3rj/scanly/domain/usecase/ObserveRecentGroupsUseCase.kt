package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecentGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    operator fun invoke(limit: Int): Flow<List<DocumentGroup>> =
        groupRepository.observeRecentGroups(limit)
}
