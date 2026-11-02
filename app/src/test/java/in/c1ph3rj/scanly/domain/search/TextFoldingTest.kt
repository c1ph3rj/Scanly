package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The folded form must equal what SQLite's `unicode61` tokenizer indexes, or typed queries
 * never reach the page text. Expected values were checked against FTS4 `unicode61`.
 */
class TextFoldingTest {
    private fun words(value: String) = TextFolding.words(TextFolding.fold(value)).map { it.value }.toList()

    @Test
    fun dropsSingleAccentOnLatinLetters() {
        assertEquals("resume istanbul cafe", TextFolding.fold("Résumé İstanbul Café"))
    }

    @Test
    fun dropsLooseCombiningAccentFromDecomposedLatin() {
        assertEquals("cafe", TextFolding.fold("café"))
    }

    @Test
    fun keepsIndicVowelSignsViramasAndAnusvaraInOneWord() {
        assertEquals(listOf("தமிழ்நாடு", "அரசு"), words("தமிழ்நாடு அரசு"))
        assertEquals(listOf("प्रधानमंत्री"), words("प्रधानमंत्री"))
        assertEquals(listOf("ಕನ್ನಡ"), words("ಕನ್ನಡ"))
        assertEquals(listOf("హైదరాబాద్"), words("హైదరాబాద్"))
    }

    @Test
    fun keepsLettersTheIndexTreatsAsDistinct() {
        // unicode61 keeps й, Greek tonos and a Vietnamese letter with two marks as they are.
        assertEquals("йод", TextFolding.fold("Йод"))
        assertEquals("άνθρωπος", TextFolding.fold("Άνθρωπος"))
        assertEquals("việt", TextFolding.fold("Việt"))
    }

    @Test
    fun keepsHangulSyllablesWhole() {
        assertEquals("한국어", TextFolding.fold("한국어"))
    }

    @Test
    fun precomposedAndDecomposedNuktaFoldAlike() {
        assertEquals(TextFolding.fold("क़"), TextFolding.fold("क़"))
    }

    @Test
    fun expandedCharsMapBackToTheirSource() {
        val folded = FoldedText.of("aक़b")
        assertEquals("aक़b", folded.text)
        assertEquals(1..1, folded.toSourceRange(1..2))
        assertEquals(1..2, folded.toSourceRange(1..3))
    }
}
