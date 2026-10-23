package `in`.c1ph3rj.scanly.data.text

import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.domain.repository.OcrEngineStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultOcrEngineStatusRepository @Inject constructor(
    private val guard: IndraCrashGuard,
    private val recognizer: SelectablePageTextRecognizer,
    private val dispatchers: ScanlyDispatchers,
) : OcrEngineStatusRepository {

    override fun observeIndraSuspended(): Flow<Boolean> = guard.observeSuspended()

    /** Also reopens this session's circuit breaker, so selecting Indra again really retries it. */
    override suspend fun resetIndra() {
        withContext(dispatchers.io) {
            guard.reset()
            recognizer.resetBreaker()
        }
    }
}
