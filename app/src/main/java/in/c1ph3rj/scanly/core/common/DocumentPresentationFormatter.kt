package `in`.c1ph3rj.scanly.core.common

import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentPresentationFormatter {
    private const val untitledDocument = "Untitled document"
    private const val untitledFolder = "Untitled folder"
    private const val untitledFileStem = "scanly_document"

    fun normalizeTitle(rawTitle: String): String {
        val normalized = rawTitle.trim().replace("\\s+".toRegex(), " ")
        return normalized.ifBlank { untitledDocument }
    }

    fun normalizeGroupTitle(rawTitle: String): String {
        val normalized = rawTitle.trim().replace("\\s+".toRegex(), " ")
        return normalized.ifBlank { untitledFolder }
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

    fun defaultImportedDocumentTitle(atMillis: Long = System.currentTimeMillis()): String =
        importedDocumentTitle(atMillis)

    fun formatTitle(
        format: DocumentTitleFormat,
        atMillis: Long = System.currentTimeMillis(),
    ): String {
        val date = Date(atMillis)
        val locale = Locale.getDefault()
        return when (format) {
            DocumentTitleFormat.ScanDateTime -> {
                val dateTime = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    locale,
                ).format(date)
                "Scan $dateTime"
            }

            DocumentTitleFormat.DocumentDateTime -> {
                val dateTime = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    locale,
                ).format(date)
                "Document $dateTime"
            }

            DocumentTitleFormat.ScanDate -> {
                val shortDate = DateFormat.getDateInstance(DateFormat.SHORT, locale).format(date)
                "Scan $shortDate"
            }

            DocumentTitleFormat.ScanIsoDate -> {
                val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                "Scan $isoDate"
            }
        }
    }

    fun uniqueDocumentTitle(
        format: DocumentTitleFormat,
        existingTitles: Iterable<String>,
        atMillis: Long = System.currentTimeMillis(),
    ): String = resolveUniqueTitle(
        baseTitle = formatTitle(format, atMillis),
        existingTitles = existingTitles,
    )

    fun formatGroupTitle(
        format: GroupTitleFormat,
        atMillis: Long = System.currentTimeMillis(),
    ): String {
        val date = Date(atMillis)
        val locale = Locale.getDefault()
        return when (format) {
            GroupTitleFormat.FolderDateTime -> {
                val dateTime = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    locale,
                ).format(date)
                "Folder $dateTime"
            }

            GroupTitleFormat.NewFolderDateTime -> {
                val dateTime = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    locale,
                ).format(date)
                "New folder $dateTime"
            }

            GroupTitleFormat.FolderDate -> {
                val shortDate = DateFormat.getDateInstance(DateFormat.SHORT, locale).format(date)
                "Folder $shortDate"
            }

            GroupTitleFormat.FolderIsoDate -> {
                val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                "Folder $isoDate"
            }
        }
    }

    fun uniqueGroupTitle(
        format: GroupTitleFormat,
        existingTitles: Iterable<String>,
        atMillis: Long = System.currentTimeMillis(),
    ): String = resolveUniqueGroupTitle(
        baseTitle = formatGroupTitle(format, atMillis),
        existingTitles = existingTitles,
    )

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

    fun resolveUniqueGroupTitle(baseTitle: String, existingTitles: Iterable<String>): String {
        val takenTitles = existingTitles.map(::normalizeGroupTitle).toSet()
        val normalizedBase = normalizeGroupTitle(baseTitle)
        if (normalizedBase !in takenTitles) {
            return normalizedBase
        }

        var suffix = 2
        while (true) {
            val candidate = normalizeGroupTitle("$baseTitle ($suffix)")
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
        baseTitle = importedDocumentTitle(atMillis),
        existingTitles = existingTitles,
    )

    private fun importedDocumentTitle(atMillis: Long): String {
        val dateTime = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault(),
        ).format(Date(atMillis))
        return "Imported $dateTime"
    }

    fun safeFileStem(title: String): String {
        val cleaned = normalizeTitle(title)
            .lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')

        return cleaned.ifBlank { untitledFileStem }
    }
}
