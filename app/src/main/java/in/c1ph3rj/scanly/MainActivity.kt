package `in`.c1ph3rj.scanly

import android.content.Intent
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionDestination
import `in`.c1ph3rj.scanly.feature.components.ScanlyImportProgressOverlay
import `in`.c1ph3rj.scanly.feature.document.DocumentDestination
import `in`.c1ph3rj.scanly.feature.launch.LaunchActionEvent
import `in`.c1ph3rj.scanly.feature.launch.LaunchActionViewModel
import `in`.c1ph3rj.scanly.feature.widget.ScanlyWidgetSupport
import `in`.c1ph3rj.scanly.feature.onboarding.OnboardingScreen
import `in`.c1ph3rj.scanly.feature.onboarding.OnboardingStatus
import `in`.c1ph3rj.scanly.feature.onboarding.OnboardingViewModel
import `in`.c1ph3rj.scanly.feature.update.AppUpdateCheckTrigger
import `in`.c1ph3rj.scanly.feature.update.AppUpdateDialog
import `in`.c1ph3rj.scanly.feature.update.AppUpdateEvent
import `in`.c1ph3rj.scanly.feature.update.AppUpdateViewModel
import `in`.c1ph3rj.scanly.feature.update.FlexibleUpdateSnackbarHost
import `in`.c1ph3rj.scanly.navigation.ScanlyDestination
import `in`.c1ph3rj.scanly.navigation.ScanlyNavHost
import `in`.c1ph3rj.scanly.navigation.ToolsQrDestination
import `in`.c1ph3rj.scanly.navigation.navigateScanlyTopLevel
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val externalIntents = MutableSharedFlow<Intent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanlyApp(
                coldStartIntent = intent,
                externalIntents = externalIntents,
            )
        }
        // Defer widget rebind until after first frame so cold start is not blocked on binder work.
        window.decorView.post {
            ScanlyWidgetSupport.refreshAll(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalIntents.tryEmit(intent)
    }
}

@Composable
private fun ScanlyApp(
    coldStartIntent: Intent?,
    externalIntents: MutableSharedFlow<Intent>,
) {
    val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val launchActionViewModel: LaunchActionViewModel = hiltViewModel()
    val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()
    val pureBlackEnabled by appSettingsViewModel.pureBlackEnabled.collectAsStateWithLifecycle()
    val updateUiState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val onboardingUiState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
    val launchImportProgress by launchActionViewModel.importProgress.collectAsStateWithLifecycle()
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
    val importImagesLauncher = rememberLauncherForActivityResult(
        contract = ImageImportSupport.pickMultipleVisualMediaContract(),
    ) { uris ->
        launchActionViewModel.importImagesAsDocument(uris)
    }

    LaunchedEffect(launchActionViewModel, coldStartIntent) {
        launchActionViewModel.onColdStartIntent(coldStartIntent)
    }

    LaunchedEffect(launchActionViewModel, externalIntents) {
        externalIntents.collect { intent ->
            launchActionViewModel.onNewIntent(intent)
        }
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
        pureBlack = pureBlackEnabled,
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

                        OnboardingStatus.COMPLETE -> {
                            ScanlyNavHost(
                                navController = navController,
                                appUpdateUiState = updateUiState,
                                onCheckForUpdates = {
                                    appUpdateViewModel.checkForUpdates(AppUpdateCheckTrigger.Manual)
                                },
                            )
                            // Start redirects only after NavHost is composed so Scan/Import
                            // skip the in-app name dialog and go straight to camera / picker.
                            LaunchedEffect(launchActionViewModel, navController) {
                                launch {
                                    launchActionViewModel.events.collect { event ->
                                        handleLaunchActionEvent(
                                            event = event,
                                            navController = navController,
                                            onRequestImport = {
                                                importImagesLauncher.launch(
                                                    ImageImportSupport.createPickRequest(),
                                                )
                                            },
                                            onShowMessage = { message ->
                                                android.widget.Toast.makeText(
                                                    context,
                                                    message,
                                                    android.widget.Toast.LENGTH_LONG,
                                                ).show()
                                            },
                                        )
                                    }
                                }
                                yield()
                                launchActionViewModel.onAppReady()
                            }
                        }
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

                if (launchImportProgress.active) {
                    ScanlyImportProgressOverlay(
                        current = launchImportProgress.current,
                        total = launchImportProgress.total,
                        stageLabel = launchImportProgress.stageLabel,
                    )
                }
            }
        }
    }
}

private fun handleLaunchActionEvent(
    event: LaunchActionEvent,
    navController: NavHostController,
    onRequestImport: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    when (event) {
        is LaunchActionEvent.OpenScanSession -> {
            navController.navigate(ScanSessionDestination.route(event.documentId))
        }

        is LaunchActionEvent.OpenDocument -> {
            navController.navigate(DocumentDestination.route(event.documentId))
        }

        LaunchActionEvent.OpenLibrary -> {
            navigateScanlyTopLevel(navController, ScanlyDestination.Library.route)
        }

        LaunchActionEvent.OpenQr -> {
            navigateScanlyTopLevel(navController, ScanlyDestination.Tools.route)
            navController.navigate(ToolsQrDestination.route("scan"))
        }

        LaunchActionEvent.RequestImportPicker -> onRequestImport()

        is LaunchActionEvent.ShowMessage -> onShowMessage(event.message)
    }
}

private fun ThemeMode.resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
