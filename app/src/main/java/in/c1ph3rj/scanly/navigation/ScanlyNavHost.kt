package `in`.c1ph3rj.scanly.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.feature.components.ScanlyAppLogo
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
    val windowSizeInfo = rememberWindowSizeInfo()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showNav = currentRoute in topLevelRoutes

    if (windowSizeInfo.isTablet) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (showNav) {
                ScanlyNavigationRail(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navigateToTopLevel(navController, route) },
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                ScanlyNavHostContent(
                    navController = navController,
                    appUpdateUiState = appUpdateUiState,
                    onCheckForUpdates = onCheckForUpdates,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    } else {
        Scaffold(
            modifier = modifier.background(MaterialTheme.colorScheme.background),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showNav) {
                    ScanlyNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route -> navigateToTopLevel(navController, route) },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .background(MaterialTheme.colorScheme.background),
            ) {
                ScanlyNavHostContent(
                    navController = navController,
                    appUpdateUiState = appUpdateUiState,
                    onCheckForUpdates = onCheckForUpdates,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ScanlyNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(item.route) },
                icon = {
                    Icon(imageVector = item.icon, contentDescription = item.label)
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun ScanlyNavigationRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(92.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScanlyAppLogo(size = 56.dp)
            Spacer(modifier = Modifier.height(20.dp))
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                RailNavIcon(
                    icon = item.icon,
                    label = item.label,
                    selected = selected,
                    onClick = { if (!selected) onNavigate(item.route) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        HorizontalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun RailNavIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val containerColor = when {
        selected -> scheme.primary.copy(alpha = if (isDark) 0.16f else 0.12f)
        isDark -> scheme.surfaceContainerHighest.copy(alpha = 0.45f)
        else -> scheme.surfaceContainerHigh
    }
    val contentColor = when {
        selected -> scheme.primary
        isDark -> scheme.onSurface.copy(alpha = 0.72f)
        else -> scheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun navigateToTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun ScanlyNavHostContent(
    navController: NavHostController,
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = ScanlyDestination.Home.route,
            modifier = Modifier.fillMaxSize(),
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
                    navigateToTopLevel(navController, ScanlyDestination.Library.route)
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
                    if (!navController.popBackStack(route = documentRoute, inclusive = false)) {
                        navController.navigate(documentRoute) {
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

private const val NavAnimDuration = 160

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelEnter(): EnterTransition =
    EnterTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelExit(): ExitTransition =
    ExitTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelPopEnter(): EnterTransition =
    EnterTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelPopExit(): ExitTransition =
    ExitTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPushEnter(): EnterTransition =
    fadeIn(animationSpec = tween(NavAnimDuration))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPushExit(): ExitTransition =
    fadeOut(animationSpec = tween(NavAnimDuration))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPopEnter(): EnterTransition =
    fadeIn(animationSpec = tween(NavAnimDuration))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPopExit(): ExitTransition =
    fadeOut(animationSpec = tween(NavAnimDuration))
