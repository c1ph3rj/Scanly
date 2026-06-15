package `in`.c1ph3rj.scanly.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionDestination
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionRoute
import `in`.c1ph3rj.scanly.feature.document.DocumentDestination
import `in`.c1ph3rj.scanly.feature.document.DocumentDetailRoute
import `in`.c1ph3rj.scanly.feature.editor.PageEditorDestination
import `in`.c1ph3rj.scanly.feature.editor.PageEditorRoute
import `in`.c1ph3rj.scanly.feature.home.HomeRoute
import `in`.c1ph3rj.scanly.feature.library.LibraryRoute
import `in`.c1ph3rj.scanly.feature.library.GroupDetailRoute
import `in`.c1ph3rj.scanly.feature.placeholder.FeaturePlaceholderRoute
import `in`.c1ph3rj.scanly.feature.settings.SettingsRoute

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(
        route = ScanlyDestination.Home.route,
        label = "Home",
        icon = Icons.Filled.Home,
    ),
    BottomNavItem(
        route = ScanlyDestination.Library.route,
        label = "Library",
        icon = Icons.Filled.FolderOpen,
    ),
    BottomNavItem(
        route = ScanlyDestination.Settings.route,
        label = "Settings",
        icon = Icons.Filled.Settings,
    ),
)

private val topLevelRoutes = setOf(
    ScanlyDestination.Home.route,
    ScanlyDestination.Library.route,
    ScanlyDestination.Settings.route,
)

@Composable
fun ScanlyNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in topLevelRoutes

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (selected) return@NavigationBarItem
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(text = item.label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ScanlyDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ScanlyDestination.Home.route) {
                HomeRoute(
                    navController = navController,
                    onOpenDocument = { documentId ->
                        navController.navigate(DocumentDestination.route(documentId))
                    },
                    onOpenScanSession = { documentId ->
                        navController.navigate(ScanSessionDestination.route(documentId))
                    },
                    onNavigateToLibrary = {
                        navController.navigate(ScanlyDestination.Library.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(
                route = ScanlyDestination.Library.route,
                enterTransition = { fadeThroughIn() },
                exitTransition = { fadeThroughOut() },
                popEnterTransition = { fadeThroughIn() },
                popExitTransition = { fadeThroughOut() },
            ) {
                LibraryRoute(
                    onOpenDocument = { documentId ->
                        navController.navigate(DocumentDestination.route(documentId))
                    },
                    onOpenScanSession = { documentId ->
                        navController.navigate(ScanSessionDestination.route(documentId))
                    },
                    onOpenGroup = { groupId ->
                        navController.navigate(GroupDetailDestination.route(groupId))
                    },
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
                val documentId =
                    it.arguments?.getString(DocumentDestination.documentIdArgument).orEmpty()
                DocumentDetailRoute(
                    onNavigateUp = navController::navigateUp,
                    onOpenCamera = {
                        navController.navigate(ScanSessionDestination.route(documentId))
                    },
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
                    onOpenDocument = { documentId ->
                        navController.navigate(DocumentDestination.route(documentId))
                    },
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
            composable(
                route = GroupDetailDestination.routePattern,
                arguments = listOf(
                    navArgument(GroupDetailDestination.groupIdArgument) {
                        type = NavType.StringType
                    },
                ),
                enterTransition = { fadeThroughIn() },
                exitTransition = { fadeThroughOut() },
                popEnterTransition = { fadeThroughIn() },
                popExitTransition = { fadeThroughOut() },
            ) {
                GroupDetailRoute(
                    onNavigateUp = navController::navigateUp,
                    onOpenDocument = { documentId ->
                        navController.navigate(DocumentDestination.route(documentId))
                    },
                )
            }
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fadeThroughIn(): EnterTransition =
    EnterTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fadeThroughOut(): ExitTransition =
    ExitTransition.None
