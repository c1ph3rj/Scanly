package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.TitleSuggestionOption
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import `in`.c1ph3rj.scanly.domain.repository.NamingTemplateRepository
import javax.inject.Inject

class SuggestDocumentTitleUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val namingTemplateRepository: NamingTemplateRepository,
) {
    /** The default suggestion: the default naming template if one is set, else [DocumentTitleFormat.default]. */
    suspend operator fun invoke(): String {
        val template = namingTemplateRepository.getDefaultTemplate()
        return if (template != null) {
            documentRepository.suggestDocumentTitleFromPattern(template.pattern)
        } else {
            documentRepository.suggestDocumentTitle(DocumentTitleFormat.default)
        }
    }

    suspend operator fun invoke(format: DocumentTitleFormat): String =
        documentRepository.suggestDocumentTitle(format)

    suspend operator fun invoke(option: TitleSuggestionOption): String = when (option) {
        is TitleSuggestionOption.BuiltIn -> documentRepository.suggestDocumentTitle(option.format)
        is TitleSuggestionOption.Template -> documentRepository.suggestDocumentTitleFromPattern(option.template.pattern)
    }
}
