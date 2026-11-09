package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.ui.res.pluralStringResource
import `in`.c1ph3rj.scanly.feature.library.retentionLabel
import `in`.c1ph3rj.scanly.domain.trash.TrashKind
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionSupport
import `in`.c1ph3rj.scanly.feature.components.ScanlyTabScreenHeader
import `in`.c1ph3rj.scanly.feature.rating.currentRatingChannel
import `in`.c1ph3rj.scanly.feature.rating.launchRating
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import `in`.c1ph3rj.scanly.R
import androidx.compose.ui.res.stringResource
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.core.common.AppLocales
import androidx.compose.material.icons.filled.Language

@Composable
fun SettingsRoute(
    onOpenFaqs: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenDetection: () -> Unit,
    onOpenTextRecognition: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLanguage: () -> Unit = {},
    onOpenPip: () -> Unit = {},
    onOpenRecentlyDeleted: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    SettingsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onOpenAppearance = onOpenAppearance,
        onOpenLanguage = onOpenLanguage,
        onOpenPip = onOpenPip,
        onOpenStorage = onOpenStorage,
        onOpenRecentlyDeleted = onOpenRecentlyDeleted,
        onOpenDetection = onOpenDetection,
        onOpenTextRecognition = onOpenTextRecognition,
        onOpenWidgets = onOpenWidgets,
        onOpenFaqs = onOpenFaqs,
        onOpenAbout = onOpenAbout,
        onDismissRatingCard = viewModel::dismissRatingCard,
        onRateApp = {
            viewModel.onRateApp()
            val activity = CameraPermissionSupport.findActivity(context)
            if (activity != null) {
                scope.launch {
                    launchRating(activity, currentRatingChannel())
                    snackbarHostState.showSnackbar(appString(R.string.settings_thanks_for_supporting_scanly))
                }
            }
        },
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onOpenAppearance: () -> Unit,
    onOpenLanguage: () -> Unit = {},
    onOpenPip: () -> Unit = {},
    onOpenStorage: () -> Unit,
    onOpenDetection: () -> Unit,
    onOpenTextRecognition: () -> Unit = {},
    onOpenWidgets: () -> Unit,
    onOpenFaqs: () -> Unit,
    onOpenAbout: () -> Unit,
    onRateApp: () -> Unit,
    onOpenRecentlyDeleted: () -> Unit = {},
    onDismissRatingCard: () -> Unit,
) {
    val content = uiState.content
    val windowSizeInfo = rememberWindowSizeInfo()
    val pipPreferences = `in`.c1ph3rj.scanly.feature.pip.LocalPipPreferences.current
    val appearanceSubtitle = buildString {
        append(uiState.themeMode.settingsLabel())
        if (uiState.pureBlackEnabled && uiState.themeMode != ThemeMode.LIGHT) {
            append(stringResource(R.string.settings_pure_black))
        }
    }
    val languageContext = LocalContext.current
    val languageSubtitle = remember(languageContext) {
        AppLocales.selectedLanguage(languageContext)?.nativeName
    } ?: stringResource(R.string.settings_language_system_default)
    val detectionSubtitle = when {
        uiState.isCalibratingModels -> stringResource(R.string.settings_calibrating)
        uiState.automaticModelSelectionEnabled -> stringResource(R.string.settings_automatic_models)
        else -> stringResource(R.string.settings_manual_models)
    }.let { base ->
        if (uiState.documentGateEnabled) stringResource(R.string.settings_gate_on, base) else stringResource(R.string.settings_gate_off, base)
    }
    val textRecognitionSubtitle = when (uiState.ocrEngine) {
        OcrEngine.ML_KIT -> stringResource(R.string.settings_ml_kit_latin_script_languages)
        OcrEngine.INDRA -> stringResource(R.string.settings_indra_ocr_100_languages_experimental)
    }
    val storageSubtitle = buildString {
        uiState.storageUsage?.let {
            append(StorageFormatter.formatBytes(it.totalBytes))
            append(" · ")
        }
        append(stringResource(R.string.settings_save_location_cleanup))
    }

    // Drives the enter/exit animation of the rating card while keeping its LazyColumn item alive
    // long enough to animate out before the item is removed.
    val ratingCardState = remember { MutableTransitionState(uiState.showRatingCard) }
    LaunchedEffect(uiState.showRatingCard) {
        ratingCardState.targetState = uiState.showRatingCard
    }

    val preferencesGroup: @Composable () -> Unit = {
        SettingsGroup(title = stringResource(R.string.settings_preferences)) {
            SettingsNavigationRow(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.settings_hub_sub_screens_appearance),
                subtitle = appearanceSubtitle,
                onClick = onOpenAppearance,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SettingsNavigationRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language),
                subtitle = languageSubtitle,
                onClick = onOpenLanguage,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SettingsNavigationRow(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.settings_pip_your_helper),
                subtitle = pipPreferences.settingsSummary(),
                onClick = onOpenPip,
                leading = { PipSettingsIcon(enabled = pipPreferences.enabled) },
            )
        }
    }
    val libraryGroup: @Composable () -> Unit = {
        SettingsGroup(title = stringResource(R.string.library_library)) {
            SettingsNavigationRow(
                icon = Icons.Filled.Storage,
                title = stringResource(R.string.settings_storage),
                subtitle = storageSubtitle,
                onClick = onOpenStorage,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SettingsNavigationRow(
                icon = Icons.Filled.RestoreFromTrash,
                title = stringResource(R.string.recently_deleted_recently_deleted),
                subtitle = stringResource(R.string.settings_pages_kept_documents_and_folders, TrashKind.PAGE.retentionLabel(), TrashKind.DOCUMENT.retentionLabel()),
                onClick = onOpenRecentlyDeleted,
            )
        }
    }
    val scanningGroup: @Composable () -> Unit = {
        SettingsGroup(title = stringResource(R.string.settings_intelligence)) {
            SettingsNavigationRow(
                icon = Icons.Filled.DocumentScanner,
                title = stringResource(R.string.settings_hub_sub_screens_document_detection),
                subtitle = detectionSubtitle,
                onClick = onOpenDetection,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SettingsNavigationRow(
                icon = Icons.Filled.TextFields,
                title = stringResource(R.string.settings_text_recognition),
                subtitle = textRecognitionSubtitle,
                onClick = onOpenTextRecognition,
            )
        }
    }
    val homeScreenGroup: @Composable () -> Unit = {
        SettingsGroup(title = stringResource(R.string.settings_hub_sub_screens_home_screen)) {
            SettingsNavigationRow(
                icon = Icons.Filled.Widgets,
                title = stringResource(R.string.settings_hub_sub_screens_widgets),
                subtitle = stringResource(R.string.settings_actions_scan_and_qr),
                onClick = onOpenWidgets,
            )
        }
    }
    val moreGroup: @Composable () -> Unit = {
        SettingsGroup(title = stringResource(R.string.settings_more)) {
            if (!content?.faqs.isNullOrEmpty()) {
                SettingsNavigationRow(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = stringResource(R.string.settings_detail_screens_help_faq),
                    subtitle = pluralStringResource(R.plurals.common_topics, content.faqs.size, content.faqs.size),
                    onClick = onOpenFaqs,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            SettingsNavigationRow(
                icon = Icons.Outlined.StarOutline,
                title = stringResource(R.string.home_rating_prompt_rate_scanly),
                subtitle = stringResource(R.string.settings_leave_a_rating_on_google),
                onClick = onRateApp,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SettingsNavigationRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_hub_sub_screens_about),
                subtitle = content?.appVersionLabel?.let { stringResource(R.string.settings_scanly, it) }
                    ?: stringResource(R.string.settings_version_updates_and_legal),
                onClick = onOpenAbout,
            )
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ScanlyTestTags.SETTINGS_SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.isLoading && content == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding()),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = if (windowSizeInfo.widthClass == WindowWidthClass.Compact) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.widthIn(max = windowSizeInfo.contentMaxWidth).fillMaxHeight()
                    },
                    contentPadding = PaddingValues(
                        start = windowSizeInfo.horizontalPadding,
                        top = 0.dp,
                        end = windowSizeInfo.horizontalPadding,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "header") {
                        ScanlyTabScreenHeader(
                            title = stringResource(R.string.settings_settings),
                            subtitle = content?.appVersionLabel?.let { stringResource(R.string.settings_scanly, it) }
                                ?: stringResource(R.string.settings_preferences_and_app_info),
                        )
                    }

                    if (ratingCardState.currentState || ratingCardState.targetState) {
                        item(key = "rating") {
                            AnimatedVisibility(
                                visibleState = ratingCardState,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                RateScanlyCard(
                                    onRate = onRateApp,
                                    onDismiss = {
                                        ratingCardState.targetState = false
                                        onDismissRatingCard()
                                    },
                                )
                            }
                        }
                    }

                    if (windowSizeInfo.useTwoPaneLayout) {
                        // Wide windows: two balanced columns of groups instead of rows stretched edge to edge.
                        item(key = "groups_two_pane") {
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    preferencesGroup()
                                    libraryGroup()
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    scanningGroup()
                                    homeScreenGroup()
                                    moreGroup()
                                }
                            }
                        }
                    } else {
                        item(key = "preferences") { preferencesGroup() }
                        item(key = "library") { libraryGroup() }
                        item(key = "scanning") { scanningGroup() }
                        item(key = "home") { homeScreenGroup() }
                        item(key = "more") { moreGroup() }
                    }
                }
            }
        }
    }
}
