package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    operator fun invoke(groupId: String): Flow<DocumentGroup?> =
        groupRepository.observeGroupWithStats(groupId)
}
