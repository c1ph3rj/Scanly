package `in`.c1ph3rj.scanly.feature.components

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
