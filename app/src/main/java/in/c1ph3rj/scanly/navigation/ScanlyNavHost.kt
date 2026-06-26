package `in`.c1ph3rj.scanly.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
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
import `in`.c1ph3rj.scanly.feature.update.AppUpdateUiState
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
import `in`.c1ph3rj.scanly.feature.settings.LegalDocumentRoute
import `in`.c1ph3rj.scanly.feature.settings.LegalDocumentType
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
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in topLevelRoutes

    Scaffold(
        modifier = modifier,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomNav,
                enter = fadeIn(animationSpec = tween(180)) +
                    slideInVertically(animationSpec = tween(220)) { height -> height / 3 },
                exit = fadeOut(animationSpec = tween(120)) +
                    slideOutVertically(animationSpec = tween(180)) { height -> height / 3 },
            ) {
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
            // Only consume the bottom padding globally (navigation bar inset).
            // Each screen is responsible for its own top / status-bar inset so
            // that its background colour flows seamlessly behind the transparent bar.
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = { topLevelEnter() },
            exitTransition = { topLevelExit() },
            popEnterTransition = { topLevelPopEnter() },
            popExitTransition = { topLevelPopExit() },
        ) {
            composable(
                route = ScanlyDestination.Home.route,
                enterTransition = { topLevelEnter() },
                exitTransition = { topLevelExit() },
                popEnterTransition = { topLevelPopEnter() },
                popExitTransition = { topLevelPopExit() },
            ) {
                HomeRoute(
                    onOpenDocument = { documentId ->
                        navController.navigate(DocumentDestination.route(documentId))
                    },
                    onOpenScanSession = { documentId ->
                        navController.navigate(ScanSessionDestination.route(documentId))
                    },
                    onOpenGroup = { groupId ->
                        navController.navigate(GroupDetailDestination.route(groupId))
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
                enterTransition = { topLevelEnter() },
                exitTransition = { topLevelExit() },
                popEnterTransition = { topLevelPopEnter() },
                popExitTransition = { topLevelPopExit() },
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
                enterTransition = { topLevelEnter() },
                exitTransition = { topLevelExit() },
                popEnterTransition = { topLevelPopEnter() },
                popExitTransition = { topLevelPopExit() },
            ) {
                SettingsRoute(
                    onNavigateUp = navController::navigateUp,
                    appUpdateUiState = appUpdateUiState,
                    onCheckForUpdates = onCheckForUpdates,
                    onOpenLegalDocument = { documentType ->
                        navController.navigate(LegalDocumentDestination.route(documentType))
                    },
                )
            }
            composable(
                route = LegalDocumentDestination.routePattern,
                arguments = listOf(
                    navArgument(LegalDocumentDestination.typeArgument) {
                        type = NavType.StringType
                    },
                ),
                enterTransition = { detailPushEnter() },
                exitTransition = { detailPushExit() },
                popEnterTransition = { detailPopEnter() },
                popExitTransition = { detailPopExit() },
            ) {
                val documentTypeName =
                    it.arguments?.getString(LegalDocumentDestination.typeArgument).orEmpty()
                val documentType = LegalDocumentType.entries.firstOrNull { type ->
                    type.name == documentTypeName
                } ?: LegalDocumentType.Privacy

                LegalDocumentRoute(
                    documentType = documentType,
                    onNavigateUp = navController::navigateUp,
                )
            }
            composable(
                route = DocumentDestination.routePattern,
                enterTransition = { detailPushEnter() },
                exitTransition = { detailPushExit() },
                popEnterTransition = { detailPopEnter() },
                popExitTransition = { detailPopExit() },
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
                enterTransition = { detailPushEnter() },
                exitTransition = { detailPushExit() },
                popEnterTransition = { detailPopEnter() },
                popExitTransition = { detailPopExit() },
            ) {
                ScanSessionRoute(
                    onNavigateUp = navController::navigateUp,
                    onOpenDocument = { documentId ->
                        val documentRoute = DocumentDestination.route(documentId)
                        val returnedToExistingDocument = navController.popBackStack(
                            route = documentRoute,
                            inclusive = false,
                        )
                        if (!returnedToExistingDocument) {
                            val currentDestinationId = navController.currentBackStackEntry?.destination?.id
                            navController.navigate(documentRoute) {
                                if (currentDestinationId != null) {
                                    popUpTo(currentDestinationId) {
                                        inclusive = true
                                    }
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
            composable(
                route = PageEditorDestination.routePattern,
                enterTransition = { detailPushEnter() },
                exitTransition = { detailPushExit() },
                popEnterTransition = { detailPopEnter() },
                popExitTransition = { detailPopExit() },
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
                enterTransition = { detailPushEnter() },
                exitTransition = { detailPushExit() },
                popEnterTransition = { detailPopEnter() },
                popExitTransition = { detailPopExit() },
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

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelEnter(): EnterTransition =
    fadeIn(animationSpec = tween(180)) +
        slideInHorizontally(animationSpec = tween(220)) { fullWidth -> fullWidth / 12 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelExit(): ExitTransition =
    fadeOut(animationSpec = tween(140)) +
        slideOutHorizontally(animationSpec = tween(180)) { fullWidth -> -fullWidth / 16 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelPopEnter(): EnterTransition =
    fadeIn(animationSpec = tween(180)) +
        slideInHorizontally(animationSpec = tween(220)) { fullWidth -> -fullWidth / 12 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelPopExit(): ExitTransition =
    fadeOut(animationSpec = tween(140)) +
        slideOutHorizontally(animationSpec = tween(180)) { fullWidth -> fullWidth / 16 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPushEnter(): EnterTransition =
    fadeIn(animationSpec = tween(180)) +
        slideInHorizontally(animationSpec = tween(240)) { fullWidth -> fullWidth / 5 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPushExit(): ExitTransition =
    fadeOut(animationSpec = tween(140)) +
        slideOutHorizontally(animationSpec = tween(220)) { fullWidth -> -fullWidth / 12 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPopEnter(): EnterTransition =
    fadeIn(animationSpec = tween(180)) +
        slideInHorizontally(animationSpec = tween(240)) { fullWidth -> -fullWidth / 12 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPopExit(): ExitTransition =
    fadeOut(animationSpec = tween(140)) +
        slideOutHorizontally(animationSpec = tween(220)) { fullWidth -> fullWidth / 5 }
