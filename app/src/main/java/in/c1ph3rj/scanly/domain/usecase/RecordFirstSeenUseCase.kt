package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.RatingPromptRepository

class RecordFirstSeenUseCase(
    private val repository: RatingPromptRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke() {
        repository.recordFirstSeenIfAbsent(nowMillis())
    }
}
