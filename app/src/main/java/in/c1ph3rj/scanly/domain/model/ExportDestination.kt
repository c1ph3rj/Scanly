package `in`.c1ph3rj.scanly.domain.model

sealed interface ExportDestination {
    data object DefaultDownloadsScanly : ExportDestination

    data class CustomTree(
        val uriString: String,
        val displayName: String,
    ) : ExportDestination

    val exportLabel: String
        get() = when (this) {
            DefaultDownloadsScanly -> DEFAULT_EXPORT_LABEL
            is CustomTree -> displayName
        }

    companion object {
        const val DEFAULT_EXPORT_LABEL = "Downloads/Scanly"
    }
}
