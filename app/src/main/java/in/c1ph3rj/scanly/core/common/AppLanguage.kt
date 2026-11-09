package `in`.c1ph3rj.scanly.core.common

import java.util.Locale

/**
 * A language Scanly ships translations for.
 *
 * [tag] is the BCP 47 tag handed to the platform (and listed in `res/xml/locales_config.xml`).
 * [nativeName] is the language's own name for itself, so people can find their language whatever
 * the app is currently showing.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    INDONESIAN("id", "Bahasa Indonesia"),
    ITALIAN("it", "Italiano"),
    TURKISH("tr", "Türkçe"),
    VIETNAMESE("vi", "Tiếng Việt"),
    POLISH("pl", "Polski"),
    DUTCH("nl", "Nederlands"),
    HINDI("hi", "हिन्दी"),
    TAMIL("ta", "தமிழ்"),
    BENGALI("bn", "বাংলা"),
    TELUGU("te", "తెలుగు"),
    MARATHI("mr", "मराठी"),
    KANNADA("kn", "ಕನ್ನಡ"),
    MALAYALAM("ml", "മലയാളം"),
    GUJARATI("gu", "ગુજરાતી"),
    RUSSIAN("ru", "Русский"),
    UKRAINIAN("uk", "Українська"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    THAI("th", "ไทย"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    /** The language's name in [displayLocale], e.g. "German" in English or "Allemand" in French. */
    fun displayName(displayLocale: Locale): String {
        val name = when (this) {
            // The platform name for zh-CN is "Chinese (China)"; the script is what matters here.
            CHINESE_SIMPLIFIED -> Locale.forLanguageTag("zh-Hans").getDisplayName(displayLocale)
            else -> locale.getDisplayName(displayLocale)
        }
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString() }
    }

    /**
     * True when PDF stamps (watermark text, signature dates) in this language can be drawn with
     * the Latin/Cyrillic fonts PdfFontResolver embeds.
     */
    val stampsInOwnScript: Boolean
        get() = when (this) {
            ENGLISH, SPANISH, PORTUGUESE_BRAZIL, FRENCH, GERMAN, INDONESIAN, ITALIAN, TURKISH,
            VIETNAMESE, POLISH, DUTCH, RUSSIAN, UKRAINIAN,
            -> true
            else -> false
        }

    companion object {
        /** The supported language closest to [locale], or null when Scanly has no translation. */
        fun from(locale: Locale?): AppLanguage? {
            locale ?: return null
            val language = when (val lang = locale.language) {
                "in" -> "id" // Java's legacy code for Indonesian.
                else -> lang
            }
            return when (language) {
                "pt" -> PORTUGUESE_BRAZIL
                "zh" -> {
                    // Only Simplified Chinese ships; Traditional readers fall back to the default.
                    val traditional = locale.script == "Hant" ||
                        (locale.script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))
                    if (traditional) null else CHINESE_SIMPLIFIED
                }
                else -> entries.firstOrNull { it.tag == language }
            }
        }

        fun fromTag(tag: String?): AppLanguage? =
            tag?.takeIf { it.isNotBlank() }?.let { from(Locale.forLanguageTag(it)) }
    }
}
