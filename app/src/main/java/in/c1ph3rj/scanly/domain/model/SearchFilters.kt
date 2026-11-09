package `in`.c1ph3rj.scanly.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

/** When a document was last changed. */
enum class DateFilter(@param:StringRes val labelRes: Int) {
    ANY(R.string.date_filter_any_time),
    TODAY(R.string.library_card_presentation_today),
    LAST_7_DAYS(R.string.date_filter_last_7_days),
    LAST_30_DAYS(R.string.date_filter_last_30_days),
    THIS_YEAR(R.string.date_filter_this_year),
    CUSTOM(R.string.date_filter_custom_range),
    ;

    val label: String get() = appString(labelRes)
}

/** Which folder documents must be in. */
sealed interface FolderFilter {
    data object Any : FolderFilter

    /** Documents not in any folder. */
    data object None : FolderFilter

    data class In(val groupId: String) : FolderFilter
}

enum class SearchSort(@param:StringRes val labelRes: Int) {
    BEST_MATCH(R.string.search_sort_best_match),
    NEWEST(R.string.search_sort_newest),
    OLDEST(R.string.search_sort_oldest),
    NAME(R.string.search_sort_name_a_z),
    ;

    val label: String get() = appString(labelRes)
}

/**
 * Filters from the search filter sheet. Each narrows the documents searched (and, with a blank
 * query, the documents browsed); [sort] only reorders.
 */
data class SearchFilters(
    val kind: DocumentKind? = null,
    val date: DateFilter = DateFilter.ANY,
    /**
     * Days chosen for [DateFilter.CUSTOM], as UTC-midnight millis like Material's date range
     * picker returns them; both days are included.
     */
    val customStartUtcMillis: Long? = null,
    val customEndUtcMillis: Long? = null,
    val folder: FolderFilter = FolderFilter.Any,
    val sort: SearchSort = SearchSort.BEST_MATCH,
) {
    /** True when some filter narrows the documents (sort alone doesn't). */
    val narrows: Boolean get() = kind != null || date != DateFilter.ANY || folder != FolderFilter.Any

    /** Filters to count on the Filters chip badge, sort included. */
    val activeCount: Int
        get() = listOf(kind != null, date != DateFilter.ANY, folder != FolderFilter.Any, sort != SearchSort.BEST_MATCH)
            .count { it }

    /** Millis range a document's last change must fall in, or null for any time. */
    fun dateRange(nowMillis: Long, zone: ZoneId): LongRange? {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (date) {
            DateFilter.ANY -> null
            DateFilter.TODAY -> startOf(today)..Long.MAX_VALUE
            DateFilter.LAST_7_DAYS -> startOf(today.minusDays(6))..Long.MAX_VALUE
            DateFilter.LAST_30_DAYS -> startOf(today.minusDays(29))..Long.MAX_VALUE
            DateFilter.THIS_YEAR -> startOf(today.withDayOfYear(1))..Long.MAX_VALUE
            DateFilter.CUSTOM -> {
                val start = customStartUtcMillis ?: return null
                val end = customEndUtcMillis ?: start
                // The picker's days are UTC midnights; match them as local calendar days.
                val firstDay = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate()
                val lastDay = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                startOf(firstDay) until startOf(lastDay.plusDays(1))
            }
        }
    }
}
