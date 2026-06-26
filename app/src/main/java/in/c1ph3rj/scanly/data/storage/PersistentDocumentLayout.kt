package `in`.c1ph3rj.scanly.data.storage

object PersistentDocumentLayout {
    const val PUBLIC_ROOT_DIRECTORY = "Pictures/Scanly"
    const val RAW_DIRECTORY = "raw"
    const val PROCESSED_DIRECTORY = "processed"
    const val THUMBNAILS_DIRECTORY = "thumbs"
    const val COVER_FILE_NAME = "cover.jpg"

    fun documentRelativePath(documentId: String, directoryName: String): String =
        "$PUBLIC_ROOT_DIRECTORY/$documentId/$directoryName/"

    fun pageFileName(pageIndex: Int): String =
        "page_${(pageIndex + 1).toString().padStart(3, '0')}.jpg"

    fun parseAsset(
        relativePath: String,
        displayName: String,
    ): PersistentDocumentAsset? {
        val normalizedRoot = "$PUBLIC_ROOT_DIRECTORY/"
        if (!relativePath.startsWith(normalizedRoot)) {
            return null
        }

        val parts = relativePath
            .removePrefix(normalizedRoot)
            .trim('/')
            .split('/')

        if (parts.size != 2) {
            return null
        }

        val documentId = parts[0].takeIf { it.isNotBlank() } ?: return null
        val directoryName = parts[1]
        if (directoryName !in knownAssetDirectories) {
            return null
        }

        if (directoryName == THUMBNAILS_DIRECTORY && displayName == COVER_FILE_NAME) {
            return PersistentDocumentAsset(
                documentId = documentId,
                directoryName = directoryName,
                pageIndex = null,
                isCover = true,
            )
        }

        val pageIndex = pageFileNameRegex.matchEntire(displayName)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.minus(1)
            ?.takeIf { it >= 0 }
            ?: return null

        return PersistentDocumentAsset(
            documentId = documentId,
            directoryName = directoryName,
            pageIndex = pageIndex,
            isCover = false,
        )
    }

    private val knownAssetDirectories = setOf(
        RAW_DIRECTORY,
        PROCESSED_DIRECTORY,
        THUMBNAILS_DIRECTORY,
    )
    private val pageFileNameRegex = Regex("""page_(\d{3,}).jpg""")
}

data class PersistentDocumentAsset(
    val documentId: String,
    val directoryName: String,
    val pageIndex: Int?,
    val isCover: Boolean,
)
