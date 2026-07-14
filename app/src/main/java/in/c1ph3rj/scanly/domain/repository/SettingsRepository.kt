package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.SettingsContent
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.domain.model.ExportDestination
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeThemeMode(): Flow<ThemeMode>

    suspend fun setThemeMode(themeMode: ThemeMode): ScanlyResult<Unit>

    fun observePureBlackEnabled(): Flow<Boolean>

    suspend fun setPureBlackEnabled(enabled: Boolean): ScanlyResult<Unit>

    fun observeOnboardingCompleted(): Flow<Boolean>

    suspend fun completeOnboarding(): ScanlyResult<Unit>

    suspend fun loadSettingsContent(): ScanlyResult<SettingsContent>

    fun observeExportDestination(): Flow<ExportDestination>

    suspend fun setExportDestination(destination: ExportDestination.CustomTree): ScanlyResult<Unit>

    suspend fun resetExportDestination(): ScanlyResult<Unit>

    fun observeLiveDetectionModel(): Flow<DocumentCornerModel>

    suspend fun setLiveDetectionModel(model: DocumentCornerModel): ScanlyResult<Unit>

    fun observePostProcessingModel(): Flow<DocumentCornerModel>

    suspend fun setPostProcessingModel(model: DocumentCornerModel): ScanlyResult<Unit>

    suspend fun getPostProcessingModel(): DocumentCornerModel

    fun observeAutomaticModelSelection(): Flow<Boolean>

    suspend fun setAutomaticModelSelection(enabled: Boolean): ScanlyResult<Unit>

    suspend fun getAutomaticModelSelection(): Boolean

    fun observeDocumentGateEnabled(): Flow<Boolean>

    suspend fun setDocumentGateEnabled(enabled: Boolean): ScanlyResult<Unit>

    suspend fun getDocumentGateEnabled(): Boolean
}
