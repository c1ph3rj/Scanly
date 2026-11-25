package `in`.c1ph3rj.scanly.core.common

import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.NamingTemplate
import `in`.c1ph3rj.scanly.domain.model.TitleSuggestionOption
import org.junit.Assert.assertEquals
import org.junit.Test

class TitleSuggestionOptionsTest {
    private val builtIns: List<TitleSuggestionOption> =
        DocumentTitleFormat.entries.map { TitleSuggestionOption.BuiltIn(it) }

    @Test
    fun noTemplates_isTheBuiltInsWithTheDefaultFirst() {
        val options = buildTitleSuggestionOptions(emptyList())

        assertEquals(builtIns, options)
        assertEquals(TitleSuggestionOption.BuiltIn(DocumentTitleFormat.default), options.first())
    }

    @Test
    fun templatesWithoutDefault_followTheBuiltInsInOrder() {
        val receipt = template("a", "Receipt {date}")
        val invoice = template("b", "{iso} Invoice {n}")

        val options = buildTitleSuggestionOptions(listOf(receipt, invoice))

        assertEquals(
            builtIns + TitleSuggestionOption.Template(receipt) + TitleSuggestionOption.Template(invoice),
            options,
        )
    }

    @Test
    fun defaultTemplate_comesFirstAndIsNotRepeated() {
        val receipt = template("a", "Receipt {date}")
        val invoice = template("b", "{iso} Invoice {n}", isDefault = true)
        val notes = template("c", "Notes {weekday}")

        val options = buildTitleSuggestionOptions(listOf(receipt, invoice, notes))

        assertEquals(
            listOf<TitleSuggestionOption>(TitleSuggestionOption.Template(invoice)) +
                builtIns +
                TitleSuggestionOption.Template(receipt) +
                TitleSuggestionOption.Template(notes),
            options,
        )
    }

    @Test
    fun onlyOneDefaultTemplateLeads_whenSeveralAreMarked() {
        val first = template("a", "First {n}", isDefault = true)
        val second = template("b", "Second {n}", isDefault = true)

        val options = buildTitleSuggestionOptions(listOf(first, second))

        assertEquals(TitleSuggestionOption.Template(first), options.first())
        assertEquals(TitleSuggestionOption.Template(second), options.last())
        assertEquals(builtIns.size + 2, options.size)
    }

    private fun template(id: String, pattern: String, isDefault: Boolean = false) = NamingTemplate(
        id = id,
        pattern = pattern,
        isDefault = isDefault,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )
}
