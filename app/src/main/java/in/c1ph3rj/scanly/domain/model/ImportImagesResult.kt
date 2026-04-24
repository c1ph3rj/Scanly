package `in`.c1ph3rj.scanly.domain.model

data class ImportImagesResult(
    val requestedCount: Int,
    val importedCount: Int,
    val failedCount: Int,
) {
    val hasFailures: Boolean
        get() = failedCount > 0
}
