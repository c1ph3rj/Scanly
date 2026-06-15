package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    operator fun invoke(): Flow<List<DocumentGroup>> = groupRepository.observeGroupsWithStats()
}
