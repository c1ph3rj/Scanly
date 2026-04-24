package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.DocumentGroupRepository
import javax.inject.Inject

class AssignDocumentToGroupUseCase @Inject constructor(
    private val documentGroupRepository: DocumentGroupRepository,
) {
    suspend operator fun invoke(
        documentId: String,
        groupId: String?,
    ) = documentGroupRepository.assignDocumentToGroup(documentId, groupId)
}
