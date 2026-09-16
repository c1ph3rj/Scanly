package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.model.ExportDestination
import `in`.c1ph3rj.scanly.domain.model.SettingsContent
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemorySettingsRepository : SettingsRepository {
    private val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val pureBlackEnabled = MutableStateFlow(false)
    private val onboardingCompleted = MutableStateFlow(false)
    private val exportDestination = MutableStateFlow<ExportDestination>(
        ExportDestination.DefaultDownloadsScanly,
    )
    private val liveDetectionModel = MutableStateFlow(DocumentCornerModel.fromStorage(null))
    private val postProcessingModel = MutableStateFlow(DocumentCornerModel.fromStorage(null))
    private val automaticModelSelection = MutableStateFlow(true)
    private val documentGateEnabled = MutableStateFlow(true)

    override fun observeThemeMode(): Flow<ThemeMode> = themeMode.asStateFlow()

    override suspend fun setThemeMode(themeMode: ThemeMode): ScanlyResult<Unit> =
        runLibraryResult("Could not update theme mode.") {
            this.themeMode.value = themeMode
        }

    override fun observePureBlackEnabled(): Flow<Boolean> = pureBlackEnabled.asStateFlow()

    override suspend fun setPureBlackEnabled(enabled: Boolean): ScanlyResult<Unit> =
        runLibraryResult("Could not update pure black.") {
            pureBlackEnabled.value = enabled
        }

    override fun observeOnboardingCompleted(): Flow<Boolean> = onboardingCompleted.asStateFlow()

    override suspend fun completeOnboarding(): ScanlyResult<Unit> =
        runLibraryResult("Could not finish onboarding.") {
            onboardingCompleted.value = true
        }

    override suspend fun loadSettingsContent(): ScanlyResult<SettingsContent> =
        runLibraryResult("Could not load settings content.") {
            SettingsContent(
                faqs = emptyList(),
                licenses = emptyList(),
                developerWebsite = "https://scanly.app",
                appVersionLabel = "1.0.16",
            )
        }

    override fun observeExportDestination(): Flow<ExportDestination> =
        exportDestination.asStateFlow()

    override suspend fun setExportDestination(
        destination: ExportDestination.CustomTree,
    ): ScanlyResult<Unit> = runLibraryResult("Could not set export destination.") {
        exportDestination.value = destination
    }

    override suspend fun resetExportDestination(): ScanlyResult<Unit> =
        runLibraryResult("Could not reset export destination.") {
            exportDestination.value = ExportDestination.DefaultDownloadsScanly
        }

    override fun observeLiveDetectionModel(): Flow<DocumentCornerModel> =
        liveDetectionModel.asStateFlow()

    override suspend fun setLiveDetectionModel(model: DocumentCornerModel): ScanlyResult<Unit> =
        runLibraryResult("Could not set live model.") {
            liveDetectionModel.value = model
        }

    override fun observePostProcessingModel(): Flow<DocumentCornerModel> =
        postProcessingModel.asStateFlow()

    override suspend fun setPostProcessingModel(model: DocumentCornerModel): ScanlyResult<Unit> =
        runLibraryResult("Could not set post model.") {
            postProcessingModel.value = model
        }

    override suspend fun getPostProcessingModel(): DocumentCornerModel = postProcessingModel.value

    override fun observeAutomaticModelSelection(): Flow<Boolean> =
        automaticModelSelection.asStateFlow()

    override suspend fun setAutomaticModelSelection(enabled: Boolean): ScanlyResult<Unit> =
        runLibraryResult("Could not set automatic model selection.") {
            automaticModelSelection.value = enabled
        }

    override suspend fun getAutomaticModelSelection(): Boolean = automaticModelSelection.value

    override fun observeDocumentGateEnabled(): Flow<Boolean> = documentGateEnabled.asStateFlow()

    override suspend fun setDocumentGateEnabled(enabled: Boolean): ScanlyResult<Unit> =
        runLibraryResult("Could not set document gate.") {
            documentGateEnabled.value = enabled
        }

    override suspend fun getDocumentGateEnabled(): Boolean = documentGateEnabled.value
}
