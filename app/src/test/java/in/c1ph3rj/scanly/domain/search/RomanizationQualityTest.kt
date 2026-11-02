package `in`.c1ph3rj.scanly.domain.search

import com.ibm.icu.text.Transliterator
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quality gate for English-spelling search: how often a common English spelling, matched as a
 * word start against the loose key, finds the word written in its own script. Uses ICU4J,
 * which has the same transliterator as `android.icu` on the phone.
 */
class RomanizationQualityTest {
    private val transliterator = Transliterator.getInstance("Any-Latin; Latin-ASCII; Lower")

    private val pairs: Map<String, List<Pair<String, String>>> = mapOf(
        "Tamil" to listOf(
            "சென்னை" to "chennai", "மதுரை" to "madurai", "திருச்சி" to "trichy", "சேலம்" to "salem",
            "தமிழ்" to "tamil", "அம்மா" to "amma", "அப்பா" to "appa", "வணக்கம்" to "vanakkam",
            "நன்றி" to "nandri", "கடை" to "kadai", "பள்ளி" to "palli", "வீடு" to "veedu",
            "தண்ணீர்" to "thanneer", "முருகன்" to "murugan", "கண்ணன்" to "kannan", "லட்சுமி" to "lakshmi",
            "செல்வம்" to "selvam", "ராஜா" to "raja", "தேனி" to "theni", "கரூர்" to "karur",
            "ஈரோடு" to "erode", "நெல்லை" to "nellai", "புதுச்சேரி" to "puducherry", "கன்னியாகுமரி" to "kanyakumari",
        ),
        "Hindi" to listOf(
            "मुंबई" to "mumbai", "दिल्ली" to "delhi", "कोलकाता" to "kolkata", "पुणे" to "pune",
            "लखनऊ" to "lucknow", "जयपुर" to "jaipur", "भारत" to "bharat", "नमस्ते" to "namaste",
            "प्रधानमंत्री" to "pradhanmantri", "सरकार" to "sarkar", "पानी" to "pani", "घर" to "ghar",
            "राम" to "ram", "कृष्ण" to "krishna", "शिक्षा" to "shiksha", "धन्यवाद" to "dhanyavad",
            "किताब" to "kitab", "बाज़ार" to "bazaar", "दुकान" to "dukaan", "गंगा" to "ganga",
            "आगरा" to "agra", "पटना" to "patna", "भोपाल" to "bhopal", "इंदौर" to "indore",
        ),
        "Kannada" to listOf(
            "ಬೆಂಗಳೂರು" to "bengaluru", "ಮೈಸೂರು" to "mysore", "ಕನ್ನಡ" to "kannada", "ಹುಬ್ಬಳ್ಳಿ" to "hubballi",
            "ಮಂಗಳೂರು" to "mangaluru", "ಬೆಳಗಾವಿ" to "belagavi", "ನಮಸ್ಕಾರ" to "namaskara", "ಉಡುಪಿ" to "udupi",
            "ಧಾರವಾಡ" to "dharwad", "ಶಿವಮೊಗ್ಗ" to "shivamogga",
        ),
        "Telugu" to listOf(
            "హైదరాబాద్" to "hyderabad", "తెలుగు" to "telugu", "విజయవాడ" to "vijayawada",
            "విశాఖపట్నం" to "visakhapatnam", "తిరుపతి" to "tirupati", "వరంగల్" to "warangal",
            "గుంటూరు" to "guntur", "నెల్లూరు" to "nellore", "కర్నూలు" to "kurnool", "నమస్కారం" to "namaskaram",
        ),
        "Malayalam" to listOf(
            "കേരളം" to "keralam", "കൊച്ചി" to "kochi", "തിരുവനന്തപുരം" to "thiruvananthapuram",
            "കോഴിക്കോട്" to "kozhikode", "തൃശ്ശൂർ" to "thrissur", "മലയാളം" to "malayalam", "കണ്ണൂർ" to "kannur",
            "ആലപ്പുഴ" to "alappuzha", "കൊല്ലം" to "kollam", "പാലക്കാട്" to "palakkad",
        ),
        "Bengali" to listOf(
            "কলকাতা" to "kolkata", "বাংলা" to "bangla", "ঢাকা" to "dhaka", "শিলিগুড়ি" to "siliguri",
            "দুর্গা" to "durga", "রবীন্দ্রনাথ" to "rabindranath", "আসানসোল" to "asansol", "হাওড়া" to "howrah",
            "নমস্কার" to "nomoskar", "মাছ" to "maach",
        ),
    )

    /** Whole keys must be equal, as search compares them (word starts would match far too much). */
    private fun found(native: String, typed: String): Boolean =
        LooseRomanization.key(transliterator.transliterate(native)) == LooseRomanization.key(typed)

    @Test
    fun englishSpellingsFindMostWordsInTheirScript() {
        var total = 0
        var hits = 0
        pairs.forEach { (script, words) ->
            val misses = words.filterNot { (native, typed) -> found(native, typed) }
            total += words.size
            hits += words.size - misses.size
            println(
                "$script: ${words.size - misses.size}/${words.size}" +
                    misses.joinToString(prefix = " misses: ") { (native, typed) ->
                        "$typed≠${LooseRomanization.key(transliterator.transliterate(native))}/${LooseRomanization.key(typed)}"
                    },
            )
        }
        val recall = hits.toDouble() / total
        println("Overall recall: ${"%.2f".format(recall)} ($hits/$total)")
        // Precision proxy: other words in the list each typed spelling would also bring up.
        val natives = pairs.values.flatten().map { it.first }.distinct()
        val falseMatches = pairs.values.flatten().sumOf { (native, typed) ->
            natives.count { other -> other != native && found(other, typed) }
        }
        println("Wrong words matched per query: ${"%.2f".format(falseMatches.toDouble() / total)}")
        assertTrue("recall $recall", recall >= REQUIRED_RECALL)
    }

    private companion object {
        /**
         * English-spelling search is on by default only while recall stays at or above this
         * (94% when it was turned on, with 0.16 wrong words per query on this list).
         */
        const val REQUIRED_RECALL = 0.8
    }
}
