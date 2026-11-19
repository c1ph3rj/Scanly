package `in`.c1ph3rj.scanly.feature.onboarding

import `in`.c1ph3rj.scanly.core.common.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WelcomeLanguageMatchTest {
    private fun search(query: String, ui: Locale = Locale.ENGLISH) =
        languageEntries(ui).filter { it.matches(query) }.map { it.language }

    @Test fun blankQueryKeepsEveryLanguage() {
        assertEquals(AppLanguage.entries, search("  "))
    }

    @Test fun matchesEnglishNameNativeNameAndTag() {
        assertEquals(listOf(AppLanguage.TAMIL), search("tamil"))
        assertEquals(listOf(AppLanguage.TAMIL), search("தமிழ்"))
        assertTrue(AppLanguage.JAPANESE in search("ja"))
        assertTrue(AppLanguage.GERMAN in search("DEUTSCH"))
    }

    @Test fun matchesTheNameInTheCurrentUiLanguage() {
        // "Allemand" is German in French, and appears nowhere else.
        assertEquals(listOf(AppLanguage.GERMAN), search("allemand", Locale.FRENCH))
    }

    @Test fun unknownQueryMatchesNothing() {
        assertTrue(search("klingon").isEmpty())
    }

    @Test fun subtitleIsHiddenWhenItRepeatsTheNativeName() {
        val english = languageEntries(Locale.ENGLISH).first { it.language == AppLanguage.ENGLISH }
        assertNull(english.subtitle)
        val tamil = languageEntries(Locale.ENGLISH).first { it.language == AppLanguage.TAMIL }
        assertEquals("Tamil", tamil.subtitle)
        assertEquals("TA", tamil.badge)
    }
}
