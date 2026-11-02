package `in`.c1ph3rj.scanly.domain.search

/** Turns a word in a non-Latin script into plain Latin letters ("சென்னை" → "cennai"), or null. */
fun interface Romanizer {
    fun romanize(word: String): String?
}

/**
 * A loose spelling key, so an English spelling typed by a person and a scholarly romanization
 * of the same Indic word meet: the consonant skeleton, with sounds Indian scripts and English
 * spell differently merged ("chennai" and "cennai" both become "sn", "madurai" and "maturai"
 * both "mtr"). A leading vowel is kept as one mark, since "amma" and "mma" should not meet.
 * Applied to both sides; the key is never shown.
 */
object LooseRomanization {
    /** Key marker in the derived column, so keys never collide with joined words or dates. */
    const val TOKEN_PREFIX = "tr"
    const val MIN_TYPED_LENGTH = 3

    private val digraphs = listOf(
        "zh" to "l", "ck" to "k", "ch" to "c", "sh" to "s", "th" to "t", "dh" to "d", "bh" to "b",
        "kh" to "k", "gh" to "g", "ph" to "p", "jh" to "j",
    )

    /** Voiced and unvoiced pairs merge: Tamil writes both with one letter. */
    private val consonants = mapOf(
        'c' to 's', 'z' to 's', 'x' to 's', 'f' to 'p', 'q' to 'k', 'w' to 'v',
        'g' to 'k', 'd' to 't', 'b' to 'p',
    )
    private const val VOWELS = "aeiouyh"

    fun key(latin: String): String {
        var text = latin.lowercase().filter { it in 'a'..'z' }
        digraphs.forEach { (from, to) -> text = text.replace(from, to) }
        val mapped = StringBuilder(text.length)
        text.forEachIndexed { index, char ->
            val replacement = when {
                char in VOWELS -> if (index == 0) 'a' else null
                else -> consonants.getOrDefault(char, char)
            }
            if (replacement != null && (mapped.isEmpty() || mapped.last() != replacement)) mapped.append(replacement)
        }
        return mapped.toString()
    }

    /** Whether [word] is Latin letters only, i.e. typed in English rather than in the script. */
    fun isLatin(word: String): Boolean = word.isNotEmpty() && word.all { it in 'a'..'z' }
}
