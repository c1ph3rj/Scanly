package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import `in`.c1ph3rj.scanly.domain.repository.OcrEngineStatusRepository
import `in`.c1ph3rj.scanly.domain.repository.SettingsRepository
import javax.inject.Inject

class ObserveLiveDetectionModelUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke() = repository.observeLiveDetectionModel()
}

class SetLiveDetectionModelUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(model: DocumentCornerModel) = repository.setLiveDetectionModel(model)
}

class ObservePostProcessingModelUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke() = repository.observePostProcessingModel()
}

class SetPostProcessingModelUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(model: DocumentCornerModel) = repository.setPostProcessingModel(model)
}

class ObserveAutomaticModelSelectionUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke() = repository.observeAutomaticModelSelection()
}

class SetAutomaticModelSelectionUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setAutomaticModelSelection(enabled)
}

class ObserveDocumentGateEnabledUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke() = repository.observeDocumentGateEnabled()
}

class SetDocumentGateEnabledUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setDocumentGateEnabled(enabled)
}

class ObserveOcrEngineUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke() = repository.observeOcrEngine()
}

class SetOcrEngineUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(engine: OcrEngine) = repository.setOcrEngine(engine)
}

class ObserveIndraSuspendedUseCase @Inject constructor(private val repository: OcrEngineStatusRepository) {
    operator fun invoke() = repository.observeIndraSuspended()
}

class ResetIndraUseCase @Inject constructor(private val repository: OcrEngineStatusRepository) {
    suspend operator fun invoke() = repository.resetIndra()
}
