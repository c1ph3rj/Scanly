package `in`.c1ph3rj.scanly.feature.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Shared library/home card presentation rules. Compose screens call these
 * helpers so overflow vs always-inline actions and cover geometry stay in
 * one place and can be unit-tested without inflating UI.
 */

/** Portrait paper (width / height) used for document and folder covers. */
const val LIBRARY_PAGE_COVER_ASPECT_RATIO = 3f / 4f

enum class LibraryCardActionPlacement {
    OverflowOrLongPress,
}

enum class LibraryCardSecondaryAction {
    Rename,
    Move,
    Delete,
    RemoveFromFolder,
}

fun libraryCardCoverAspectRatio(): Float = LIBRARY_PAGE_COVER_ASPECT_RATIO

fun libraryCardActionPlacement(): LibraryCardActionPlacement =
    LibraryCardActionPlacement.OverflowOrLongPress

fun libraryCardSecondaryActions(
    showRename: Boolean = false,
    canMove: Boolean = false,
    showDelete: Boolean = false,
    deleteIsRemoveFromFolder: Boolean = false,
): List<LibraryCardSecondaryAction> = buildList {
    if (showRename) add(LibraryCardSecondaryAction.Rename)
    if (canMove) add(LibraryCardSecondaryAction.Move)
    if (showDelete) {
        add(
            if (deleteIsRemoveFromFolder) {
                LibraryCardSecondaryAction.RemoveFromFolder
            } else {
                LibraryCardSecondaryAction.Delete
            },
        )
    }
}

fun formatDocumentCardMeta(pageCount: Int, dateLabel: String): String {
    val pages = if (pageCount == 1) "1 page" else "$pageCount pages"
    val date = dateLabel.trim()
    return if (date.isEmpty()) pages else "$pages  ·  $date"
}

fun formatLibraryModifiedDate(
    modifiedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val date = Instant.ofEpochMilli(modifiedAtMillis).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", Locale.getDefault()))
    }
}

/**
 * Date for a card that already sits under a day/month section header: the time
 * of day for today and yesterday, otherwise the day and month.
 */
fun formatLibrarySectionedDate(
    modifiedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val dateTime = Instant.ofEpochMilli(modifiedAtMillis).atZone(zoneId)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val date = dateTime.toLocalDate()
    return if (date == today || date == today.minusDays(1) || date.isAfter(today)) {
        dateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    } else {
        date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", locale))
    }
}

fun formatGroupCardMeta(documentCount: Int, totalPageCount: Int): String {
    val documents = if (documentCount == 1) "1 document" else "$documentCount documents"
    if (totalPageCount <= 0) return documents
    val pages = if (totalPageCount == 1) "1 page" else "$totalPageCount pages"
    return "$documents  ·  $pages"
}

fun libraryCardSecondaryActionLabel(
    action: LibraryCardSecondaryAction,
    deleteContentDescription: String = "Delete",
): String = when (action) {
    LibraryCardSecondaryAction.Rename -> "Rename"
    LibraryCardSecondaryAction.Move -> "Move to folder"
    LibraryCardSecondaryAction.Delete -> deleteContentDescription
    LibraryCardSecondaryAction.RemoveFromFolder -> "Remove from folder"
}
