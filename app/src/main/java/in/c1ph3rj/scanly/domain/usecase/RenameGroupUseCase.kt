package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import javax.inject.Inject

class RenameGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String, title: String): ScanlyResult<Unit> =
        groupRepository.renameGroup(groupId, title)
}
