package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.NamingTemplate
import `in`.c1ph3rj.scanly.domain.repository.NamingTemplateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveNamingTemplatesUseCase @Inject constructor(private val repository: NamingTemplateRepository) {
    operator fun invoke(): Flow<List<NamingTemplate>> = repository.observeTemplates()
}

class SaveNamingTemplateUseCase @Inject constructor(private val repository: NamingTemplateRepository) {
    suspend operator fun invoke(id: String?, pattern: String): ScanlyResult<NamingTemplate> =
        repository.saveTemplate(id, pattern)
}

class DeleteNamingTemplateUseCase @Inject constructor(private val repository: NamingTemplateRepository) {
    suspend operator fun invoke(id: String): ScanlyResult<Unit> = repository.deleteTemplate(id)
}

class SetDefaultNamingTemplateUseCase @Inject constructor(private val repository: NamingTemplateRepository) {
    suspend operator fun invoke(id: String?): ScanlyResult<Unit> = repository.setDefaultTemplate(id)
}
