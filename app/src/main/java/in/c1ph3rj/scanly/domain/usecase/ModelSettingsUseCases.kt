package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
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
