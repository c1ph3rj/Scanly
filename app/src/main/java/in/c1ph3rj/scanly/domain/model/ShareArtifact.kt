package `in`.c1ph3rj.scanly.domain.model

data class ShareArtifact(
    val mimeType: String,
    val title: String,
    val filePaths: List<String>,
)
