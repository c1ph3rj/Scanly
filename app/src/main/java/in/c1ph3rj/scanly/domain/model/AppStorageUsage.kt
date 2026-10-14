package `in`.c1ph3rj.scanly.domain.model

data class AppStorageUsage(
    val documentsBytes: Long,
    val exportCacheBytes: Long,
    val databaseBytes: Long,
) {
    val totalBytes: Long
        get() = documentsBytes + exportCacheBytes + databaseBytes
}
