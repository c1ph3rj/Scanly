package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.domain.model.PipPreference
import `in`.c1ph3rj.scanly.domain.model.PipPreferences
import kotlinx.coroutines.flow.Flow

interface PipRepository {
    fun observePreferences(): Flow<PipPreferences>

    suspend fun setPreference(preference: PipPreference, enabled: Boolean)

    suspend fun dismissTip(epochDay: Long)
}
