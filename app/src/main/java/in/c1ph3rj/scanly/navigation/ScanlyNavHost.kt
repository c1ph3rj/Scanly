package `in`.c1ph3rj.scanly.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionDestination
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionRoute
import `in`.c1ph3rj.scanly.feature.document.DocumentDestination
import `in`.c1ph3rj.scanly.feature.document.DocumentDetailRoute
import `in`.c1ph3rj.scanly.feature.editor.PageEditorDestination
import `in`.c1ph3rj.scanly.feature.editor.PageEditorRoute
import `in`.c1ph3rj.scanly.feature.home.HomeRoute
import `in`.c1ph3rj.scanly.feature.placeholder.FeaturePlaceholderRoute
import `in`.c1ph3rj.scanly.feature.readiness.ReadinessRoute

@Composable
fun ScanlyNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = ScanlyDestination.Home.route,
        modifier = modifier,
    ) {
        composable(ScanlyDestination.Home.route) {
            HomeRoute(
                onOpenDocument = { documentId ->
                    navController.navigate(DocumentDestination.route(documentId))
                },
                onOpenReadiness = { navController.navigate(ScanlyDestination.Readiness.route) },
                onOpenScanSession = { documentId ->
                    navController.navigate(ScanSessionDestination.route(documentId))
                },
            )
        }
        composable(ScanlyDestination.Library.route) {
            HomeRoute(
                onOpenDocument = { documentId ->
                    navController.navigate(DocumentDestination.route(documentId))
                },
                onOpenReadiness = { navController.navigate(ScanlyDestination.Readiness.route) },
                onOpenScanSession = { documentId ->
                    navController.navigate(ScanSessionDestination.route(documentId))
                },
                onNavigateUp = navController::navigateUp,
            )
        }
        composable(ScanlyDestination.Camera.route) {
            FeaturePlaceholderRoute(
                destination = ScanlyDestination.Camera,
                onNavigateUp = navController::navigateUp,
            )
        }
        composable(ScanlyDestination.Review.route) {
            FeaturePlaceholderRoute(
                destination = ScanlyDestination.Review,
                onNavigateUp = navController::navigateUp,
            )
        }
        composable(ScanlyDestination.Editor.route) {
            FeaturePlaceholderRoute(
                destination = ScanlyDestination.Editor,
                onNavigateUp = navController::navigateUp,
            )
        }
        composable(ScanlyDestination.Readiness.route) {
            ReadinessRoute(
                onNavigateUp = navController::navigateUp,
            )
        }
        composable(DocumentDestination.routePattern) {
            val documentId = it.arguments?.getString(DocumentDestination.documentIdArgument).orEmpty()
            DocumentDetailRoute(
                onNavigateUp = navController::navigateUp,
                onOpenCamera = { navController.navigate(ScanSessionDestination.route(documentId)) },
                onOpenPageEditor = { pageId ->
                    navController.navigate(PageEditorDestination.route(pageId))
                },
                onReplacePage = { pageId ->
                    navController.navigate(
                        ScanSessionDestination.route(
                            documentId = documentId,
                            replacePageId = pageId,
                        ),
                    )
                },
            )
        }
        composable(
            route = ScanSessionDestination.routePattern,
            arguments = listOf(
                navArgument(ScanSessionDestination.documentIdArgument) {
                    type = NavType.StringType
                },
                navArgument(ScanSessionDestination.replacePageIdArgument) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ScanSessionRoute(
                onNavigateUp = navController::navigateUp,
            )
        }
        composable(PageEditorDestination.routePattern) {
            PageEditorRoute(
                onNavigateUp = navController::navigateUp,
            )
        }
    }
}
