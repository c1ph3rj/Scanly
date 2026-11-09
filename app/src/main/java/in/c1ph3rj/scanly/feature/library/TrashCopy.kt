package `in`.c1ph3rj.scanly.feature.library

import `in`.c1ph3rj.scanly.core.common.appPluralString
import `in`.c1ph3rj.scanly.domain.trash.TrashKind
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString

/** "7 days", "2 days": how long a kind of item stays in Recently deleted. */
fun TrashKind.retentionLabel(): String = appPluralString(R.plurals.common_days, retentionDays, retentionDays)

/** Delete-confirmation copy, shared by every screen that can delete a document, folder or page. */
object TrashCopy {
    fun document(title: String): String =
        appString(R.string.trash_copy_moves_to_recently_deleted_you, title, TrashKind.DOCUMENT.retentionLabel())

    fun folder(title: String, documentCount: Int): String = when (documentCount) {
        0 -> appString(R.string.trash_copy_moves_to_recently_deleted_you, title, TrashKind.FOLDER.retentionLabel())
        1 -> appString(R.string.trash_copy_and_the_document_in_it, title, TrashKind.FOLDER.retentionLabel())
        else -> appString(R.string.trash_copy_and_its_documents_move_to, title, documentCount, TrashKind.FOLDER.retentionLabel())
    }

    fun page(pageNumber: Int): String =
        appString(R.string.trash_copy_page_moves_to_recently_deleted, pageNumber, TrashKind.PAGE.retentionLabel())
}
