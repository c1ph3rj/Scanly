package `in`.c1ph3rj.scanly.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
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
import `in`.c1ph3rj.scanly.feature.settings.SettingsRoute

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
                onOpenSettings = { navController.navigate(ScanlyDestination.Settings.route) },
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
                onOpenSettings = { navController.navigate(ScanlyDestination.Settings.route) },
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
        composable(
            route = ScanlyDestination.Settings.route,
            enterTransition = { fadeThroughIn() },
            exitTransition = { fadeThroughOut() },
            popEnterTransition = { fadeThroughIn() },
            popExitTransition = { fadeThroughOut() },
        ) {
            SettingsRoute(
                onNavigateUp = navController::navigateUp,
            )
        }
        composable(
            route = DocumentDestination.routePattern,
            enterTransition = { fadeThroughIn() },
            exitTransition = { fadeThroughOut() },
            popEnterTransition = { fadeThroughIn() },
            popExitTransition = { fadeThroughOut() },
        ) {
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
            enterTransition = { fadeThroughIn() },
            exitTransition = { fadeThroughOut() },
            popEnterTransition = { fadeThroughIn() },
            popExitTransition = { fadeThroughOut() },
        ) {
            ScanSessionRoute(
                onNavigateUp = navController::navigateUp,
            )
        }
        composable(
            route = PageEditorDestination.routePattern,
            enterTransition = { fadeThroughIn() },
            exitTransition = { fadeThroughOut() },
            popEnterTransition = { fadeThroughIn() },
            popExitTransition = { fadeThroughOut() },
        ) {
            PageEditorRoute(
                onNavigateUp = navController::navigateUp,
            )
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fadeThroughIn(): EnterTransition =
    EnterTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fadeThroughOut(): ExitTransition =
    ExitTransition.None
