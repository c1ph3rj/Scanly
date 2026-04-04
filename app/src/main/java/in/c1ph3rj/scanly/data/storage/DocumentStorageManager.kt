package `in`.c1ph3rj.scanly.data.storage

interface DocumentStorageManager {
    suspend fun createDocumentScaffold(
        documentId: String,
        title: String,
    ): DocumentFileLayout

    suspend fun refreshDocumentCover(
        documentId: String,
        title: String,
    ): String

    suspend fun createPageCaptureDraft(
        documentId: String,
        pageIndex: Int,
    ): PageCaptureStorageDraft

    suspend fun generatePageThumbnail(
        rawImagePath: String,
        thumbnailPath: String,
    ): PageThumbnailResult

    suspend fun deleteDocumentStorage(documentId: String)
}
