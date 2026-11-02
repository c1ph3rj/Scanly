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
            var indices = IntArray(source.length)
            source.forEachIndexed { index, char ->
                // A char folds to zero chars (a dropped accent), one, or two (क़ in NFC is क + ़).
                TextFolding.foldChar(char).forEach { folded ->
                    if (builder.length == indices.size) indices = indices.copyOf(indices.size * 2 + 1)
                    indices[builder.length] = index
                    builder.append(folded)
                }
            }
            return FoldedText(builder.toString(), indices.copyOf(builder.length))
        }
    }
}

/**
 * Folds text the way the page-text index (SQLite FTS4 `unicode61`) tokenizes it, so a typed
 * query and the indexed words line up:
 * - case is folded in every script;
 * - only a single accent on a Latin letter is dropped ("Résumé" → "resume"), which is all
 *   `unicode61` removes;
 * - every other mark is part of the word. Indic vowel signs, viramas and anusvaras (ா, ि, ், ं)
 *   change the word, and so do Greek tonos, Cyrillic й or Vietnamese ệ, as far as the index
 *   is concerned.
 *
 * Text is also put in NFC, so a precomposed letter and its decomposed spelling agree.
 */
object TextFolding {
    private val wordPattern = Regex("[\\p{L}\\p{M}\\p{N}]+")
    private const val COMBINING_DIACRITICS_START = '̀'
    private const val COMBINING_DIACRITICS_END = 'ͯ'

    fun fold(value: String): String = FoldedText.of(value).text

    /** Folds one char to the chars that stand for it in folded text (possibly none). */
    internal fun foldChar(char: Char): String {
        if (char.code < 0x80) return char.lowercaseChar().toString()
        // A loose generic accent (from decomposed Latin text) is dropped like a precomposed one.
        if (isGenericDiacritic(char)) return ""
        val lower = char.toString().lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        if (decomposed.length == 2 && decomposed[0].code < 0x80 && isGenericDiacritic(decomposed[1])) {
            return decomposed.substring(0, 1)
        }
        return Normalizer.normalize(lower, Normalizer.Form.NFC)
    }

    private fun isGenericDiacritic(char: Char): Boolean =
        char in COMBINING_DIACRITICS_START..COMBINING_DIACRITICS_END

    /** Words in folded text. Marks belong to their word, matching the index's tokenizer. */
    fun words(folded: String): Sequence<MatchResult> = wordPattern.findAll(folded)
}
