package `in`.c1ph3rj.scanly.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionDestination
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionRoute
import `in`.c1ph3rj.scanly.feature.document.DocumentDestination
import `in`.c1ph3rj.scanly.feature.document.DocumentDetailRoute
import `in`.c1ph3rj.scanly.feature.editor.PageEditorDestination
import `in`.c1ph3rj.scanly.feature.editor.PageEditorRoute
import `in`.c1ph3rj.scanly.feature.groups.GroupsRoute
import `in`.c1ph3rj.scanly.feature.home.HomeRoute
import `in`.c1ph3rj.scanly.feature.placeholder.FeaturePlaceholderRoute
import `in`.c1ph3rj.scanly.feature.search.SearchDestination
import `in`.c1ph3rj.scanly.feature.search.SearchRoute
import `in`.c1ph3rj.scanly.feature.settings.PrivacyPolicyDestination
import `in`.c1ph3rj.scanly.feature.settings.PrivacyPolicyRoute
import `in`.c1ph3rj.scanly.feature.settings.SettingsRoute

@Composable
fun ScanlyNavHost(
    navController: NavHostController,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = ScanlyDestination.Home.route,
        modifier = modifier,
    ) {
        composable(ScanlyDestination.Home.route) {
            RootDestinationScaffold(
                navController = navController,
                onScanClick = onScanClick,
            ) {
                HomeRoute(
                    onOpenDocument = { documentId ->
                        navController.navigate(DocumentDestination.route(documentId))
                    },
                    onOpenGroups = { navController.navigateRoot(ScanlyDestination.Groups.route) },
                    onOpenSearch = { navController.navigate(SearchDestination.route) },
                )
            }
        }
        composable(ScanlyDestination.Library.route) {
            HomeRoute(
                onOpenDocument = { documentId ->
                    navController.navigate(DocumentDestination.route(documentId))
                },
                onOpenGroups = { navController.navigateRoot(ScanlyDestination.Groups.route) },
                onOpenSearch = { navController.navigate(SearchDestination.route) },
                onNavigateUp = navController::navigateUp,
            )
        }
        composable(ScanlyDestination.Groups.route) {
            RootDestinationScaffold(
                navController = navController,
                onScanClick = onScanClick,
            ) {
                GroupsRoute(
                    onOpenDocument = { documentId ->
                        navController.navigate(DocumentDestination.route(documentId))
                    },
                )
            }
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
        composable(SearchDestination.route) {
            SearchRoute(
                onNavigateUp = navController::navigateUp,
                onOpenDocument = { documentId ->
                    navController.navigate(DocumentDestination.route(documentId))
                },
                onOpenGroups = {
                    navController.navigateRoot(ScanlyDestination.Groups.route)
                },
            )
        }
        composable(
            route = ScanlyDestination.Settings.route,
            enterTransition = { fadeThroughIn() },
            exitTransition = { fadeThroughOut() },
            popEnterTransition = { fadeThroughIn() },
            popExitTransition = { fadeThroughOut() },
        ) {
            RootDestinationScaffold(
                navController = navController,
                onScanClick = onScanClick,
            ) {
                SettingsRoute(
                    onOpenPrivacyPolicy = {
                        navController.navigate(PrivacyPolicyDestination.route)
                    },
                )
            }
        }
        composable(
            route = PrivacyPolicyDestination.route,
            enterTransition = { fadeThroughIn() },
            exitTransition = { fadeThroughOut() },
            popEnterTransition = { fadeThroughIn() },
            popExitTransition = { fadeThroughOut() },
        ) {
            PrivacyPolicyRoute(onNavigateUp = navController::navigateUp)
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
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fadeThroughIn(): EnterTransition =
    EnterTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fadeThroughOut(): ExitTransition =
    ExitTransition.None

private fun NavHostController.navigateRoot(route: String) {
    if (currentDestination?.route == route) {
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun RootDestinationScaffold(
    navController: NavHostController,
    onScanClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    Scaffold(
        bottomBar = {
            ScanlyBottomNavigation(
                navController = navController,
                currentDestination = backStackEntry?.destination,
                onScanClick = onScanClick,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}

@Composable
private fun ScanlyBottomNavigation(
    navController: NavHostController,
    currentDestination: NavDestination?,
    onScanClick: () -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        RootNavItem(
            destination = ScanlyDestination.Home,
            currentDestination = currentDestination,
            icon = Icons.Filled.Home,
            navController = navController,
        )
        RootNavItem(
            destination = ScanlyDestination.Groups,
            currentDestination = currentDestination,
            icon = Icons.Filled.Folder,
            navController = navController,
        )
        NavigationBarItem(
            selected = false,
            onClick = onScanClick,
            icon = {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            },
            label = { Text(text = "Scan") },
        )
        RootNavItem(
            destination = ScanlyDestination.Settings,
            currentDestination = currentDestination,
            icon = Icons.Filled.Settings,
            navController = navController,
        )
    }
}

@Composable
private fun RowScope.RootNavItem(
    destination: ScanlyDestination,
    currentDestination: NavDestination?,
    icon: ImageVector,
    navController: NavHostController,
) {
    val selected = currentDestination?.route == destination.route
    NavigationBarItem(
        selected = selected,
        onClick = {
            if (selected) {
                return@NavigationBarItem
            }
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
        },
        label = { Text(text = destination.title) },
    )
}

