package `in`.c1ph3rj.scanly.domain.search

/**
 * Characters OCR tends to read as one another, in folded (lowercase) text. Typing "invoice"
 * should still reach a page read as "lnvoice", and "मिल" one read as "मील".
 */
object OcrConfusions {
    /** Cost of swapping two look-alike Latin characters or digits. */
    const val LOOK_ALIKE_COST = 0.3

    /** Cost of a look-alike vowel sign, or one glyph read as two ("m" ↔ "rn"). */
    const val SPLIT_GLYPH_COST = 0.4

    private val latinLookAlikes = listOf(
        "li1|", "o0", "s5", "b68", "g9q", "z2", "ec", "uv",
    )

    /** Short/long vowel signs and nasal marks that differ by a small stroke. */
    private val vowelSignLookAlikes = listOf(
        // Devanagari: ि ी, ु ू, े ै, ो ौ, ँ ं
        "िी", "ुू", "ेै", "ोौ", "ँं",
        // Tamil: ி ீ, ு ூ, ெ ே
        "ிீ", "ுூ", "ெே",
        // Telugu: ి ీ, ు ూ, ె ే
        "ిీ", "ుూ", "ెే",
        // Kannada: ಿ ೀ, ು ೂ, ೆ ೇ
        "ಿೀ", "ುೂ", "ೆೇ",
    )

    /** One glyph read as two, written two-char form to one-char form. */
    val splitGlyphs: Map<String, Char> = mapOf(
        "rn" to 'm', "cl" to 'd', "vv" to 'w', "ri" to 'n', "li" to 'h', "ii" to 'u',
    )

    private val substitutionCosts: Map<Long, Double> = buildMap {
        fun addGroup(group: String, cost: Double) {
            group.forEach { a -> group.forEach { b -> if (a != b) put(key(a, b), cost) } }
        }
        latinLookAlikes.forEach { addGroup(it, LOOK_ALIKE_COST) }
        vowelSignLookAlikes.forEach { addGroup(it, SPLIT_GLYPH_COST) }
    }

    private fun key(a: Char, b: Char): Long = (a.code.toLong() shl 16) or b.code.toLong()

    /** Cost of reading [a] as [b]: 0 when equal, less than 1 for look-alikes, else 1. */
    fun substitutionCost(a: Char, b: Char): Double =
        if (a == b) 0.0 else substitutionCosts[key(a, b)] ?: 1.0

    /**
     * First characters a word starting with [first] may have been read as, [first] included.
     * Typo lookups usually trust the first letter; OCR gets it wrong often ("lnvoice").
     */
    fun firstCharsFor(first: Char): Set<Char> = buildSet {
        add(first)
        substitutionCosts.keys.forEach { pair ->
            if ((pair shr 16).toInt().toChar() == first) add((pair and 0xFFFF).toInt().toChar())
        }
        splitGlyphs.forEach { (pair, single) ->
            if (single == first) add(pair[0])
            if (pair[0] == first) add(single)
        }
    }
}
