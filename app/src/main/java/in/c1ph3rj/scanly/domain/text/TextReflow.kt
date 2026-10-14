package `in`.c1ph3rj.scanly.domain.text

/**
 * Rebuilds readable paragraphs from recognized lines. Prose wraps into one flowing paragraph
 * (so copy/paste doesn't carry the page's hard line breaks, and "agree-/ment" rejoins), while
 * short-line layouts such as addresses, receipts and lists keep their line breaks.
 */
object TextReflow {
    private const val PROSE_MIN_AVERAGE_LINE_LENGTH = 28
    private val listItemStart = Regex("^([•·▪◦*\\-–]|\\(?\\d{1,3}[.)]|\\(?[a-zA-Z][.)])\\s")

    fun paragraph(lines: List<String>): String {
        val cleaned = lines.map(String::trim).filter(String::isNotEmpty)
        if (cleaned.size < 2) return cleaned.joinToString("\n")
        val averageLength = cleaned.sumOf { it.length } / cleaned.size
        if (averageLength < PROSE_MIN_AVERAGE_LINE_LENGTH) return cleaned.joinToString("\n")

        val builder = StringBuilder(cleaned.first())
        cleaned.drop(1).forEach { line ->
            when {
                listItemStart.containsMatchIn(line) -> builder.append('\n').append(line)
                endsWithHyphenatedWord(builder) && line.first().isLowerCase() -> {
                    builder.setLength(builder.length - 1)
                    builder.append(line)
                }
                else -> builder.append(' ').append(line)
            }
        }
        return builder.toString()
    }

    /** Joins block paragraphs into page text with a blank line between blocks. */
    fun page(paragraphs: List<String>): String =
        paragraphs.filter(String::isNotBlank).joinToString("\n\n")

    private fun endsWithHyphenatedWord(builder: StringBuilder): Boolean =
        builder.length >= 2 && builder.last() == '-' && builder[builder.length - 2].isLetter()
}
