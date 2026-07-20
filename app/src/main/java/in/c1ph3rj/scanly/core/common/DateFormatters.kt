package `in`.c1ph3rj.scanly.core.common

import java.text.DateFormat
import java.util.Date

/** Short date (e.g. library cards). */
fun Long.toShortDate(): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(this))

/**
 * Medium date label used for "updated" style chips.
 * Name is historical — not a true relative time string.
 */
fun Long.toRelativeDate(): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

/** Medium date + short time for page timestamps. */
fun Long.toReadableDateTime(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))
