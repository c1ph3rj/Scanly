package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import javax.inject.Inject

class SuggestGroupTitleUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(
        format: GroupTitleFormat = GroupTitleFormat.default,
    ): String = groupRepository.suggestGroupTitle(format)
}