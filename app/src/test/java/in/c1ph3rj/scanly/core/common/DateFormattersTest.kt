package `in`.c1ph3rj.scanly.core.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DateFormattersTest {
    @Test
    fun formatters_returnNonBlankStringsForKnownMillis() {
        // Fixed instant: 2024-06-15 12:00:00 UTC — formatting still uses default locale/timezone.
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            set(2024, Calendar.JUNE, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val millis = cal.timeInMillis

        assertTrue(millis.toShortDate().isNotBlank())
        assertTrue(millis.toRelativeDate().isNotBlank())
        assertTrue(millis.toReadableDateTime().isNotBlank())
    }
}
