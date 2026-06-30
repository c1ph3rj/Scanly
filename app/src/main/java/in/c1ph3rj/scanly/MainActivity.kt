package `in`.c1ph3rj.scanly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.feature.onboarding.OnboardingScreen
import `in`.c1ph3rj.scanly.feature.onboarding.OnboardingStatus
import `in`.c1ph3rj.scanly.feature.onboarding.OnboardingViewModel
import `in`.c1ph3rj.scanly.feature.update.AppUpdateCheckTrigger
import `in`.c1ph3rj.scanly.feature.update.AppUpdateDialog
import `in`.c1ph3rj.scanly.feature.update.AppUpdateEvent
import `in`.c1ph3rj.scanly.feature.update.AppUpdateViewModel
import `in`.c1ph3rj.scanly.feature.update.FlexibleUpdateSnackbarHost
import `in`.c1ph3rj.scanly.navigation.ScanlyNavHost
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.unit.dp

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
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()
    val updateUiState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val onboardingUiState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = themeMode.resolveDarkTheme(systemDark)
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as ComponentActivity
    val flexibleUpdateSnackbarHostState = remember { SnackbarHostState() }
    val playUpdateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        appUpdateViewModel.onPlayUpdateFlowResult(result.resultCode)
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

                is AppUpdateEvent.LaunchPlayUpdate -> {
                    appUpdateViewModel.launchPlayUpdate(
                        activity = activity,
                        launcher = playUpdateLauncher,
                        updateType = event.updateType,
                    )
                }

                AppUpdateEvent.ResumePlayUpdate -> {
                    appUpdateViewModel.resumePlayUpdate(
                        activity = activity,
                        launcher = playUpdateLauncher,
                    )
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, appUpdateViewModel, onboardingUiState.status) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (onboardingUiState.status == OnboardingStatus.COMPLETE) {
                        appUpdateViewModel.checkForUpdates(AppUpdateCheckTrigger.Automatic)
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (onboardingUiState.status == OnboardingStatus.COMPLETE) {
                        appUpdateViewModel.resumePlayUpdateIfNeeded()
                    }
                }

                else -> Unit
            }
        }
        if (onboardingUiState.status == OnboardingStatus.COMPLETE) {
            lifecycleOwner.lifecycle.addObserver(observer)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
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
        Scaffold(
            snackbarHost = {
                FlexibleUpdateSnackbarHost(
                    hostState = flexibleUpdateSnackbarHostState,
                    visible = updateUiState.flexibleUpdateDownloaded,
                    promptToken = updateUiState.flexibleUpdatePromptToken,
                    onRestartNow = appUpdateViewModel::completeFlexibleUpdate,
                    modifier = Modifier.padding(16.dp),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
            ),
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AnimatedContent(
                    targetState = onboardingUiState.status,
                    transitionSpec = {
                        fadeIn(tween(durationMillis = 320)) togetherWith
                            fadeOut(tween(durationMillis = 220))
                    },
                    label = "first_run_content",
                ) { onboardingStatus ->
                    when (onboardingStatus) {
                        OnboardingStatus.LOADING -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        )

                        OnboardingStatus.REQUIRED -> OnboardingScreen(
                            uiState = onboardingUiState,
                            onComplete = onboardingViewModel::completeOnboarding,
                            onDismissError = onboardingViewModel::dismissError,
                        )

                        OnboardingStatus.COMPLETE -> ScanlyNavHost(
                            navController = navController,
                            appUpdateUiState = updateUiState,
                            onCheckForUpdates = {
                                appUpdateViewModel.checkForUpdates(AppUpdateCheckTrigger.Manual)
                            },
                        )
                    }
                }

                if (onboardingUiState.status == OnboardingStatus.COMPLETE) {
                    updateUiState.dialogCheckResult?.let { checkResult ->
                        AppUpdateDialog(
                            checkResult = checkResult,
                            onDismiss = appUpdateViewModel::dismissUpdateDialog,
                            onUpdate = appUpdateViewModel::startUpdate,
                        )
                    }
                }
            }
        }
    }
}


private fun ThemeMode.resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
