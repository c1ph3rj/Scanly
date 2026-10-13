package `in`.c1ph3rj.scanly.feature.library

import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class LibraryDocumentSection(
    val key: String,
    val title: String?,
    val documents: List<ScanDocument>,
)

private data class LibrarySectionBucket(
    val key: String,
    val title: String,
)

/**
 * Groups already-sorted documents into sections by date bucket. Documents are grouped
 * contiguously in input order, so a new section starts whenever the bucket changes.
 */
fun libraryDocumentSections(
    documents: List<ScanDocument>,
    sortOption: LibrarySortOption,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): List<LibraryDocumentSection> {
    if (documents.isEmpty()) return emptyList()

    if (sortOption == LibrarySortOption.NameAscending || sortOption == LibrarySortOption.NameDescending) {
        return listOf(LibraryDocumentSection(key = "all", title = null, documents = documents))
    }

    val usesCreatedTime = sortOption == LibrarySortOption.NewestCreated ||
        sortOption == LibrarySortOption.OldestCreated
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()

    val runs = mutableListOf<Pair<LibrarySectionBucket, MutableList<ScanDocument>>>()
    documents.forEach { document ->
        val timestamp = if (usesCreatedTime) document.createdAtMillis else document.updatedAtMillis
        val date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
        val bucket = bucketFor(date = date, today = today, locale = locale)
        val lastRun = runs.lastOrNull()
        if (lastRun != null && lastRun.first.key == bucket.key) {
            lastRun.second += document
        } else {
            runs += bucket to mutableListOf(document)
        }
    }

    val usedKeys = mutableSetOf<String>()
    return runs.mapIndexed { index, (bucket, runDocuments) ->
        val key = if (usedKeys.add(bucket.key)) {
            bucket.key
        } else {
            "${bucket.key}-$index".also { usedKeys += it }
        }
        LibraryDocumentSection(key = key, title = bucket.title, documents = runDocuments.toList())
    }
}

private fun bucketFor(
    date: LocalDate,
    today: LocalDate,
    locale: Locale,
): LibrarySectionBucket {
    // Future dates are treated as today.
    val effective = if (date.isAfter(today)) today else date
    val daysAgo = ChronoUnit.DAYS.between(effective, today)
    return when {
        daysAgo == 0L -> LibrarySectionBucket(key = "today", title = "Today")
        daysAgo == 1L -> LibrarySectionBucket(key = "yesterday", title = "Yesterday")
        daysAgo in 2L..7L -> LibrarySectionBucket(key = "week", title = "Previous 7 days")
        else -> {
            val monthName = effective.month.getDisplayName(TextStyle.FULL, locale)
            val title = if (effective.year == today.year) {
                monthName
            } else {
                "$monthName ${effective.year}"
            }
            LibrarySectionBucket(
                key = "month-%04d-%02d".format(Locale.ROOT, effective.year, effective.monthValue),
                title = title,
            )
        }
    }
}
