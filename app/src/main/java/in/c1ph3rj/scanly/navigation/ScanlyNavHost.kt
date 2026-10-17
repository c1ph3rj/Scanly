package `in`.c1ph3rj.scanly.navigation

import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionDestination
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionRoute
import `in`.c1ph3rj.scanly.feature.components.ScanlyAppLogo
import `in`.c1ph3rj.scanly.feature.document.DocumentDestination
import `in`.c1ph3rj.scanly.feature.document.DocumentDetailRoute
import `in`.c1ph3rj.scanly.feature.editor.PageCropDestination
import `in`.c1ph3rj.scanly.feature.editor.PageCropRoute
import `in`.c1ph3rj.scanly.feature.editor.PageEditorDestination
import `in`.c1ph3rj.scanly.feature.editor.PageEditorRoute
import `in`.c1ph3rj.scanly.feature.home.HomeRoute
import `in`.c1ph3rj.scanly.feature.library.GroupDetailRoute
import `in`.c1ph3rj.scanly.feature.library.LibraryRoute
import `in`.c1ph3rj.scanly.feature.library.LibrarySearchRoute
import `in`.c1ph3rj.scanly.feature.text.PageTextDestination
import `in`.c1ph3rj.scanly.feature.text.PageTextRoute
import `in`.c1ph3rj.scanly.feature.library.LibraryTab
import `in`.c1ph3rj.scanly.feature.placeholder.FeaturePlaceholderRoute
import `in`.c1ph3rj.scanly.feature.preview.PageImagePreviewDestination
import `in`.c1ph3rj.scanly.feature.preview.PageImagePreviewRoute
import `in`.c1ph3rj.scanly.feature.settings.LegalDocumentRoute
import `in`.c1ph3rj.scanly.feature.settings.LegalDocumentType
import `in`.c1ph3rj.scanly.feature.settings.ModelBenchmarkRoute
import `in`.c1ph3rj.scanly.feature.settings.SettingsAboutRoute
import `in`.c1ph3rj.scanly.feature.settings.SettingsAppearanceRoute
import `in`.c1ph3rj.scanly.feature.settings.SettingsDetectionRoute
import `in`.c1ph3rj.scanly.feature.settings.SettingsFaqRoute
import `in`.c1ph3rj.scanly.feature.settings.SettingsLicensesRoute
import `in`.c1ph3rj.scanly.feature.settings.SettingsRoute
import `in`.c1ph3rj.scanly.feature.settings.SettingsStorageRoute
import `in`.c1ph3rj.scanly.feature.settings.SettingsWidgetsRoute
import `in`.c1ph3rj.scanly.feature.tools.ToolsRoute
import `in`.c1ph3rj.scanly.feature.tools.pdf.PdfCompressRoute
import `in`.c1ph3rj.scanly.feature.tools.pdf.PdfMergeRoute
import `in`.c1ph3rj.scanly.feature.tools.pdf.PdfPasswordRoute
import `in`.c1ph3rj.scanly.feature.tools.pdf.PdfReaderRoute
import `in`.c1ph3rj.scanly.feature.tools.pdf.PdfWatermarkRoute
import `in`.c1ph3rj.scanly.feature.tools.pdf.PdfSignRoute
import `in`.c1ph3rj.scanly.feature.sign.create.SignatureCreateRoute
import `in`.c1ph3rj.scanly.feature.sign.document.DocumentSignRoute
import `in`.c1ph3rj.scanly.feature.sign.manage.SignatureManageRoute
import `in`.c1ph3rj.scanly.feature.tools.qr.QrToolMode
import `in`.c1ph3rj.scanly.feature.tools.qr.QrToolRoute
import `in`.c1ph3rj.scanly.feature.update.AppUpdateUiState
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(
        route = ScanlyDestination.Home.route,
        label = "Home",
        icon = Icons.Outlined.Home,
    ),
    BottomNavItem(
        route = ScanlyDestination.Library.route,
        label = "Library",
        icon = Icons.Outlined.Folder,
    ),
    BottomNavItem(
        route = ScanlyDestination.Tools.route,
        label = "Tools",
        icon = Icons.Outlined.GridView,
    ),
    BottomNavItem(
        route = ScanlyDestination.Settings.route,
        label = "Settings",
        icon = Icons.Outlined.Settings,
    ),
)

private val topLevelRoutes = setOf(
    ScanlyDestination.Home.route,
    ScanlyDestination.Library.route,
    ScanlyDestination.Tools.route,
    ScanlyDestination.Settings.route,
)

@Composable
fun ScanlyNavHost(
    navController: NavHostController,
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier,
    onScan: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    val captureActions = remember(onScan, onImport) { ScanlyCaptureActions(onScan, onImport) }
    CompositionLocalProvider(LocalScanlyCaptureActions provides captureActions) {
        ScanlyNavHostLayout(navController, appUpdateUiState, onCheckForUpdates, modifier)
    }
}

/** Actions behind the center capture button; same flows as the launcher Scan / Import shortcuts. */
private class ScanlyCaptureActions(val onScan: () -> Unit, val onImport: () -> Unit)

private val LocalScanlyCaptureActions = staticCompositionLocalOf { ScanlyCaptureActions({}, {}) }

@Composable
private fun ScanlyNavHostLayout(
    navController: NavHostController,
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in topLevelRoutes

    if (windowSizeInfo.isTablet) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ScanlyNavHostContent(
                navController = navController,
                appUpdateUiState = appUpdateUiState,
                onCheckForUpdates = onCheckForUpdates,
                useTabletNavigationRail = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Scaffold(
            modifier = modifier.background(MaterialTheme.colorScheme.background),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomNav) {
                    ScanlyNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route -> navigateToTopLevel(navController, route) },
                    )
                }
            },
        ) { innerPadding ->
            // The bottom bar's measured height includes the transparent strip the capture button
            // rises into. Padding content by the full height left that strip as an opaque band of
            // background colour above the bar (it read as a black bar cutting through the button
            // in dark mode). Let content run under the strip so only the button overlaps it.
            val contentBottomPadding = if (showBottomNav) {
                (innerPadding.calculateBottomPadding() - CaptureButtonRise).coerceAtLeast(0.dp)
            } else {
                innerPadding.calculateBottomPadding()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = contentBottomPadding)
                    .consumeWindowInsets(PaddingValues(bottom = contentBottomPadding))
                    .background(MaterialTheme.colorScheme.background),
            ) {
                ScanlyNavHostContent(
                    navController = navController,
                    appUpdateUiState = appUpdateUiState,
                    onCheckForUpdates = onCheckForUpdates,
                    useTabletNavigationRail = false,
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
    var chooserVisible by rememberSaveable { mutableStateOf(false) }
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    // The rise strip keeps the overlapping capture button inside this Box, so all of it is touchable.
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CaptureButtonRise)
                .drawBehind {
                    // Hairline top edge so the bar separates from same-tone content scrolling under it.
                    val stroke = 1.dp.toPx()
                    drawRect(color = dividerColor, size = Size(size.width, stroke))
                },
            color = barColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(CompactNavBarHeight)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bottomNavItems.forEachIndexed { index, item ->
                    if (index == bottomNavItems.size / 2) {
                        Box(
                            modifier = Modifier.weight(1f).height(CompactNavBarHeight),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Text(
                                text = "Scan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .clearAndSetSemantics {},
                            )
                        }
                    }
                    val selected = currentRoute == item.route
                    CompactNavItem(
                        item = item,
                        selected = selected,
                        onClick = { if (!selected) onNavigate(item.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        CaptureButton(
            onClick = { chooserVisible = true },
            // Page-colour ring, as in the design: the button reads as sitting in a notch.
            ringColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    if (chooserVisible) {
        CaptureChooserSheet(onDismiss = { chooserVisible = false })
    }
}

@Composable
private fun CompactNavItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .height(CompactNavBarHeight)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag(ScanlyTestTags.navTag(item.route)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Ripple and selection pill share the icon's 56 x 30 shape.
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun CaptureButton(
    onClick: () -> Unit,
    ringColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(CaptureButtonSize)
            .testTag(ScanlyTestTags.NAV_SCAN)
            .semantics { contentDescription = "Scan or import" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(4.dp, ringColor),
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

/** Asks whether to capture with the camera or import photos, then hands off to that flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureChooserSheet(onDismiss: () -> Unit) {
    val actions = LocalScanlyCaptureActions.current
    ScanlyModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = { MaterialTheme.colorScheme.surfaceContainerLow },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Add a document",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            CaptureChoiceRow(
                icon = Icons.Outlined.DocumentScanner,
                title = "Scan with camera",
                subtitle = "Capture pages now",
                onClick = {
                    onDismiss()
                    actions.onScan()
                },
            )
            CaptureChoiceRow(
                icon = Icons.Outlined.PhotoLibrary,
                title = "Import from gallery",
                subtitle = "Up to 10 photos",
                onClick = {
                    onDismiss()
                    actions.onImport()
                },
            )
        }
    }
}

@Composable
private fun CaptureChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val CompactNavBarHeight = 64.dp
private val CaptureButtonSize = 52.dp
private val CaptureButtonRise = 16.dp

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
            var chooserVisible by rememberSaveable { mutableStateOf(false) }
            Surface(
                onClick = { chooserVisible = true },
                modifier = Modifier
                    .size(56.dp)
                    .testTag(ScanlyTestTags.NAV_SCAN)
                    .semantics { contentDescription = "Scan or import" },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            if (chooserVisible) {
                CaptureChooserSheet(onDismiss = { chooserVisible = false })
            }
            Spacer(modifier = Modifier.height(20.dp))
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                RailNavIcon(
                    icon = item.icon,
                    label = item.label,
                    selected = selected,
                    testTag = ScanlyTestTags.navTag(item.route),
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
    testTag: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(14.dp)
    val containerColor = when {
        selected && isDark -> scheme.surfaceContainerHigh
        selected -> scheme.surfaceContainerLow
        isDark -> scheme.surfaceContainer
        else -> scheme.surfaceContainerHigh
    }
    val contentColor = when {
        selected -> scheme.primary
        else -> scheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag),
        color = containerColor,
        shape = shape,
        border = if (selected) {
            BorderStroke(1.dp, scheme.primary.copy(alpha = if (isDark) 0.72f else 0.64f))
        } else {
            null
        },
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

@Composable
private fun TopLevelDestinationShell(
    useTabletNavigationRail: Boolean,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!useTabletNavigationRail) {
        content()
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScanlyNavigationRail(
            currentRoute = currentRoute,
            onNavigate = onNavigate,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background),
        ) {
            content()
        }
    }
}

fun navigateScanlyTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun navigateToTopLevel(navController: NavHostController, route: String) {
    navigateScanlyTopLevel(navController, route)
}

@Composable
private fun ScanlyNavHostContent(
    navController: NavHostController,
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    useTabletNavigationRail: Boolean,
    modifier: Modifier = Modifier,
) {
    // One-shot request from Home's "See all" actions for how the Library should open.
    var libraryTabRequest by remember { mutableStateOf<LibraryTab?>(null) }
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
            TopLevelDestinationShell(
                useTabletNavigationRail = useTabletNavigationRail,
                currentRoute = ScanlyDestination.Home.route,
                onNavigate = { route -> navigateToTopLevel(navController, route) },
            ) {
                HomeRoute(
                    onOpenSearch = { navController.navigate(LibrarySearchDestination.route) },
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
                        libraryTabRequest = LibraryTab.All
                        navigateToTopLevel(navController, ScanlyDestination.Library.route)
                    },
                    onSeeAllFolders = {
                        libraryTabRequest = LibraryTab.Folders
                        navigateToTopLevel(navController, ScanlyDestination.Library.route)
                    },
                )
            }
        }
        composable(
            route = ScanlyDestination.Library.route,
            enterTransition = { topLevelEnter() },
            exitTransition = { topLevelExit() },
            popEnterTransition = { topLevelPopEnter() },
            popExitTransition = { topLevelPopExit() },
        ) {
            TopLevelDestinationShell(
                useTabletNavigationRail = useTabletNavigationRail,
                currentRoute = ScanlyDestination.Library.route,
                onNavigate = { route -> navigateToTopLevel(navController, route) },
            ) {
                LibraryRoute(
                    tabRequest = libraryTabRequest,
                    onTabRequestHandled = { libraryTabRequest = null },
                    onOpenSearch = { navController.navigate(LibrarySearchDestination.route) },
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
        }
        composable(route = LibrarySearchDestination.route) {
            LibrarySearchRoute(
                onBack = { navController.popBackStack() },
                onOpenDocument = { navController.navigate(DocumentDestination.route(it)) },
                onOpenGroup = { navController.navigate(GroupDetailDestination.route(it)) },
                onOpenPageText = { documentId, pageIndex, query ->
                    navController.navigate(PageTextDestination.route(documentId, pageIndex, query))
                },
            )
        }
        composable(
            route = PageTextDestination.routePattern,
            arguments = listOf(
                navArgument(PageTextDestination.documentIdArgument) { type = NavType.StringType },
                navArgument(PageTextDestination.pageArgument) {
                    type = NavType.StringType
                    defaultValue = "0"
                },
                navArgument(PageTextDestination.queryArgument) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            PageTextRoute(onNavigateUp = navController::navigateUp)
        }
        composable(
            route = ScanlyDestination.Tools.route,
            enterTransition = { topLevelEnter() },
            exitTransition = { topLevelExit() },
            popEnterTransition = { topLevelPopEnter() },
            popExitTransition = { topLevelPopExit() },
        ) {
            TopLevelDestinationShell(
                useTabletNavigationRail = useTabletNavigationRail,
                currentRoute = ScanlyDestination.Tools.route,
                onNavigate = { route -> navigateToTopLevel(navController, route) },
            ) {
                ToolsRoute(
                    onOpenDocument = { documentId ->
                        navController.navigate(DocumentDestination.route(documentId))
                    },
                    onOpenScanSession = { documentId ->
                        navController.navigate(ScanSessionDestination.route(documentId))
                    },
                    onOpenTool = { route ->
                        navController.navigate(route)
                    },
                )
            }
        }
        composable(
            route = ToolsQrDestination.routePattern,
            arguments = listOf(
                navArgument(ToolsQrDestination.modeArgument) {
                    type = NavType.StringType
                    defaultValue = "scan"
                },
            ),
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) { backStackEntry ->
            val mode = when (
                backStackEntry.arguments?.getString(ToolsQrDestination.modeArgument)?.lowercase()
            ) {
                "generate" -> QrToolMode.Generate
                else -> QrToolMode.Scan
            }
            QrToolRoute(
                onNavigateUp = navController::navigateUp,
                initialMode = mode,
            )
        }
        composable(
            route = ToolsPdfReaderDestination.routePattern,
            arguments = listOf(
                navArgument(ToolsPdfReaderDestination.filePathArgument) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(ToolsPdfReaderDestination.fileNameArgument) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments
                ?.getString(ToolsPdfReaderDestination.filePathArgument)
                .orEmpty()
            val fileName = backStackEntry.arguments
                ?.getString(ToolsPdfReaderDestination.fileNameArgument)
                .orEmpty()
            PdfReaderRoute(
                onNavigateUp = navController::navigateUp,
                initialSource = filePath.takeIf(String::isNotBlank)?.let {
                    PdfToolSource.AppFile(
                        filePath = it,
                        displayName = fileName.ifBlank { "Scanly PDF" },
                    )
                },
            )
        }
        composable(
            route = ToolsPdfMergeDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            PdfMergeRoute(
                onNavigateUp = navController::navigateUp,
                onPreviewPdf = { artifact ->
                    navController.navigate(
                        ToolsPdfReaderDestination.route(artifact.filePath, artifact.fileName),
                    )
                },
            )
        }
        composable(
            route = ToolsPdfCompressDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            PdfCompressRoute(
                onNavigateUp = navController::navigateUp,
                onPreviewPdf = { artifact ->
                    navController.navigate(
                        ToolsPdfReaderDestination.route(artifact.filePath, artifact.fileName),
                    )
                },
            )
        }
        composable(
            route = ToolsPdfPasswordDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            PdfPasswordRoute(
                onNavigateUp = navController::navigateUp,
                onPreviewPdf = { artifact ->
                    navController.navigate(
                        ToolsPdfReaderDestination.route(artifact.filePath, artifact.fileName),
                    )
                },
            )
        }
        composable(
            route = ToolsPdfWatermarkDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            PdfWatermarkRoute(
                onNavigateUp = navController::navigateUp,
                onPreviewPdf = { artifact ->
                    navController.navigate(
                        ToolsPdfReaderDestination.route(artifact.filePath, artifact.fileName),
                    )
                },
            )
        }
        composable(
            route = ToolsPdfSignDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            PdfSignRoute(
                onNavigateUp = navController::navigateUp,
                onPreviewPdf = { artifact ->
                    navController.navigate(
                        ToolsPdfReaderDestination.route(artifact.filePath, artifact.fileName),
                    )
                },
                onManageSignatures = { navController.navigate(SignatureManageDestination.route) },
                onCreateSignature = { kind -> navController.navigate(SignatureCreateDestination.route(kind)) },
            )
        }
        composable(
            route = DocumentSignDestination.routePattern,
            arguments = listOf(
                navArgument(DocumentSignDestination.documentIdArgument) { type = NavType.StringType },
                navArgument(DocumentSignDestination.pageArgument) {
                    type = NavType.StringType
                    defaultValue = "0"
                },
            ),
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            DocumentSignRoute(
                onNavigateUp = navController::navigateUp,
                onManageSignatures = { navController.navigate(SignatureManageDestination.route) },
                onCreateSignature = { kind -> navController.navigate(SignatureCreateDestination.route(kind)) },
            )
        }
        composable(
            route = SignatureManageDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            SignatureManageRoute(
                onNavigateUp = navController::navigateUp,
                onCreate = { kind -> navController.navigate(SignatureCreateDestination.route(kind)) },
            )
        }
        composable(
            route = SignatureCreateDestination.routePattern,
            arguments = listOf(
                navArgument(SignatureCreateDestination.kindArgument) {
                    type = NavType.StringType
                    defaultValue = "signature"
                },
            ),
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            SignatureCreateRoute(onNavigateUp = navController::navigateUp)
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
            TopLevelDestinationShell(
                useTabletNavigationRail = useTabletNavigationRail,
                currentRoute = ScanlyDestination.Settings.route,
                onNavigate = { route -> navigateToTopLevel(navController, route) },
            ) {
                SettingsRoute(
                    onOpenFaqs = { navController.navigate(SettingsFaqDestination.route) },
                    onOpenStorage = { navController.navigate(SettingsStorageDestination.route) },
                    onOpenAppearance = { navController.navigate(SettingsAppearanceDestination.route) },
                    onOpenDetection = { navController.navigate(SettingsDetectionDestination.route) },
                    onOpenWidgets = { navController.navigate(SettingsWidgetsDestination.route) },
                    onOpenAbout = { navController.navigate(SettingsAboutDestination.route) },
                )
            }
        }
        composable(
            route = SettingsAppearanceDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(ScanlyDestination.Settings.route)
            }
            SettingsAppearanceRoute(
                onNavigateUp = navController::navigateUp,
                parentEntry = parentEntry,
            )
        }
        composable(
            route = SettingsDetectionDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(ScanlyDestination.Settings.route)
            }
            SettingsDetectionRoute(
                onNavigateUp = navController::navigateUp,
                onOpenModelBenchmark = {
                    navController.navigate(SettingsModelBenchmarkDestination.route)
                },
                parentEntry = parentEntry,
            )
        }
        composable(
            route = SettingsWidgetsDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(ScanlyDestination.Settings.route)
            }
            SettingsWidgetsRoute(
                onNavigateUp = navController::navigateUp,
                parentEntry = parentEntry,
            )
        }
        composable(
            route = SettingsAboutDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(ScanlyDestination.Settings.route)
            }
            SettingsAboutRoute(
                onNavigateUp = navController::navigateUp,
                appUpdateUiState = appUpdateUiState,
                onCheckForUpdates = onCheckForUpdates,
                onOpenLegalDocument = { documentType ->
                    navController.navigate(LegalDocumentDestination.route(documentType))
                },
                onOpenFaqs = { navController.navigate(SettingsFaqDestination.route) },
                onOpenLicenses = { navController.navigate(SettingsLicensesDestination.route) },
                parentEntry = parentEntry,
            )
        }
        composable(
            route = SettingsModelBenchmarkDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            ModelBenchmarkRoute(onNavigateUp = navController::navigateUp)
        }
        composable(
            route = SettingsFaqDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(ScanlyDestination.Settings.route)
            }
            SettingsFaqRoute(
                onNavigateUp = navController::navigateUp,
                parentEntry = parentEntry,
            )
        }
        composable(
            route = SettingsLicensesDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(ScanlyDestination.Settings.route)
            }
            SettingsLicensesRoute(
                onNavigateUp = navController::navigateUp,
                parentEntry = parentEntry,
            )
        }
        composable(
            route = SettingsStorageDestination.route,
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(ScanlyDestination.Settings.route)
            }
            SettingsStorageRoute(
                onNavigateUp = navController::navigateUp,
                parentEntry = parentEntry,
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
                    navController.navigate(PageImagePreviewDestination.route(pageId))
                },
                onOpenText = { pageIndex ->
                    navController.navigate(PageTextDestination.route(documentId, pageIndex))
                },
                onOpenSign = { pageIndex ->
                    navController.navigate(DocumentSignDestination.route(documentId, pageIndex))
                },
                onReplacePage = { pageId ->
                    navController.navigate(ScanSessionDestination.route(documentId, replacePageId = pageId))
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
                    // Prefer returning to an existing document detail under this session.
                    if (!navController.popBackStack(route = documentRoute, inclusive = false)) {
                        // No document detail under us (e.g. Home → Scan). Leave the scan
                        // session so deleting the document cannot resume a dead camera screen.
                        navController.navigate(documentRoute) {
                            popUpTo(ScanSessionDestination.routePattern) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
                onReplacementCompleted = { pageId ->
                    val editorRoute = PageEditorDestination.route(pageId)
                    if (!navController.popBackStack(route = editorRoute, inclusive = false)) {
                        val previewRoute = PageImagePreviewDestination.route(pageId)
                        if (!navController.popBackStack(route = previewRoute, inclusive = false)) {
                            navController.popBackStack()
                            navController.navigate(editorRoute) {
                                launchSingleTop = true
                            }
                        }
                    }
                },
            )
        }
        composable(
            route = PageImagePreviewDestination.routePattern,
            arguments = listOf(
                navArgument(PageImagePreviewDestination.pageIdArgument) {
                    type = NavType.StringType
                },
            ),
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            PageImagePreviewRoute(
                onNavigateUp = navController::navigateUp,
                onEditPage = { pageId ->
                    navController.navigate(PageEditorDestination.route(pageId))
                },
                onRetakePage = { documentId, pageId ->
                    navController.navigate(
                        ScanSessionDestination.route(documentId, replacePageId = pageId),
                    )
                },
            )
        }
        composable(
            route = PageEditorDestination.routePattern,
            arguments = listOf(
                navArgument(PageEditorDestination.pageIdArgument) {
                    type = NavType.StringType
                },
            ),
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            PageEditorRoute(
                onNavigateUp = navController::navigateUp,
                onOpenCrop = { pageId ->
                    navController.navigate(PageCropDestination.route(pageId))
                },
                onRetakePage = { documentId, pageId ->
                    navController.navigate(ScanSessionDestination.route(documentId, replacePageId = pageId))
                },
            )
        }
        composable(
            route = PageCropDestination.routePattern,
            arguments = listOf(
                navArgument(PageCropDestination.pageIdArgument) {
                    type = NavType.StringType
                },
            ),
            enterTransition = { detailPushEnter() },
            exitTransition = { detailPushExit() },
            popEnterTransition = { detailPopEnter() },
            popExitTransition = { detailPopExit() },
        ) {
            PageCropRoute(
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

private const val NavEnterDuration = 220
private const val NavExitDuration = 110
private const val NavPopDuration = 180
private const val NavMotionDistanceDivisor = 24

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelEnter(): EnterTransition =
    EnterTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelExit(): ExitTransition =
    ExitTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelPopEnter(): EnterTransition =
    EnterTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelPopExit(): ExitTransition =
    ExitTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPushEnter(): EnterTransition =
    fadeIn(
        animationSpec = tween(
            durationMillis = NavEnterDuration,
            delayMillis = 20,
            easing = FastOutSlowInEasing,
        ),
    ) + slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(
            durationMillis = NavEnterDuration,
            easing = FastOutSlowInEasing,
        ),
        initialOffset = { fullDistance -> fullDistance / NavMotionDistanceDivisor },
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPushExit(): ExitTransition =
    fadeOut(animationSpec = tween(NavExitDuration))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPopEnter(): EnterTransition =
    fadeIn(
        animationSpec = tween(
            durationMillis = NavPopDuration,
            delayMillis = 20,
            easing = FastOutSlowInEasing,
        ),
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.detailPopExit(): ExitTransition =
    fadeOut(
        animationSpec = tween(
            durationMillis = NavPopDuration,
            easing = FastOutSlowInEasing,
        ),
    ) + slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(
            durationMillis = NavPopDuration,
            easing = FastOutSlowInEasing,
        ),
        targetOffset = { fullDistance -> fullDistance / NavMotionDistanceDivisor },
    )
