package `in`.c1ph3rj.scanly.domain.model

/**
 * Progress emitted while each gallery image is decoded and run through the
 * document processing pipeline (corners + filter + thumbnail).
 */
data class ImportImagesProgress(
    val currentIndex: Int,
    val totalCount: Int,
    val stage: ImportStage,
) {
    /** Short, stable stage copy — pair with "X of Y" in the fixed-size loader. */
    val stageLabel: String
        get() = when (stage) {
            ImportStage.Preparing -> "Preparing page"
            ImportStage.Detecting -> "Detecting document"
            ImportStage.Finalizing -> "Saving page"
        }
}

enum class ImportStage {
    Preparing,
    Detecting,
    Finalizing,
}
