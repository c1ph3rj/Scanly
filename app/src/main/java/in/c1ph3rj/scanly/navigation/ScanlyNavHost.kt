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
fun ScanlyNavHost(
    navController: NavHostController,
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier,
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
                    useTabletNavigationRail = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
