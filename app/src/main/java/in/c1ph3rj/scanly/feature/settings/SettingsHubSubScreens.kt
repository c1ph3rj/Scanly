package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.ui.res.pluralStringResource
import android.content.ActivityNotFoundException

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
import androidx.compose.ui.platform.UriHandler
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
import `in`.c1ph3rj.scanly.R
import androidx.compose.ui.res.stringResource
import `in`.c1ph3rj.scanly.core.common.appString

internal const val DEVELOPER_PORTFOLIO_URL = "https://c1ph3rj.in"
internal const val PROJECT_WEBSITE_URL = "https://www.usescanly.co.in"
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
        title = stringResource(R.string.settings_hub_sub_screens_appearance),
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
                SettingsGroup(title = stringResource(R.string.settings_hub_sub_screens_theme)) {
                    ThemeModeSelector(
                        selectedMode = themeMode,
                        onThemeModeSelected = onThemeModeSelected,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_hub_sub_screens_pure_black),
                        subtitle = if (themeMode == ThemeMode.LIGHT) {
                            stringResource(R.string.settings_hub_sub_screens_takes_effect_in_dark_theme)
                        } else {
                            stringResource(R.string.settings_hub_sub_screens_true_black_surfaces_saves_battery)
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    SettingsDetectionScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
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
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onLiveDetectionModelSelected: (DocumentCornerModel) -> Unit,
    onPostProcessingModelSelected: (DocumentCornerModel) -> Unit,
    onAutomaticModelSelectionChanged: (Boolean) -> Unit,
    onDocumentGateEnabledChanged: (Boolean) -> Unit,
    onOpenModelBenchmark: () -> Unit,
) {
    val window = rememberWindowSizeInfo()
    ScanlyDetailScaffold(
        title = stringResource(R.string.settings_hub_sub_screens_document_detection),
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
            item("models") {
                SettingsGroup(title = stringResource(R.string.settings_hub_sub_screens_models)) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_hub_sub_screens_automatic_model_selection),
                        subtitle = when {
                            uiState.isCalibratingModels -> stringResource(R.string.settings_hub_sub_screens_testing_model_speed_on_this)
                            uiState.automaticLiveModel != null &&
                                uiState.automaticPostProcessingModel != null ->
                                stringResource(R.string.settings_hub_sub_screens_live_processing, uiState.automaticLiveModel.displayName, uiState.automaticPostProcessingModel.displayName)
                            else -> stringResource(R.string.settings_hub_sub_screens_choose_the_best_models_for)
                        },
                        checked = uiState.automaticModelSelectionEnabled,
                        onCheckedChange = onAutomaticModelSelectionChanged,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ModelSelector(
                        title = stringResource(R.string.settings_hub_sub_screens_live_preview_model),
                        subtitle = if (uiState.automaticModelSelectionEnabled) {
                            stringResource(R.string.settings_hub_sub_screens_selected_automatically_for_responsive_preview)
                        } else {
                            stringResource(R.string.settings_hub_sub_screens_used_while_the_camera_preview)
                        },
                        selected = uiState.automaticLiveModel ?: uiState.liveDetectionModel,
                        enabled = !uiState.automaticModelSelectionEnabled,
                        onSelected = onLiveDetectionModelSelected,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ModelSelector(
                        title = stringResource(R.string.settings_hub_sub_screens_post_processing_model),
                        subtitle = if (uiState.automaticModelSelectionEnabled) {
                            stringResource(R.string.settings_hub_sub_screens_selected_automatically_for_higher_accuracy)
                        } else {
                            stringResource(R.string.settings_hub_sub_screens_used_after_camera_or_gallery)
                        },
                        selected = uiState.automaticPostProcessingModel
                            ?: uiState.postProcessingModel,
                        enabled = !uiState.automaticModelSelectionEnabled,
                        onSelected = onPostProcessingModelSelected,
                    )
                }
            }
            item("gate") {
                SettingsGroup(title = stringResource(R.string.settings_hub_sub_screens_capture_quality)) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_hub_sub_screens_physical_document_gate),
                        subtitle = stringResource(R.string.settings_hub_sub_screens_reject_screens_and_non_documents),
                        checked = uiState.documentGateEnabled,
                        onCheckedChange = onDocumentGateEnabledChanged,
                    )
                }
            }
            item("tools") {
                SettingsGroup(title = stringResource(R.string.settings_hub_sub_screens_tools)) {
                    SettingsNavigationRow(
                        icon = Icons.Filled.Speed,
                        title = stringResource(R.string.model_benchmark_model_benchmark),
                        subtitle = stringResource(R.string.settings_hub_sub_screens_compare_all_models_on_local),
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
                    appString(R.string.settings_hub_sub_screens_your_launcher_doesn_t_support),
                )
            }
            ScanlyWidgetSupport.PinResult.Failed -> scope.launch {
                snackbarHostState.showSnackbar(appString(R.string.settings_hub_sub_screens_could_not_open_the_add))
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
        stringResource(R.string.settings_hub_sub_screens_tap_to_add_a_widget)
    } else {
        stringResource(R.string.settings_hub_sub_screens_long_press_home_screen_widgets)
    }
    ScanlyDetailScaffold(
        title = stringResource(R.string.settings_hub_sub_screens_widgets),
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
                SettingsGroup(title = stringResource(R.string.settings_hub_sub_screens_home_screen)) {
                    SettingsNavigationRow(
                        icon = Icons.Filled.Widgets,
                        title = stringResource(R.string.settings_hub_sub_screens_actions_bar),
                        subtitle = stringResource(R.string.settings_hub_sub_screens_scan_import_qr_library),
                        onClick = onAddActionsWidget,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Filled.DocumentScanner,
                        title = stringResource(R.string.settings_hub_sub_screens_scan_widget),
                        subtitle = stringResource(R.string.settings_hub_sub_screens_start_a_new_document_scan),
                        onClick = onAddScanWidget,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Filled.QrCode2,
                        title = stringResource(R.string.settings_hub_sub_screens_qr_widget),
                        subtitle = stringResource(R.string.settings_hub_sub_screens_open_the_qr_tool),
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
        onOpenPortfolio = { uriHandler.openUriSafely(DEVELOPER_PORTFOLIO_URL) },
        onOpenFaqs = onOpenFaqs,
        onOpenLegalDocument = onOpenLegalDocument,
        onOpenLicenses = onOpenLicenses,
        onOpenWebsite = { uriHandler.openUriSafely(PROJECT_WEBSITE_URL) },
        onContactSupport = { uriHandler.openUriSafely("mailto:$SUPPORT_EMAIL") },
    )
}

/** openUri throws when no app can handle the link (e.g. mailto: with no mail app). */
internal fun UriHandler.openUriSafely(uri: String) {
    try {
        openUri(uri)
    } catch (_: ActivityNotFoundException) {
        // Nothing to open the link with; the row stays inert rather than crashing.
    }
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
        title = stringResource(R.string.settings_hub_sub_screens_about),
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
                SettingsGroup(title = stringResource(R.string.shared_scanly)) {
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
                SettingsGroup(title = stringResource(R.string.settings_hub_sub_screens_help)) {
                    if (faqCount > 0) {
                        SettingsNavigationRow(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            title = stringResource(R.string.settings_detail_screens_help_faq),
                            subtitle = pluralStringResource(R.plurals.common_topics, faqCount, faqCount),
                            onClick = onOpenFaqs,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    SettingsLinkRow(
                        icon = Icons.Filled.Email,
                        title = stringResource(R.string.settings_hub_sub_screens_contact_support),
                        onClick = onContactSupport,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsLinkRow(
                        icon = Icons.Filled.Public,
                        title = stringResource(R.string.settings_hub_sub_screens_project_website),
                        onClick = onOpenWebsite,
                    )
                }
            }
            item("legal") {
                SettingsGroup(title = stringResource(R.string.settings_hub_sub_screens_legal)) {
                    SettingsNavigationRow(
                        icon = Icons.Filled.Policy,
                        title = stringResource(R.string.settings_hub_sub_screens_privacy_policy),
                        onClick = { onOpenLegalDocument(LegalDocumentType.Privacy) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Filled.Gavel,
                        title = stringResource(R.string.settings_hub_sub_screens_terms_conditions),
                        onClick = { onOpenLegalDocument(LegalDocumentType.Terms) },
                    )
                    if (licenseCount > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsNavigationRow(
                            icon = Icons.Filled.Code,
                            title = stringResource(R.string.settings_detail_screens_open_source),
                            subtitle = pluralStringResource(R.plurals.common_libraries, licenseCount, licenseCount),
                            onClick = onOpenLicenses,
                        )
                    }
                }
            }
        }
    }
}
