package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.DocumentGroupRepository
import javax.inject.Inject

class RenameDocumentGroupUseCase @Inject constructor(
    private val documentGroupRepository: DocumentGroupRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        name: String,
    ) = documentGroupRepository.renameGroup(groupId, name)
}
