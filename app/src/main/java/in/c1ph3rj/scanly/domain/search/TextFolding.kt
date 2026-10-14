package `in`.c1ph3rj.scanly.domain.search

import java.text.Normalizer
import java.util.Locale

/**
 * Case- and accent-insensitive view of a string that remembers where each folded character
 * came from, so matches found on the folded text can be highlighted on the original.
 */
class FoldedText private constructor(
    val text: String,
    private val sourceIndex: IntArray,
) {
    /** Maps a range on [text] back to the original string. */
    fun toSourceRange(range: IntRange): IntRange =
        sourceIndex[range.first]..sourceIndex[range.last]

    companion object {
        fun of(source: String): FoldedText {
            val builder = StringBuilder(source.length)
            val indices = IntArray(source.length)
            var count = 0
            source.forEachIndexed { index, char ->
                val folded = TextFolding.foldChar(char)
                if (folded != null) {
                    builder.append(folded)
                    indices[count++] = index
                }
            }
            return FoldedText(builder.toString(), indices.copyOf(count))
        }
    }
}

object TextFolding {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val wordPattern = Regex("[\\p{L}\\p{N}]+")

    fun fold(value: String): String = FoldedText.of(value).text

    /** Folds one char to a single char, or null for chars that vanish (combining marks). */
    internal fun foldChar(char: Char): Char? {
        if (char.code < 0x80) return char.lowercaseChar()
        val decomposed = combiningMarks.replace(Normalizer.normalize(char.toString(), Normalizer.Form.NFD), "")
        return decomposed.lowercase(Locale.ROOT).firstOrNull()
    }

    fun words(folded: String): Sequence<MatchResult> = wordPattern.findAll(folded)
}
