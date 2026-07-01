package `in`.c1ph3rj.scanly.core.common

import java.util.Locale

object LibraryPathFormatter {
    fun formatTreeDocumentId(documentId: String): String {
        val colonIndex = documentId.indexOf(':')
        if (colonIndex < 0) return documentId

        val volume = documentId.substring(0, colonIndex)
        val relativePath = documentId.substring(colonIndex + 1).trim('/')
        val volumeLabel = when (volume.lowercase(Locale.US)) {
            "primary" -> "Internal storage"
            else -> volume
        }
        return if (relativePath.isEmpty()) {
            volumeLabel
        } else {
            "$volumeLabel/$relativePath"
        }
    }
}
