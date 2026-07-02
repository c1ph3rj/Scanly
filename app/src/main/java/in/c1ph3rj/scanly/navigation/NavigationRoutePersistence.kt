package `in`.c1ph3rj.scanly.navigation

import androidx.navigation.NavBackStackEntry
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionDestination
import `in`.c1ph3rj.scanly.feature.document.DocumentDestination
import `in`.c1ph3rj.scanly.feature.editor.PageEditorDestination
import `in`.c1ph3rj.scanly.feature.preview.PageImagePreviewDestination

val topLevelNavigationRoutes = setOf(
    ScanlyDestination.Home.route,
    ScanlyDestination.Library.route,
    ScanlyDestination.Settings.route,
)

fun NavBackStackEntry.toPersistableRoute(): String? {
    val template = destination.route ?: return null
    val args = arguments ?: return null
    return when (template) {
        DocumentDestination.routePattern ->
            args.getString(DocumentDestination.documentIdArgument)?.let(DocumentDestination::route)
        GroupDetailDestination.routePattern ->
            args.getString(GroupDetailDestination.groupIdArgument)?.let(GroupDetailDestination::route)
        PageImagePreviewDestination.routePattern ->
            args.getString(PageImagePreviewDestination.pageIdArgument)?.let(PageImagePreviewDestination::route)
        PageEditorDestination.routePattern ->
            args.getString(PageEditorDestination.pageIdArgument)?.let(PageEditorDestination::route)
        ScanSessionDestination.routePattern -> {
            val documentId = args.getString(ScanSessionDestination.documentIdArgument) ?: return null
            val replacePageId = args.getString(ScanSessionDestination.replacePageIdArgument)
            ScanSessionDestination.route(documentId, replacePageId)
        }
        in topLevelNavigationRoutes -> null
        else -> null
    }
}