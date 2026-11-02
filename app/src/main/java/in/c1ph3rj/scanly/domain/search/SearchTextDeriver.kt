package `in`.c1ph3rj.scanly.domain.search

import java.util.Locale

/** A searchable form of part of a text, e.g. "1250" for "1,250", and where it came from. */
data class DerivedToken(
    /** Range in the source text this token stands for. */
    val sourceRange: IntRange,
    /** Folded letters and digits only, so it is one index token. */
    val value: String,
    val kind: Kind,
) {
    enum class Kind {
        /** "agree-\nment" → "agreement", "e-mail" → "email": matched as a word prefix. */
        JOINED_WORD,

        /** "1,250.00" → "125000", "1250"; "98 400 12345" → "9840012345": matched exactly. */
        NUMBER,

        /** Any written date → "d20261102" (yyyymmdd): matched exactly. */
        DATE,

        /** A non-Latin word's loose English-spelling key ("சென்னை" → "trsn"): matched exactly. */
        TRANSLITERATED,
    }
}

/**
 * Extra forms of page text that the index stores beside the words themselves (the
 * `derivedText` column), so a search reaches text written differently from how it is typed:
 * words broken across lines or by hyphens, numbers with separators, and dates in any format.
 * The same deriver runs on the query, so both sides agree. With a [romanizer], words in other
 * scripts also get a loose English-spelling key (see [LooseRomanization]).
 */
object SearchTextDeriver {
    /** Bump when the output changes; stored rows below it are re-derived in the background. */
    const val VERSION = 2

    /**
     * Romanizes non-Latin words for English-spelling search; set once at app start (the
     * platform's ICU), left null in plain JVM tests.
     */
    @Volatile
    var romanizer: Romanizer? = null

    private const val WORD = "[\\p{L}\\p{M}\\p{N}]+"
    private val joinedWord = Regex("($WORD)-(?:[ \\t]*\\r?\\n[ \\t]*)?($WORD)")
    private val groupedNumber = Regex("(?<![\\d,.])\\d{1,3}(?:[,.]\\d{2,3})+(?:[.,]\\d{1,2})?(?![\\d,.]*\\d)")
    private val digitRun = Regex("(?<![\\d])\\d{2,}(?:[ \\-.]\\d{2,})+(?![\\d])")
    private val numericDate = Regex("(?<!\\d)(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}|\\d{2})(?!\\d)")
    private val isoDate = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
    private const val MONTH =
        "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|" +
            "sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private val dayMonthYear = Regex(
        "(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?[ \\-]+$MONTH\\.?[ ,\\-]+(\\d{4})(?!\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val monthDayYear = Regex(
        "(?<![\\p{L}])$MONTH\\.?[ \\-]+(\\d{1,2})(?:st|nd|rd|th)?,?[ \\-]+(\\d{4})(?!\\d)",
        RegexOption.IGNORE_CASE,
    )
    private const val MIN_DIGIT_RUN = 7

    /** Shorter keys ("s") would make an English word match half the library. */
    private const val MIN_TRANSLITERATION_KEY = 2
    private const val TWO_DIGIT_YEAR_PIVOT = 70

    fun derive(text: String): List<DerivedToken> = buildList {
        romanizer?.let { romanize ->
            val folded = FoldedText.of(text)
            TextFolding.words(folded.text).forEach { word ->
                val latin = romanize.romanize(word.value) ?: return@forEach
                val key = LooseRomanization.key(latin)
                if (key.length >= MIN_TRANSLITERATION_KEY) {
                    add(DerivedToken(folded.toSourceRange(word.range), LooseRomanization.TOKEN_PREFIX + key, DerivedToken.Kind.TRANSLITERATED))
                }
            }
        }
        joinedWord.findAll(text).forEach { match ->
            val joined = TextFolding.fold(match.groupValues[1] + match.groupValues[2])
            add(DerivedToken(match.range, joined, DerivedToken.Kind.JOINED_WORD))
        }
        groupedNumber.findAll(text).forEach { match ->
            // Thousands grouping only: a lone "12.5" is a decimal, not "125".
            if (',' !in match.value && match.value.count { it == '.' } < 2) return@forEach
            val digits = match.value.filter(Char::isDigit)
            add(DerivedToken(match.range, digits, DerivedToken.Kind.NUMBER))
            integerPart(match.value)?.let { add(DerivedToken(match.range, it, DerivedToken.Kind.NUMBER)) }
        }
        digitRun.findAll(text).forEach { match ->
            val digits = match.value.filter(Char::isDigit)
            if (digits.length >= MIN_DIGIT_RUN) add(DerivedToken(match.range, digits, DerivedToken.Kind.NUMBER))
        }
        numericDate.findAll(text).forEach { match ->
            val (first, second, yearText) = match.destructured
            val year = fullYear(yearText)
            // Day first is the norm here; month first too when it is also a valid date.
            date(year, second.toInt(), first.toInt())?.let { add(DerivedToken(match.range, it, DerivedToken.Kind.DATE)) }
            date(year, first.toInt(), second.toInt())?.let { add(DerivedToken(match.range, it, DerivedToken.Kind.DATE)) }
        }
        isoDate.findAll(text).forEach { match ->
            val (year, month, day) = match.destructured
            date(year.toInt(), month.toInt(), day.toInt())?.let { add(DerivedToken(match.range, it, DerivedToken.Kind.DATE)) }
        }
        dayMonthYear.findAll(text).forEach { match ->
            val (day, month, year) = match.destructured
            date(year.toInt(), monthNumber(month), day.toInt())?.let {
                add(DerivedToken(match.range, it, DerivedToken.Kind.DATE))
            }
        }
        monthDayYear.findAll(text).forEach { match ->
            val (month, day, year) = match.destructured
            date(year.toInt(), monthNumber(month), day.toInt())?.let {
                add(DerivedToken(match.range, it, DerivedToken.Kind.DATE))
            }
        }
    }.distinctBy { it.sourceRange to it.value }

    /** The `derivedText` column: every derived value, space-separated. */
    fun derivedText(text: String): String = derive(text).joinToString(" ") { it.value }

    /** "1,250.00" → "1250"; null when there is no decimal part to drop. */
    private fun integerPart(number: String): String? {
        val lastSeparator = number.indexOfLast { it == ',' || it == '.' }
        if (lastSeparator < 0 || number.length - lastSeparator - 1 > 2) return null
        if (number.count { it == number[lastSeparator] } > 1) return null
        return number.substring(0, lastSeparator).filter(Char::isDigit).takeIf { it.isNotEmpty() }
    }

    private fun fullYear(text: String): Int {
        val value = text.toInt()
        return when {
            text.length == 4 -> value
            value < TWO_DIGIT_YEAR_PIVOT -> 2000 + value
            else -> 1900 + value
        }
    }

    private fun monthNumber(name: String): Int = when (name.take(3).lowercase(Locale.ROOT)) {
        "jan" -> 1
        "feb" -> 2
        "mar" -> 3
        "apr" -> 4
        "may" -> 5
        "jun" -> 6
        "jul" -> 7
        "aug" -> 8
        "sep" -> 9
        "oct" -> 10
        "nov" -> 11
        else -> 12
    }

    private fun date(year: Int, month: Int, day: Int): String? {
        if (year !in 1900..2099 || month !in 1..12 || day < 1) return null
        val daysInMonth = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        if (day > daysInMonth) return null
        return "d%04d%02d%02d".format(Locale.ROOT, year, month, day)
    }
}
