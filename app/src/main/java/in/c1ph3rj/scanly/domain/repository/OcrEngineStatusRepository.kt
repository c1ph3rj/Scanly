package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import kotlinx.coroutines.flow.Flow

/** Health of the Indra OCR engine, so Settings can explain why pages use ML Kit. */
interface OcrEngineStatusRepository {
    /** Engine used until the user picks one: Indra when this build ships its models, else ML Kit. */
    fun defaultEngine(): OcrEngine

    /** True while Indra is paused because it stopped the app unexpectedly more than once. */
    fun observeIndraSuspended(): Flow<Boolean>

    /** Clears the pause and the pages marked as crashing, so Indra is tried again. */
    suspend fun resetIndra()
}
