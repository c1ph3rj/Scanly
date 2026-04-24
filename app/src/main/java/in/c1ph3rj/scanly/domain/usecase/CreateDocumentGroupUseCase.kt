package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.DocumentGroupRepository
import javax.inject.Inject

class CreateDocumentGroupUseCase @Inject constructor(
    private val documentGroupRepository: DocumentGroupRepository,
) {
    suspend operator fun invoke(name: String) = documentGroupRepository.createGroup(name)
}
