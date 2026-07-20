package `in`.c1ph3rj.scanly.feature.document

object DocumentDestination {
    const val documentIdArgument = "documentId"
    const val routePattern = "document/{$documentIdArgument}"

    fun route(documentId: String): String = "document/$documentId"
}
