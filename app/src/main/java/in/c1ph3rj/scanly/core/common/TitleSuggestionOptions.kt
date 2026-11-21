package `in`.c1ph3rj.scanly.core.common

import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.NamingTemplate
import `in`.c1ph3rj.scanly.domain.model.TitleSuggestionOption

/**
 * The "Suggest name" cycle for new document names. The first entry is the default suggestion
 * (used to auto-fill names): the default template when one is set, else
 * [DocumentTitleFormat.default]. Then every built-in format (default first, otherwise in enum
 * order), then the remaining templates in [templates] order.
 */
fun buildTitleSuggestionOptions(templates: List<NamingTemplate>): List<TitleSuggestionOption> {
    val defaultTemplate = templates.firstOrNull { it.isDefault }
    val builtIns = listOf(DocumentTitleFormat.default) +
        DocumentTitleFormat.entries.filter { it != DocumentTitleFormat.default }
    return buildList {
        defaultTemplate?.let { add(TitleSuggestionOption.Template(it)) }
        builtIns.forEach { add(TitleSuggestionOption.BuiltIn(it)) }
        templates
            .filter { it.id != defaultTemplate?.id }
            .distinctBy { it.id }
            .forEach { add(TitleSuggestionOption.Template(it)) }
    }
}
