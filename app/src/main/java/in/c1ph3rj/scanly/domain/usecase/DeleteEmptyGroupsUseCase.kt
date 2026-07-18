package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import javax.inject.Inject

class DeleteEmptyGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    /** @return number of empty folders/groups removed */
    suspend operator fun invoke(): ScanlyResult<Int> =
        groupRepository.deleteEmptyGroups()
}
