package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import `in`.c1ph3rj.scanly.domain.repository.NamingTemplateRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class CreateDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val namingTemplateRepository: NamingTemplateRepository,
) {
    /**
     * Creates an empty document. A non-default [scanMode] is stored as the document's preferred
     * mode so the camera opens in it; failing to store it never fails the create.
     */
    suspend operator fun invoke(
        title: String,
        groupId: String? = null,
        scanMode: ScanMode = ScanMode.DOCUMENT,
    ): ScanlyResult<String> {
        val created = documentRepository.createDocument(title, groupId)
        if (created is ScanlyResult.Success && scanMode != ScanMode.DOCUMENT) {
            documentRepository.setPreferredScanMode(created.value, scanMode)
        }
        return created
    }

    /**
     * Creates an empty document for imported or shared images. It is named with the default naming
     * template when one is set (as Settings › Naming templates promises for every new document),
     * otherwise "Imported <date>".
     */
    suspend fun createImported(groupId: String? = null): ScanlyResult<String> {
        val pattern = try {
            namingTemplateRepository.getDefaultTemplate()?.pattern
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A template that can't be read must not block the import; fall back to the usual name.
            null
        }
        return if (pattern == null) {
            documentRepository.createImportedDocument(groupId)
        } else {
            documentRepository.createDocument(documentRepository.suggestDocumentTitleFromPattern(pattern), groupId)
        }
    }
}
