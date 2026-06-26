package `in`.c1ph3rj.scanly

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.feature.update.AppUpdateCheckTrigger
import `in`.c1ph3rj.scanly.feature.update.AppUpdateDialog
import `in`.c1ph3rj.scanly.feature.update.AppUpdateEvent
import `in`.c1ph3rj.scanly.feature.update.AppUpdateViewModel
import `in`.c1ph3rj.scanly.navigation.ScanlyNavHost
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanlyApp()
        }
    }
}

@Composable
private fun ScanlyApp() {
    val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel()
    val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()
    val updateUiState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = themeMode.resolveDarkTheme(systemDark)
    val navController = rememberNavController()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        appUpdateViewModel.retryPendingInstall()
    }

    LaunchedEffect(appUpdateViewModel) {
        appUpdateViewModel.events.collect { event ->
            when (event) {
                is AppUpdateEvent.ShowMessage -> {
                    android.widget.Toast.makeText(
                        context,
                        event.message,
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }

                is AppUpdateEvent.OpenUri -> uriHandler.openUri(event.uri)

                is AppUpdateEvent.InstallApk -> {
                    context.startActivity(event.intent)
                }

                AppUpdateEvent.RequestInstallPermission -> {
                    installPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        },
                    )
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, appUpdateViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    appUpdateViewModel.checkForUpdates(AppUpdateCheckTrigger.Automatic)
                }

                Lifecycle.Event.ON_RESUME -> {
                    appUpdateViewModel.retryPendingInstall()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    DisposableEffect(isDarkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { isDarkTheme },
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { isDarkTheme }
        )
        onDispose {}
    }

    ScanlyTheme(
        darkTheme = isDarkTheme,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ScanlyNavHost(
                navController = navController,
                appUpdateUiState = updateUiState,
                onCheckForUpdates = {
                    appUpdateViewModel.checkForUpdates(AppUpdateCheckTrigger.Manual)
                },
            )

            updateUiState.dialogCheckResult?.let { checkResult ->
                AppUpdateDialog(
                    checkResult = checkResult,
                    isDownloading = updateUiState.isDownloadingApk,
                    onDismiss = appUpdateViewModel::dismissUpdateDialog,
                    onDownload = appUpdateViewModel::downloadRelease,
                )
            }
        }
    }
}


private fun ThemeMode.resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
