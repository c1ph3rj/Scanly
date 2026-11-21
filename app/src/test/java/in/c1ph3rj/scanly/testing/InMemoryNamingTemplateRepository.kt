package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.NamingTemplateRenderer
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.NamingTemplate
import `in`.c1ph3rj.scanly.domain.repository.NamingTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Mirrors `DefaultNamingTemplateRepository`: patterns are trimmed and validated with
 * [NamingTemplateRenderer.validate], duplicate patterns (ignoring case) are rejected, at most one
 * template is the default, and templates are listed oldest first.
 */
class InMemoryNamingTemplateRepository(
    initial: List<NamingTemplate> = emptyList(),
    private val now: () -> Long = defaultClock(),
) : NamingTemplateRepository {
    private val templates = MutableStateFlow(initial.sortedWith(oldestFirst))
    private var nextId = 1

    /** Current templates, oldest first. */
    val current: List<NamingTemplate> get() = templates.value

    override fun observeTemplates(): Flow<List<NamingTemplate>> = templates.asStateFlow()

    override suspend fun getDefaultTemplate(): NamingTemplate? = templates.value.firstOrNull { it.isDefault }

    override suspend fun saveTemplate(id: String?, pattern: String): ScanlyResult<NamingTemplate> =
        runLibraryResult(FAILURE) {
            val trimmed = pattern.trim()
            require(NamingTemplateRenderer.validate(trimmed).isEmpty()) { FAILURE }
            require(templates.value.none { it.id != id && it.pattern.equals(trimmed, ignoreCase = true) }) { FAILURE }
            val timestamp = now()
            val saved = if (id == null) {
                NamingTemplate(
                    id = "template-${nextId++}",
                    pattern = trimmed,
                    isDefault = false,
                    createdAtMillis = timestamp,
                    updatedAtMillis = timestamp,
                )
            } else {
                val existing = requireNotNull(templates.value.firstOrNull { it.id == id }) { FAILURE }
                existing.copy(pattern = trimmed, updatedAtMillis = timestamp)
            }
            templates.value = (templates.value.filterNot { it.id == saved.id } + saved).sortedWith(oldestFirst)
            saved
        }

    override suspend fun deleteTemplate(id: String): ScanlyResult<Unit> = runLibraryResult(FAILURE) {
        templates.value = templates.value.filterNot { it.id == id }
    }

    override suspend fun setDefaultTemplate(id: String?): ScanlyResult<Unit> = runLibraryResult(FAILURE) {
        if (id != null) requireNotNull(templates.value.firstOrNull { it.id == id }) { FAILURE }
        templates.value = templates.value.map { it.copy(isDefault = it.id == id) }
    }

    private companion object {
        const val FAILURE = "Could not save your changes."

        val oldestFirst = compareBy<NamingTemplate>({ it.createdAtMillis }, { it.id })

        fun defaultClock(): () -> Long {
            val clock = AtomicLong(1_700_000_000_000L)
            return { clock.getAndAdd(1_000L) }
        }
    }
}
