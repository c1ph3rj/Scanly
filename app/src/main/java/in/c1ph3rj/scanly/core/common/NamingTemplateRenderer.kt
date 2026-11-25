package `in`.c1ph3rj.scanly.core.common

import `in`.c1ph3rj.scanly.domain.model.NamingTemplateRules
import `in`.c1ph3rj.scanly.domain.model.NamingToken
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure rendering and validation of naming-template patterns. No Android dependencies. */
object NamingTemplateRenderer {
    private val tokenRegex = Regex("\\{([A-Za-z]+)\\}")

    enum class Problem { BLANK, TOO_LONG, UNKNOWN_TOKEN }

    /** Problems with [pattern], empty when it can be saved. */
    fun validate(pattern: String): Set<Problem> = buildSet {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty() || render(trimmed, 0L, Locale.ROOT, 1).isBlank()) add(Problem.BLANK)
        if (trimmed.length > NamingTemplateRules.MAX_PATTERN_LENGTH) add(Problem.TOO_LONG)
        if (unknownTokens(trimmed).isNotEmpty()) add(Problem.UNKNOWN_TOKEN)
    }

    /** `{word}` placeholders that aren't a [NamingToken], as written. */
    fun unknownTokens(pattern: String): List<String> =
        tokenRegex.findAll(pattern)
            .filter { match -> tokenFor(match.groupValues[1]) == null }
            .map { it.value }
            .distinct()
            .toList()

    fun usesNumber(pattern: String): Boolean =
        tokenRegex.findAll(pattern).any { tokenFor(it.groupValues[1]) == NamingToken.NUMBER }

    /**
     * [pattern] with tokens filled for [atMillis]; [number] fills `{n}`. Unknown tokens stay as
     * written. Whitespace is collapsed like document titles.
     */
    fun render(pattern: String, atMillis: Long, locale: Locale, number: Int): String {
        val date = Date(atMillis)
        val rendered = tokenRegex.replace(pattern) { match ->
            when (tokenFor(match.groupValues[1])) {
                NamingToken.DATE -> DateFormat.getDateInstance(DateFormat.SHORT, locale).format(date)
                NamingToken.TIME -> DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(date)
                NamingToken.DATE_TIME ->
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(date)
                NamingToken.ISO_DATE -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                NamingToken.YEAR -> SimpleDateFormat("yyyy", Locale.US).format(date)
                NamingToken.MONTH -> SimpleDateFormat("MM", Locale.US).format(date)
                NamingToken.DAY -> SimpleDateFormat("dd", Locale.US).format(date)
                NamingToken.WEEKDAY -> SimpleDateFormat("EEEE", locale).format(date)
                NamingToken.NUMBER -> number.toString()
                null -> match.value
            }
        }
        return rendered.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * A name from [pattern] not in [existingTitles] (case-insensitive). With `{n}` the number
     * counts up from 1; without it, the base name is returned and the caller's usual
     * de-duplication (`DocumentPresentationFormatter.resolveUniqueTitle`) applies.
     */
    fun renderUnique(
        pattern: String,
        existingTitles: Iterable<String>,
        atMillis: Long,
        locale: Locale,
    ): String {
        if (!usesNumber(pattern)) return render(pattern, atMillis, locale, 1)
        val taken = existingTitles.mapTo(HashSet()) { it.trim().lowercase(Locale.ROOT) }
        var n = 1
        while (true) {
            val candidate = render(pattern, atMillis, locale, n)
            if (candidate.lowercase(Locale.ROOT) !in taken || n >= MAX_NUMBER) return candidate
            n++
        }
    }

    private const val MAX_NUMBER = 100_000

    private fun tokenFor(key: String): NamingToken? =
        NamingToken.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
}
