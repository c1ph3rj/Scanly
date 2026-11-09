package `in`.c1ph3rj.scanly.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class AppLanguageTest {

    @Test
    fun shipsTheTwentyFiveRequestedLanguages() {
        assertEquals(25, AppLanguage.entries.size)
        assertEquals(AppLanguage.entries.size, AppLanguage.entries.map { it.tag }.toSet().size)
    }

    @Test
    fun matchesLocalesToSupportedLanguages() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.from(Locale.forLanguageTag("de-AT")))
        assertEquals(AppLanguage.SPANISH, AppLanguage.from(Locale.forLanguageTag("es-MX")))
        assertEquals(AppLanguage.PORTUGUESE_BRAZIL, AppLanguage.from(Locale.forLanguageTag("pt-PT")))
        assertEquals(AppLanguage.TAMIL, AppLanguage.from(Locale.forLanguageTag("ta-IN")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.from(Locale.UK))
    }

    @Test
    fun handlesIndonesianLegacyCode() {
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromTag("id"))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.from(Locale("in", "ID")))
    }

    @Test
    fun onlySimplifiedChineseMatchesChinese() {
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.fromTag("zh-CN"))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.fromTag("zh-Hans-SG"))
        assertNull(AppLanguage.fromTag("zh-TW"))
        assertNull(AppLanguage.fromTag("zh-Hant"))
    }

    @Test
    fun unsupportedOrBlankTagsMeanSystemDefault() {
        assertNull(AppLanguage.fromTag("sw"))
        assertNull(AppLanguage.fromTag(""))
        assertNull(AppLanguage.fromTag(null))
    }

    @Test
    fun stampDatesFallBackToEnglishForScriptsPdfFontsCannotDraw() {
        assertTrue(AppLanguage.RUSSIAN.stampsInOwnScript)
        assertFalse(AppLanguage.HINDI.stampsInOwnScript)
        assertEquals("13 Oct 2026", formatStampDate(STAMP_MILLIS, Locale.forLanguageTag("hi")))
        assertEquals("13 Oct 2026", formatStampDate(STAMP_MILLIS, Locale.ENGLISH))
    }

    @Test
    fun localeConfigListsEveryLanguage() {
        val config = listOf("src/main/res/xml/locales_config.xml", "app/src/main/res/xml/locales_config.xml")
            .map(::File).first(File::exists).readText()
        val tags = Regex("android:name=\"([^\"]+)\"").findAll(config).map { it.groupValues[1] }.toList()
        assertEquals(AppLanguage.entries.map { it.tag }, tags)
    }

    private companion object {
        // 13 Oct 2026, midday UTC so every test time zone sees the same date.
        const val STAMP_MILLIS = 1_791_892_800_000L
    }
}
