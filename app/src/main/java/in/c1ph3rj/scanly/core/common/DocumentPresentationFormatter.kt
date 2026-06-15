package `in`.c1ph3rj.scanly.core.common

import java.text.DateFormat
import java.util.Date
import java.util.Locale

object DocumentPresentationFormatter {
    private const val untitledDocument = "Untitled document"
    private const val untitledFileStem = "scanly_document"

    fun normalizeTitle(rawTitle: String): String {
        val normalized = rawTitle.trim().replace("\\s+".toRegex(), " ")
        return normalized.ifBlank { untitledDocument }
    }

    fun initials(title: String): String {
        val words = normalizeTitle(title)
            .split(' ')
            .filter { it.isNotBlank() }

        val letters = when {
            words.isEmpty() -> "D"
            words.size == 1 -> words.first().take(2)
            else -> words.take(2).joinToString(separator = "") { word -> word.take(1) }
        }

        return letters.uppercase(Locale.US)
    }

    fun defaultImportedDocumentTitle(atMillis: Long = System.currentTimeMillis()): String {
        val dateTime = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault(),
        ).format(Date(atMillis))
        return "Imported $dateTime"
    }

    fun resolveUniqueTitle(baseTitle: String, existingTitles: Iterable<String>): String {
        val takenTitles = existingTitles.map(::normalizeTitle).toSet()
        val normalizedBase = normalizeTitle(baseTitle)
        if (normalizedBase !in takenTitles) {
            return normalizedBase
        }

        var suffix = 2
        while (true) {
            val candidate = normalizeTitle("$baseTitle ($suffix)")
            if (candidate !in takenTitles) {
                return candidate
            }
            suffix++
        }
    }

    fun uniqueImportedDocumentTitle(
        existingTitles: Iterable<String>,
        atMillis: Long = System.currentTimeMillis(),
    ): String = resolveUniqueTitle(
        baseTitle = defaultImportedDocumentTitle(atMillis),
        existingTitles = existingTitles,
    )

    fun safeFileStem(title: String): String {
        val cleaned = normalizeTitle(title)
            .lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')

        return cleaned.ifBlank { untitledFileStem }
    }
}
