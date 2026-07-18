package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import `in`.c1ph3rj.scanly.feature.update.AppUpdateUiState
import `in`.c1ph3rj.scanly.feature.widget.ScanlyWidgetSupport
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

internal const val DEVELOPER_PORTFOLIO_URL = "https://c1ph3rj.in"
internal const val PROJECT_WEBSITE_URL = "https://scanly.c1ph3rj.in"
internal const val SUPPORT_EMAIL = "info@c1ph3rj.in"

@Composable
fun SettingsAppearanceRoute(
    onNavigateUp: () -> Unit,
    parentEntry: NavBackStackEntry,
    viewModel: SettingsViewModel = hiltViewModel(parentEntry),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsAppearanceScreen(
        themeMode = uiState.themeMode,
        pureBlackEnabled = uiState.pureBlackEnabled,
        onNavigateUp = onNavigateUp,
        onThemeModeSelected = viewModel::setThemeMode,
        onPureBlackEnabledChanged = viewModel::setPureBlackEnabled,
    )
}

@Composable
fun SettingsAppearanceScreen(
    themeMode: ThemeMode,
    pureBlackEnabled: Boolean,
    onNavigateUp: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onPureBlackEnabledChanged: (Boolean) -> Unit,
) {
    val window = rememberWindowSizeInfo()
    ScanlyDetailScaffold(
        title = "Appearance",
        onNavigateUp = onNavigateUp,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = window.horizontalPadding,
                end = window.horizontalPadding,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("theme") {
                SettingsGroup(title = "Theme") {
                    ThemeModeSelector(
                        selectedMode = themeMode,
                        onThemeModeSelected = onThemeModeSelected,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        title = "Pure black",
                        subtitle = if (themeMode == ThemeMode.LIGHT) {
                            "Takes effect in dark theme · saves battery on AMOLED screens"
                        } else {
                            "True black surfaces · saves battery on AMOLED screens"
                        },
                        checked = pureBlackEnabled,
                        onCheckedChange = onPureBlackEnabledChanged,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDetectionRoute(
    onNavigateUp: () -> Unit,
    onOpenModelBenchmark: () -> Unit,
    parentEntry: NavBackStackEntry,
    viewModel: SettingsViewModel = hiltViewModel(parentEntry),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsDetectionScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onLiveDetectionModelSelected = viewModel::setLiveDetectionModel,
        onPostProcessingModelSelected = viewModel::setPostProcessingModel,
        onAutomaticModelSelectionChanged = viewModel::setAutomaticModelSelection,
        onDocumentGateEnabledChanged = viewModel::setDocumentGateEnabled,
        onOpenModelBenchmark = onOpenModelBenchmark,
    )
}

@Composable
fun SettingsDetectionScreen(
    uiState: SettingsUiState,
    onNavigateUp: () -> Unit,
    onLiveDetectionModelSelected: (DocumentCornerModel) -> Unit,
    onPostProcessingModelSelected: (DocumentCornerModel) -> Unit,
    onAutomaticModelSelectionChanged: (Boolean) -> Unit,
    onDocumentGateEnabledChanged: (Boolean) -> Unit,
    onOpenModelBenchmark: () -> Unit,
) {
    val window = rememberWindowSizeInfo()
    ScanlyDetailScaffold(
        title = "Document detection",
        onNavigateUp = onNavigateUp,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = window.horizontalPadding,
                end = window.horizontalPadding,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("models") {
                SettingsGroup(title = "Models") {
                    SettingsToggleRow(
                        title = "Automatic model selection",
                        subtitle = when {
                            uiState.isCalibratingModels -> "Testing model speed on this device…"
                            uiState.automaticLiveModel != null &&
                                uiState.automaticPostProcessingModel != null ->
                                "Live: ${uiState.automaticLiveModel.displayName} · " +
                                    "Processing: ${uiState.automaticPostProcessingModel.displayName}"
                            else -> "Choose the best models for this device"
                        },
                        checked = uiState.automaticModelSelectionEnabled,
                        onCheckedChange = onAutomaticModelSelectionChanged,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ModelSelector(
                        title = "Live preview model",
                        subtitle = if (uiState.automaticModelSelectionEnabled) {
                            "Selected automatically for responsive preview"
                        } else {
                            "Used while the camera preview is running"
                        },
                        selected = uiState.automaticLiveModel ?: uiState.liveDetectionModel,
                        enabled = !uiState.automaticModelSelectionEnabled,
                        onSelected = onLiveDetectionModelSelected,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ModelSelector(
                        title = "Post-processing model",
                        subtitle = if (uiState.automaticModelSelectionEnabled) {
                            "Selected automatically for higher accuracy"
                        } else {
                            "Used after camera or gallery capture"
                        },
                        selected = uiState.automaticPostProcessingModel
                            ?: uiState.postProcessingModel,
                        enabled = !uiState.automaticModelSelectionEnabled,
                        onSelected = onPostProcessingModelSelected,
                    )
                }
            }
            item("gate") {
                SettingsGroup(title = "Capture quality") {
                    SettingsToggleRow(
                        title = "Physical-document gate",
                        subtitle = "Reject screens and non-documents before edge detection",
                        checked = uiState.documentGateEnabled,
                        onCheckedChange = onDocumentGateEnabledChanged,
                    )
                }
            }
            item("tools") {
                SettingsGroup(title = "Tools") {
                    SettingsNavigationRow(
                        icon = Icons.Filled.Speed,
                        title = "Model benchmark",
                        subtitle = "Compare all models on local images",
                        onClick = onOpenModelBenchmark,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsWidgetsRoute(
    onNavigateUp: () -> Unit,
    parentEntry: NavBackStackEntry,
    viewModel: SettingsViewModel = hiltViewModel(parentEntry),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pinSupported = remember(context) { ScanlyWidgetSupport.isPinSupported(context) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    fun requestPin(kind: ScanlyWidgetSupport.WidgetKind) {
        when (ScanlyWidgetSupport.requestPin(context, kind)) {
            ScanlyWidgetSupport.PinResult.Requested -> Unit
            ScanlyWidgetSupport.PinResult.Unsupported -> scope.launch {
                snackbarHostState.showSnackbar(
                    "Your launcher doesn’t support adding widgets from the app. " +
                        "Long-press the home screen and pick Scanly from Widgets.",
                )
            }
            ScanlyWidgetSupport.PinResult.Failed -> scope.launch {
                snackbarHostState.showSnackbar("Could not open the add-widget dialog.")
            }
        }
    }

    SettingsWidgetsScreen(
        pinWidgetsSupported = pinSupported,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onAddActionsWidget = { requestPin(ScanlyWidgetSupport.WidgetKind.Actions) },
        onAddScanWidget = { requestPin(ScanlyWidgetSupport.WidgetKind.Scan) },
        onAddQrWidget = { requestPin(ScanlyWidgetSupport.WidgetKind.Qr) },
    )
}

@Composable
fun SettingsWidgetsScreen(
    pinWidgetsSupported: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onAddActionsWidget: () -> Unit,
    onAddScanWidget: () -> Unit,
    onAddQrWidget: () -> Unit,
) {
    val window = rememberWindowSizeInfo()
    val hint = if (pinWidgetsSupported) {
        "Tap to add a widget to your home screen"
    } else {
        "Long-press home screen → Widgets → Scanly"
    }
    ScanlyDetailScaffold(
        title = "Widgets",
        onNavigateUp = onNavigateUp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = window.horizontalPadding,
                end = window.horizontalPadding,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("widgets") {
                SettingsGroup(title = "Home screen") {
                    SettingsNavigationRow(
                        icon = Icons.Filled.Widgets,
                        title = "Actions bar",
                        subtitle = "Scan · Import · QR · Library",
                        onClick = onAddActionsWidget,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Filled.DocumentScanner,
                        title = "Scan widget",
                        subtitle = "Start a new document scan",
                        onClick = onAddScanWidget,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Filled.QrCode2,
                        title = "QR widget",
                        subtitle = "Open the QR tool",
                        onClick = onAddQrWidget,
                    )
                }
            }
            item("hint") {
                androidx.compose.material3.Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsAboutRoute(
    onNavigateUp: () -> Unit,
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onOpenLegalDocument: (LegalDocumentType) -> Unit,
    onOpenFaqs: () -> Unit,
    onOpenLicenses: () -> Unit,
    parentEntry: NavBackStackEntry,
    viewModel: SettingsViewModel = hiltViewModel(parentEntry),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    SettingsAboutScreen(
        versionLabel = uiState.content?.appVersionLabel,
        faqCount = uiState.content?.faqs?.size ?: 0,
        licenseCount = uiState.content?.licenses?.size ?: 0,
        appUpdateUiState = appUpdateUiState,
        onNavigateUp = onNavigateUp,
        onCheckForUpdates = onCheckForUpdates,
        onOpenPortfolio = { uriHandler.openUri(DEVELOPER_PORTFOLIO_URL) },
        onOpenFaqs = onOpenFaqs,
        onOpenLegalDocument = onOpenLegalDocument,
        onOpenLicenses = onOpenLicenses,
        onOpenWebsite = { uriHandler.openUri(PROJECT_WEBSITE_URL) },
        onContactSupport = { uriHandler.openUri("mailto:$SUPPORT_EMAIL") },
    )
}

@Composable
fun SettingsAboutScreen(
    versionLabel: String?,
    faqCount: Int,
    licenseCount: Int,
    appUpdateUiState: AppUpdateUiState,
    onNavigateUp: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onOpenFaqs: () -> Unit,
    onOpenLegalDocument: (LegalDocumentType) -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenWebsite: () -> Unit,
    onContactSupport: () -> Unit,
) {
    val window = rememberWindowSizeInfo()
    ScanlyDetailScaffold(
        title = "About",
        onNavigateUp = onNavigateUp,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = window.horizontalPadding,
                end = window.horizontalPadding,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("app") {
                SettingsGroup(title = "Scanly") {
                    AboutHero(
                        versionLabel = versionLabel,
                        onOpenPortfolio = onOpenPortfolio,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsUpdateRow(
                        appUpdateUiState = appUpdateUiState,
                        onCheckForUpdates = onCheckForUpdates,
                    )
                }
            }
            item("help") {
                SettingsGroup(title = "Help") {
                    if (faqCount > 0) {
                        SettingsNavigationRow(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            title = "Help & FAQ",
                            subtitle = "$faqCount topics",
                            onClick = onOpenFaqs,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    SettingsLinkRow(
                        icon = Icons.Filled.Email,
                        title = "Contact support",
                        onClick = onContactSupport,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsLinkRow(
                        icon = Icons.Filled.Public,
                        title = "Project website",
                        onClick = onOpenWebsite,
                    )
                }
            }
            item("legal") {
                SettingsGroup(title = "Legal") {
                    SettingsNavigationRow(
                        icon = Icons.Filled.Policy,
                        title = "Privacy Policy",
                        onClick = { onOpenLegalDocument(LegalDocumentType.Privacy) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Filled.Gavel,
                        title = "Terms & Conditions",
                        onClick = { onOpenLegalDocument(LegalDocumentType.Terms) },
                    )
                    if (licenseCount > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsNavigationRow(
                            icon = Icons.Filled.Code,
                            title = "Open source",
                            subtitle = "$licenseCount libraries",
                            onClick = onOpenLicenses,
                        )
                    }
                }
            }
        }
    }
}
