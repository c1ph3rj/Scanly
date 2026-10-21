package `in`.c1ph3rj.scanly.domain.trash

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class TrashPolicyTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val hour = TimeUnit.HOURS.toMillis(1)
    private val deletedAt = 1_700_000_000_000L

    @Test
    fun retention_isTwoDaysForPagesAndSevenForDocumentsAndFolders() {
        assertEquals(2, TrashKind.PAGE.retentionDays)
        assertEquals(7, TrashKind.DOCUMENT.retentionDays)
        assertEquals(7, TrashKind.FOLDER.retentionDays)
    }

    @Test
    fun purgeCutoff_isRetentionBeforeNow_perKind() {
        val now = deletedAt + 45 * day
        assertEquals(now - 2 * day, TrashPolicy.purgeCutoff(TrashKind.PAGE, now))
        assertEquals(now - 7 * day, TrashPolicy.purgeCutoff(TrashKind.DOCUMENT, now))
        assertEquals(now - 7 * day, TrashPolicy.purgeCutoff(TrashKind.FOLDER, now))
    }

    @Test
    fun daysUntilPurge_isFullRetentionRightAfterDeleting_perKind() {
        assertEquals(2, TrashPolicy.daysUntilPurge(TrashKind.PAGE, deletedAt, deletedAt))
        assertEquals(7, TrashPolicy.daysUntilPurge(TrashKind.DOCUMENT, deletedAt, deletedAt))
        assertEquals(7, TrashPolicy.daysUntilPurge(TrashKind.FOLDER, deletedAt, deletedAt))
    }

    @Test
    fun daysUntilPurge_roundsPartialDaysUp() {
        // One day minus an hour has passed: a page has 1 day + 1 hour left (shows 2), a document 6 days + 1 hour (shows 7).
        val now = deletedAt + day - hour
        assertEquals(2, TrashPolicy.daysUntilPurge(TrashKind.PAGE, deletedAt, now))
        assertEquals(7, TrashPolicy.daysUntilPurge(TrashKind.DOCUMENT, deletedAt, now))
    }

    @Test
    fun daysUntilPurge_exactlyOneDayLeft_isOne_perKind() {
        assertEquals(1, TrashPolicy.daysUntilPurge(TrashKind.PAGE, deletedAt, deletedAt + day))
        assertEquals(1, TrashPolicy.daysUntilPurge(TrashKind.DOCUMENT, deletedAt, deletedAt + 6 * day))
        assertEquals(1, TrashPolicy.daysUntilPurge(TrashKind.FOLDER, deletedAt, deletedAt + 6 * day))
    }

    @Test
    fun daysUntilPurge_oneMillisecondLeft_isOne() {
        assertEquals(
            1,
            TrashPolicy.daysUntilPurge(TrashKind.DOCUMENT, deletedAt, deletedAt + 7 * day - 1),
        )
    }

    @Test
    fun daysUntilPurge_isZeroWhenExactlyDue_perKind() {
        assertEquals(0, TrashPolicy.daysUntilPurge(TrashKind.PAGE, deletedAt, deletedAt + 2 * day))
        assertEquals(0, TrashPolicy.daysUntilPurge(TrashKind.DOCUMENT, deletedAt, deletedAt + 7 * day))
        assertEquals(0, TrashPolicy.daysUntilPurge(TrashKind.FOLDER, deletedAt, deletedAt + 7 * day))
    }

    @Test
    fun daysUntilPurge_isZeroWhenOverdue_perKind() {
        assertEquals(0, TrashPolicy.daysUntilPurge(TrashKind.PAGE, deletedAt, deletedAt + 3 * day))
        assertEquals(0, TrashPolicy.daysUntilPurge(TrashKind.DOCUMENT, deletedAt, deletedAt + 8 * day))
        assertEquals(0, TrashPolicy.daysUntilPurge(TrashKind.FOLDER, deletedAt, deletedAt + 8 * day))
    }
}
