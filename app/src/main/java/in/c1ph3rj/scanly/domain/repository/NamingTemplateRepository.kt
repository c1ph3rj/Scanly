package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.NamingTemplate
import kotlinx.coroutines.flow.Flow

interface NamingTemplateRepository {
    /** Saved templates, oldest first. */
    fun observeTemplates(): Flow<List<NamingTemplate>>

    suspend fun getDefaultTemplate(): NamingTemplate?

    /**
     * Creates ([id] null) or updates a template. Fails when the pattern doesn't pass
     * `NamingTemplateRenderer.validate`, or another template already has the same pattern.
     */
    suspend fun saveTemplate(id: String?, pattern: String): ScanlyResult<NamingTemplate>

    suspend fun deleteTemplate(id: String): ScanlyResult<Unit>

    /** Makes [id] the default, or clears the default (back to the built-in) when null. */
    suspend fun setDefaultTemplate(id: String?): ScanlyResult<Unit>
}
