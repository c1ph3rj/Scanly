package `in`.c1ph3rj.scanly.data.naming

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.NamingTemplateRenderer
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.NamingTemplateDao
import `in`.c1ph3rj.scanly.data.local.db.entity.NamingTemplateEntity
import `in`.c1ph3rj.scanly.domain.model.NamingTemplate
import `in`.c1ph3rj.scanly.domain.repository.NamingTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved name patterns in `naming_templates`. Patterns are stored trimmed and must pass
 * [NamingTemplateRenderer.validate]; two templates can't share a pattern (ignoring case). At most
 * one is the default; deleting it falls back to the built-in suggestion.
 */
@Singleton
class DefaultNamingTemplateRepository @Inject constructor(
    private val database: ScanlyDatabase,
    private val namingTemplateDao: NamingTemplateDao,
    private val dispatchers: ScanlyDispatchers,
) : NamingTemplateRepository {

    override fun observeTemplates(): Flow<List<NamingTemplate>> =
        namingTemplateDao.observeTemplates().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun getDefaultTemplate(): NamingTemplate? = withContext(dispatchers.io) {
        namingTemplateDao.getDefaultTemplate()?.toDomain()
    }

    override suspend fun saveTemplate(id: String?, pattern: String): ScanlyResult<NamingTemplate> = mutate {
        val trimmed = pattern.trim()
        val problems = NamingTemplateRenderer.validate(trimmed)
        require(problems.isEmpty()) { "Invalid naming template: $problems" }
        database.withTransaction {
            val clash = namingTemplateDao.getTemplates().any { template ->
                template.id != id && template.pattern.equals(trimmed, ignoreCase = true)
            }
            require(!clash) { "Another template already uses this pattern" }
            val now = System.currentTimeMillis()
            if (id == null) {
                val template = NamingTemplateEntity(
                    id = UUID.randomUUID().toString(),
                    pattern = trimmed,
                    isDefault = false,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
                namingTemplateDao.insert(template)
                template.toDomain()
            } else {
                val existing = requireNotNull(namingTemplateDao.getTemplate(id)) { "Template $id not found" }
                val updated = existing.copy(pattern = trimmed, updatedAtMillis = now)
                namingTemplateDao.update(updated)
                updated.toDomain()
            }
        }
    }

    override suspend fun deleteTemplate(id: String): ScanlyResult<Unit> = mutate {
        namingTemplateDao.delete(id)
    }

    override suspend fun setDefaultTemplate(id: String?): ScanlyResult<Unit> = mutate {
        database.withTransaction {
            if (id != null) {
                requireNotNull(namingTemplateDao.getTemplate(id)) { "Template $id not found" }
            }
            namingTemplateDao.clearDefault()
            if (id != null) namingTemplateDao.markDefault(id)
            Unit
        }
    }

    private suspend fun <T> mutate(block: suspend () -> T): ScanlyResult<T> = withContext(dispatchers.io) {
        runCatching { block() }.fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    // Repository messages are for logs; the UI validates patterns itself and shows its own text.
                    ScanlyError(
                        message = appString(R.string.default_page_text_repository_could_not_save_your_changes),
                        cause = throwable,
                    ),
                )
            },
        )
    }
}

private fun NamingTemplateEntity.toDomain(): NamingTemplate = NamingTemplate(
    id = id,
    pattern = pattern,
    isDefault = isDefault,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
