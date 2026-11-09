package `in`.c1ph3rj.scanly.data.text

import android.icu.text.Transliterator
import `in`.c1ph3rj.scanly.domain.search.Romanizer

/**
 * Romanizes words in any script with the platform's ICU ("சென்னை" → "cennai"), so typing an
 * English spelling can find text read from Indic pages. A Transliterator isn't thread-safe;
 * calls are serialized, and recent words are cached since page text repeats them a lot.
 */
class IcuRomanizer : Romanizer {
    private val transliterator by lazy { Transliterator.getInstance("Any-Latin; Latin-ASCII; Lower") }

    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > MAX_CACHED
    }

    override fun romanize(word: String): String? {
        if (word.none { it.code > LATIN_EXTENDED_END && it.isLetter() }) return null
        synchronized(this) {
            return cache.getOrPut(word) { transliterator.transliterate(word) }
        }
    }

    private companion object {
        /** Letters past Latin Extended-B are in another script and worth romanizing. */
        const val LATIN_EXTENDED_END = 0x024F
        const val MAX_CACHED = 4_096
    }
}
