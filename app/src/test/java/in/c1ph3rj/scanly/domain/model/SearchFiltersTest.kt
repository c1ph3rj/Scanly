package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SearchFiltersTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDate.of(2026, 11, 2).atTime(15, 30).atZone(zone).toInstant().toEpochMilli()

    private fun startOf(year: Int, month: Int, day: Int) =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun relativeWindowsStartAtLocalMidnight() {
        assertNull(SearchFilters().dateRange(now, zone))
        assertEquals(startOf(2026, 11, 2), SearchFilters(date = DateFilter.TODAY).dateRange(now, zone)!!.first)
        assertEquals(startOf(2026, 10, 27), SearchFilters(date = DateFilter.LAST_7_DAYS).dateRange(now, zone)!!.first)
        assertEquals(startOf(2026, 10, 4), SearchFilters(date = DateFilter.LAST_30_DAYS).dateRange(now, zone)!!.first)
        assertEquals(startOf(2026, 1, 1), SearchFilters(date = DateFilter.THIS_YEAR).dateRange(now, zone)!!.first)
    }

    @Test
    fun customRangeIncludesBothPickedDays() {
        fun utc(day: Int) = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val range = SearchFilters(date = DateFilter.CUSTOM, customStartUtcMillis = utc(10), customEndUtcMillis = utc(12))
            .dateRange(now, zone)!!
        assertEquals(startOf(2026, 10, 10), range.first)
        assertTrue(startOf(2026, 10, 12) + 1 in range)
        assertFalse(startOf(2026, 10, 13) in range)
    }

    @Test
    fun sortAloneDoesNotNarrowButCountsAsActive() {
        val sorted = SearchFilters(sort = SearchSort.NEWEST)
        assertFalse(sorted.narrows)
        assertEquals(1, sorted.activeCount)
        assertTrue(SearchFilters(folder = FolderFilter.None).narrows)
    }
}
