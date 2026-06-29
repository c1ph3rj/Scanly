package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.SettingsContent
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeThemeMode(): Flow<ThemeMode>

    suspend fun setThemeMode(themeMode: ThemeMode): ScanlyResult<Unit>

    fun observeOnboardingCompleted(): Flow<Boolean>

    suspend fun completeOnboarding(): ScanlyResult<Unit>

    suspend fun loadSettingsContent(): ScanlyResult<SettingsContent>
}
