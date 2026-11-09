package `in`.c1ph3rj.scanly.domain.model

import `in`.c1ph3rj.scanly.domain.trash.TrashKind

/** One entry in Recently deleted. */
sealed interface TrashedItem {
    val kind: TrashKind
    val id: String
    val deletedAtMillis: Long
}

/** A folder deleted together with the documents that were in it. */
data class TrashedFolder(
    override val id: String,
    val title: String,
    val documentCount: Int,
    val pageCount: Int,
    val coverThumbnailPath: String?,
    override val deletedAtMillis: Long,
    /** Documents kept in the library when only the folder was deleted; they rejoin it on restore. */
    val keptDocumentCount: Int = 0,
) : TrashedItem {
    override val kind: TrashKind get() = TrashKind.FOLDER
}

data class TrashedDocument(
    val document: ScanDocument,
    override val deletedAtMillis: Long,
) : TrashedItem {
    override val kind: TrashKind get() = TrashKind.DOCUMENT
    override val id: String get() = document.id
}

/** A page deleted from a document that is still in the library. */
data class TrashedPage(
    override val id: String,
    val documentId: String,
    val documentTitle: String,
    /** 1-based position the page had before it was deleted. */
    val pageNumber: Int,
    val thumbnailPath: String?,
    override val deletedAtMillis: Long,
) : TrashedItem {
    override val kind: TrashKind get() = TrashKind.PAGE
}
