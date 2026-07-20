package `in`.c1ph3rj.scanly.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Handyman
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
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
import `in`.c1ph3rj.scanly.feature.editor.PageCropDestination
import `in`.c1ph3rj.scanly.feature.editor.PageCropRoute
import `in`.c1ph3rj.scanly.feature.editor.PageEditorDestination
import `in`.c1ph3rj.scanly.feature.editor.PageEditorRoute
import `in`.c1ph3rj.scanly.feature.home.HomeRoute
import `in`.c1ph3rj.scanly.feature.library.LibraryRoute
import `in`.c1ph3rj.scanly.feature.library.GroupDetailRoute
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
import `in`.c1ph3rj.scanly.feature.tools.qr.QrToolRoute
import `in`.c1ph3rj.scanly.feature.tools.qr.QrToolMode
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource

@Composable
internal fun ScanlyNavHostContent(
    navController: NavHostController,
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    useTabletNavigationRail: Boolean,
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
            TopLevelDestinationShell(
                useTabletNavigationRail = useTabletNavigationRail,
                currentRoute = ScanlyDestination.Home.route,
                onNavigate = { route -> navigateToTopLevel(navController, route) },
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
                    if (!navController.popBackStack(route = documentRoute, inclusive = false)) {
                        navController.navigate(documentRoute) {
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
