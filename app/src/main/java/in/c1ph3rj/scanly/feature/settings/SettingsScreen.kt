package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.feature.components.ScanlyAppLogo
import `in`.c1ph3rj.scanly.feature.components.ScanlyTabScreenHeader
import `in`.c1ph3rj.scanly.feature.update.AppUpdateUiState
import kotlinx.coroutines.flow.collectLatest

private const val DEVELOPER_PORTFOLIO_URL = "https://c1ph3rj.in"
private const val PROJECT_WEBSITE_URL = "https://scanly.c1ph3rj.in"
private const val SUPPORT_EMAIL = "info@c1ph3rj.in"

private val SettingsRowPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private val ModelChipShape = RoundedCornerShape(20.dp)

private fun Modifier.settingsRowSurface(onClick: (() -> Unit)? = null): Modifier =
    fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(SettingsRowPadding)

private val DocumentCornerModel.description: String
    get() = when (this) {
        DocumentCornerModel.LITE -> "Fastest · ideal for live camera preview"
        DocumentCornerModel.STANDARD -> "Balanced speed and accuracy"
        DocumentCornerModel.HIGH -> "Higher accuracy · best after capture"
        DocumentCornerModel.ACCURATE -> "Highest accuracy · maximum compatibility"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    title: String,
    subtitle: String,
    selected: DocumentCornerModel,
    enabled: Boolean,
    onSelected: (DocumentCornerModel) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Flat chip (Box + clip) — avoid Material Surface elevation/tonal shadow on nested cards.
    val chipContainer = if (enabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val chipContent = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val chipBorder = BorderStroke(
        width = 1.dp,
        color = if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
    )

    Row(
        modifier = Modifier.settingsRowSurface(
            onClick = if (enabled) ({ showPicker = true }) else null,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .clip(ModelChipShape)
                .background(chipContainer)
                .border(chipBorder, ModelChipShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = selected.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = chipContent,
            )
            if (enabled) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "Choose $title",
                    tint = chipContent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showPicker && enabled) {
        ModelPickerSheet(
            title = title,
            selected = selected,
            onSelected = { model ->
                showPicker = false
                onSelected(model)
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    title: String,
    selected: DocumentCornerModel,
    onSelected: (DocumentCornerModel) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        // Default DragHandle uses an elevated Surface that casts a boxed shadow on pure black.
        dragHandle = { FlatBottomSheetDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Pick the detector that fits this device and workflow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface)
                    .selectableGroup(),
            ) {
                DocumentCornerModel.entries.forEachIndexed { index, model ->
                    val isSelected = model == selected
                    ModelPickerOption(
                        model = model,
                        selected = isSelected,
                        onClick = { onSelected(model) },
                    )
                    if (index < DocumentCornerModel.entries.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlatBottomSheetDragHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun ModelPickerOption(
    model: DocumentCornerModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val titleColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = titleColor,
            )
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsRoute(
    onNavigateUp: () -> Unit,
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onOpenLegalDocument: (LegalDocumentType) -> Unit,
    onOpenFaqs: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenModelBenchmark: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    SettingsScreen(
        uiState = uiState,
        appUpdateUiState = appUpdateUiState,
        snackbarHostState = snackbarHostState,
        onThemeModeSelected = viewModel::setThemeMode,
        onPureBlackEnabledChanged = viewModel::setPureBlackEnabled,
        onOpenWebsite = { url -> uriHandler.openUri(url) },
        onOpenLegalDocument = onOpenLegalDocument,
        onOpenFaqs = onOpenFaqs,
        onOpenLicenses = onOpenLicenses,
        onOpenStorage = onOpenStorage,
        onOpenModelBenchmark = onOpenModelBenchmark,
        onLiveDetectionModelSelected = viewModel::setLiveDetectionModel,
        onPostProcessingModelSelected = viewModel::setPostProcessingModel,
        onAutomaticModelSelectionChanged = viewModel::setAutomaticModelSelection,
        onDocumentGateEnabledChanged = viewModel::setDocumentGateEnabled,
        onCheckForUpdates = onCheckForUpdates,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    appUpdateUiState: AppUpdateUiState,
    snackbarHostState: SnackbarHostState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onPureBlackEnabledChanged: (Boolean) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onOpenLegalDocument: (LegalDocumentType) -> Unit,
    onOpenFaqs: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenModelBenchmark: () -> Unit,
    onLiveDetectionModelSelected: (DocumentCornerModel) -> Unit,
    onPostProcessingModelSelected: (DocumentCornerModel) -> Unit,
    onAutomaticModelSelectionChanged: (Boolean) -> Unit,
    onDocumentGateEnabledChanged: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    val content = uiState.content
    val windowSizeInfo = rememberWindowSizeInfo()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                modifier = if (windowSizeInfo.isTablet) {
                    Modifier.widthIn(max = windowSizeInfo.contentMaxWidth).fillMaxHeight()
                } else {
                    Modifier.fillMaxSize()
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
                        title = "Settings",
                        subtitle = content?.appVersionLabel?.let { "Scanly $it" }
                            ?: "Preferences and app info",
                    )
                }

                item(key = "look_and_feel") {
                    SettingsGroup(title = "Look & feel") {
                        ThemeModeSelector(
                            selectedMode = uiState.themeMode,
                            onThemeModeSelected = onThemeModeSelected,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleRow(
                            title = "Pure black",
                            subtitle = if (uiState.themeMode == ThemeMode.LIGHT) {
                                "Takes effect in dark theme · saves battery on AMOLED screens"
                            } else {
                                "True black surfaces · saves battery on AMOLED screens"
                            },
                            checked = uiState.pureBlackEnabled,
                            onCheckedChange = onPureBlackEnabledChanged,
                        )
                    }
                }

                item(key = "storage") {
                    SettingsGroup(title = "Storage") {
                        SettingsNavigationRow(
                            icon = Icons.Filled.Storage,
                            title = "Storage & backup",
                            subtitle = buildString {
                                uiState.storageUsage?.let {
                                    append(StorageFormatter.formatBytes(it.totalBytes))
                                    append(" • ")
                                }
                                append(uiState.exportDestination.exportLabel)
                            },
                            onClick = onOpenStorage,
                        )
                    }
                }

                item(key = "model_testing") {
                    SettingsGroup(title = "Document detection") {
                        SettingsToggleRow(
                            title = "Automatic model selection",
                            subtitle = when {
                                uiState.isCalibratingModels -> "Testing model speed on this device…"
                                uiState.automaticLiveModel != null && uiState.automaticPostProcessingModel != null ->
                                    "Live: ${uiState.automaticLiveModel.displayName} • Processing: ${uiState.automaticPostProcessingModel.displayName}"
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
                            selected = uiState.automaticPostProcessingModel ?: uiState.postProcessingModel,
                            enabled = !uiState.automaticModelSelectionEnabled,
                            onSelected = onPostProcessingModelSelected,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleRow(
                            title = "Physical-document gate",
                            subtitle = "Reject screens and non-documents before edge detection",
                            checked = uiState.documentGateEnabled,
                            onCheckedChange = onDocumentGateEnabledChanged,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsNavigationRow(
                            icon = Icons.Filled.Speed,
                            title = "Model benchmark",
                            subtitle = "Compare all models on local images",
                            onClick = onOpenModelBenchmark,
                        )
                    }
                }

                item(key = "about") {
                    SettingsGroup(title = "About") {
                        AboutHero(
                            versionLabel = content?.appVersionLabel,
                            onOpenPortfolio = { onOpenWebsite(DEVELOPER_PORTFOLIO_URL) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsUpdateRow(
                            appUpdateUiState = appUpdateUiState,
                            onCheckForUpdates = onCheckForUpdates,
                        )
                    }
                }

                item(key = "support") {
                    SettingsGroup(title = "Support") {
                        if (!content?.faqs.isNullOrEmpty()) {
                            SettingsNavigationRow(
                                icon = Icons.AutoMirrored.Filled.HelpOutline,
                                title = "Help & FAQ",
                                subtitle = "${content!!.faqs.size} topics",
                                onClick = onOpenFaqs,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        SettingsLinkRow(
                            icon = Icons.Filled.Email,
                            title = "Contact support",
                            subtitle = null,
                            onClick = { onOpenWebsite("mailto:$SUPPORT_EMAIL") },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsLinkRow(
                            icon = Icons.Filled.Public,
                            title = "Project website",
                            subtitle = null,
                            onClick = { onOpenWebsite(PROJECT_WEBSITE_URL) },
                        )
                    }
                }

                item(key = "legal") {
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
                        if (!content?.licenses.isNullOrEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            SettingsNavigationRow(
                                icon = Icons.Filled.Code,
                                title = "Open source",
                                subtitle = "${content!!.licenses.size} libraries",
                                onClick = onOpenLicenses,
                            )
                        }
                    }
                }
            }
            } // end adaptive Box
        }
    }

}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.settingsRowSurface(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun AboutHero(
    versionLabel: String?,
    onOpenPortfolio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(SettingsRowPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScanlyAppLogo(size = 48.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Scanly",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = versionLabel ?: "Version unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "by jeevaprakash g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenPortfolio),
            )
        }
    }
}

@Composable
private fun StorageUsageRow(
    storageUsage: AppStorageUsage?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.settingsRowSurface(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Storage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "App storage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            when {
                isLoading -> {
                    Text(
                        text = "Calculating…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                storageUsage != null -> {
                    Text(
                        text = StorageFormatter.formatBytes(storageUsage.totalBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun SettingsUpdateRow(
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkResult = appUpdateUiState.lastCheckResult
    val sourceLabel = appUpdateUiState.channel.sourceLabel
    val updateAvailable = checkResult?.updateAvailable == true
    val title = if (updateAvailable) {
        "Update available"
    } else {
        "Check for updates"
    }
    val subtitle = when {
        appUpdateUiState.isChecking -> "Checking $sourceLabel..."
        updateAvailable -> {
            "Scanly ${checkResult!!.latestRelease.tagName} is available on $sourceLabel."
        }

        checkResult != null -> {
            "You are on ${versionLabel(checkResult.installedVersionName)}. Latest is ${checkResult.latestRelease.tagName}."
        }

        else -> "Tap to check $sourceLabel"
    }
    val rowModifier = if (updateAvailable) {
        modifier.settingsRowSurface(
            onClick = if (appUpdateUiState.isChecking) null else onCheckForUpdates,
        )
    } else {
        modifier.settingsRowSurface(
            onClick = if (appUpdateUiState.isChecking) null else onCheckForUpdates,
        )
    }
    val titleColor = if (updateAvailable) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (updateAvailable) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconContainerColor = if (updateAvailable) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val iconTint = if (updateAvailable) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (updateAvailable) 34.dp else 22.dp)
                .clip(CircleShape)
                .background(iconContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(if (updateAvailable) 20.dp else 22.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (updateAvailable) FontWeight.SemiBold else FontWeight.Medium,
                color = titleColor,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
            )
        }
        if (appUpdateUiState.isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun SettingsDestructiveRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.settingsRowSurface(onClick = if (enabled) onClick else null),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun versionLabel(versionName: String): String =
    if (versionName.startsWith("v", ignoreCase = true)) {
        versionName
    } else {
        "v$versionName"
    }

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.settingsRowSurface(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    selectedMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(SettingsRowPadding),
    ) {
        options.forEachIndexed { index, themeMode ->
            val selected = themeMode == selectedMode
            SegmentedButton(
                selected = selected,
                onClick = { onThemeModeSelected(themeMode) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                ),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                icon = {
                    Icon(
                        imageVector = themeMode.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                    )
                },
                label = {
                    Text(
                        text = themeMode.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.DARK -> Icons.Filled.DarkMode
}

@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showExternalLink: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.settingsRowSurface(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showExternalLink && onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
