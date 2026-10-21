package `in`.c1ph3rj.scanly.feature.library

import `in`.c1ph3rj.scanly.domain.trash.TrashKind

/** "7 days", "2 days": how long a kind of item stays in Recently deleted. */
fun TrashKind.retentionLabel(): String = if (retentionDays == 1) "1 day" else "$retentionDays days"

/** Delete-confirmation copy, shared by every screen that can delete a document, folder or page. */
object TrashCopy {
    fun document(title: String): String =
        "\"$title\" moves to Recently deleted. You can restore it for ${TrashKind.DOCUMENT.retentionLabel()}."

    fun folder(title: String, documentCount: Int): String = when (documentCount) {
        0 -> "\"$title\" moves to Recently deleted. You can restore it for ${TrashKind.FOLDER.retentionLabel()}."
        1 -> "\"$title\" and the document in it move to Recently deleted. " +
            "You can restore them for ${TrashKind.FOLDER.retentionLabel()}."
        else -> "\"$title\" and its $documentCount documents move to Recently deleted. " +
            "You can restore them for ${TrashKind.FOLDER.retentionLabel()}."
    }

    fun page(pageNumber: Int): String =
        "Page $pageNumber moves to Recently deleted. You can restore it for ${TrashKind.PAGE.retentionLabel()}."
}
