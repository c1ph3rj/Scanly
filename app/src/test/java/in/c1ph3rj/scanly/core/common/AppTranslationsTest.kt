package `in`.c1ph3rj.scanly.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Checks the shipped resources, independent of the local translation work directory. */
class AppTranslationsTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first(File::isDirectory)
    private val english = readResources(File(res, "values/strings.xml"))

    @Test
    fun everyShippedLanguageHasEveryTranslatableResource() {
        AppLanguage.entries.filter { it != AppLanguage.ENGLISH }.forEach { language ->
            val translated = readResources(resourceFile(language))
            assertEquals("${language.tag}: resource names", english.keys, translated.keys)
            english.forEach { (name, source) ->
                val target = translated.getValue(name)
                assertEquals("${language.tag}/$name: resource type", source.type, target.type)
                assertEquals("${language.tag}/$name: formatting mode", source.formatted, target.formatted)
                if (source.type == "plurals") {
                    assertEquals("${language.tag}/$name: plural forms", quantities(language), target.values.keys)
                }
                target.values.forEach { (quantity, text) ->
                    assertTrue("${language.tag}/$name/$quantity: empty text", text.isNotBlank())
                    val original = source.values[quantity] ?: source.values.getValue("other")
                    assertEquals(
                        "${language.tag}/$name/$quantity: format arguments",
                        placeholders(original),
                        placeholders(text),
                    )
                }
            }
        }
    }

    @Test
    fun stampsKeepEnglishWhenThePdfFontCannotDrawTheLanguage() {
        val stamps = listOf(
            "pdf_watermark_preset_confidential", "pdf_watermark_preset_draft",
            "pdf_watermark_preset_copy", "pdf_watermark_for_kyc_use_only",
        )
        AppLanguage.entries.filter { !it.stampsInOwnScript }.forEach { language ->
            val translated = readResources(resourceFile(language))
            stamps.forEach { name ->
                assertEquals("${language.tag}/$name", english.getValue(name).values, translated.getValue(name).values)
            }
        }
    }

    private fun resourceFile(language: AppLanguage): File {
        val qualifier = when (language) {
            AppLanguage.INDONESIAN -> "in"
            AppLanguage.PORTUGUESE_BRAZIL -> "pt-rBR"
            AppLanguage.CHINESE_SIMPLIFIED -> "zh-rCN"
            else -> language.tag
        }
        return File(res, "values-$qualifier/strings.xml").also {
            assertTrue("Missing translations for ${language.tag}: $it", it.isFile)
        }
    }

    private fun quantities(language: AppLanguage): Set<String> = when (language) {
        AppLanguage.SPANISH, AppLanguage.PORTUGUESE_BRAZIL, AppLanguage.FRENCH, AppLanguage.ITALIAN ->
            setOf("one", "many", "other")
        AppLanguage.POLISH, AppLanguage.RUSSIAN, AppLanguage.UKRAINIAN -> setOf("one", "few", "many", "other")
        AppLanguage.INDONESIAN, AppLanguage.VIETNAMESE, AppLanguage.JAPANESE, AppLanguage.KOREAN,
        AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.THAI -> setOf("other")
        else -> setOf("one", "other")
    }

    private fun placeholders(text: String): Map<String, Int> =
        Regex("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])")
            .findAll(text).filter { it.groupValues[2] != "%" }
            .map { "${it.groupValues[1]}:${it.groupValues[2]}" }.groupingBy { it }.eachCount()

    private fun readResources(file: File): Map<String, Resource> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        return buildMap {
            for (i in 0 until root.childNodes.length) {
                val element = root.childNodes.item(i) as? Element ?: continue
                if (element.getAttribute("translatable") == "false") continue
                val name = element.getAttribute("name")
                val values = if (element.tagName == "plurals") {
                    val items = element.getElementsByTagName("item")
                    buildMap {
                        for (j in 0 until items.length) {
                            val item = items.item(j) as Element
                            put(item.getAttribute("quantity"), unescape(item.textContent))
                        }
                    }
                } else {
                    mapOf("other" to unescape(element.textContent))
                }
                assertTrue(
                    "$file: duplicate $name",
                    put(name, Resource(element.tagName, values, element.getAttribute("formatted") != "false")) == null,
                )
            }
        }
    }

    private fun unescape(text: String): String = text.removeSurrounding("\"")
        .replace("\\'", "'").replace("\\\"", "\"")

    private data class Resource(val type: String, val values: Map<String, String>, val formatted: Boolean)
}
