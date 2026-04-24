package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.DocumentGroupRepository
import javax.inject.Inject

class ObserveDocumentGroupsUseCase @Inject constructor(
    private val documentGroupRepository: DocumentGroupRepository,
) {
    operator fun invoke() = documentGroupRepository.observeGroups()
}
