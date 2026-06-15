package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import javax.inject.Inject

class SetDocumentGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(documentId: String, groupId: String?): ScanlyResult<Unit> =
        groupRepository.setDocumentGroup(documentId, groupId)
}
