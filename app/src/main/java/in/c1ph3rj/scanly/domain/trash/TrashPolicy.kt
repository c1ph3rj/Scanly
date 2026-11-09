package `in`.c1ph3rj.scanly.domain.trash

import java.util.concurrent.TimeUnit

/** What can sit in Recently deleted, and for how long before it is removed for good. */
enum class TrashKind(val retentionDays: Int) {
    PAGE(retentionDays = 2),
    DOCUMENT(retentionDays = 7),
    FOLDER(retentionDays = 7),
}

object TrashPolicy {
    private val DayMillis = TimeUnit.DAYS.toMillis(1)

    /** Items of [kind] deleted at or before this instant are due for permanent removal. */
    fun purgeCutoff(kind: TrashKind, nowMillis: Long): Long = nowMillis - kind.retentionDays * DayMillis

    /** Whole days left before an item of [kind] is purged; 0 once it is due. Partial days round up. */
    fun daysUntilPurge(kind: TrashKind, deletedAtMillis: Long, nowMillis: Long): Int {
        val remaining = deletedAtMillis + kind.retentionDays * DayMillis - nowMillis
        if (remaining <= 0L) return 0
        return ((remaining + DayMillis - 1) / DayMillis).toInt()
    }
}
