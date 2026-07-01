package `in`.c1ph3rj.scanly.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LibraryAssetRef(
    val relativePath: String,
    val revision: Long,
    val byteCount: Long,
    val sha256: String,
    val mimeType: String = "image/jpeg",
)

