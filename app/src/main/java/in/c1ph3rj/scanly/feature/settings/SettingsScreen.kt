package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.model.LicenseInfo
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.feature.components.ScanlyAppLogo
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.update.AppUpdateUiState
import kotlinx.coroutines.flow.collectLatest

private const val DEVELOPER_PORTFOLIO_URL = "https://c1ph3rj.in"
private const val PROJECT_WEBSITE_URL = "https://scanly.c1ph3rj.in"
private const val SUPPORT_EMAIL = "info@c1ph3rj.in"

private val SettingsRowPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

private fun Modifier.settingsRowSurface(onClick: (() -> Unit)? = null): Modifier =
    fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(SettingsRowPadding)

@Composable
fun SettingsRoute(
    onNavigateUp: () -> Unit,
    appUpdateUiState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onOpenLegalDocument: (LegalDocumentType) -> Unit,
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
        onShowDetectionStatsChanged = viewModel::setShowDetectionStats,
        onOpenWebsite = { url -> uriHandler.openUri(url) },
        onOpenLegalDocument = onOpenLegalDocument,
        onCheckForUpdates = onCheckForUpdates,
        onClearAllData = viewModel::clearAllData,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    appUpdateUiState: AppUpdateUiState,
    snackbarHostState: SnackbarHostState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onShowDetectionStatsChanged: (Boolean) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onOpenLegalDocument: (LegalDocumentType) -> Unit,
    onCheckForUpdates: () -> Unit,
    onClearAllData: () -> Unit,
) {
    val content = uiState.content
    val expandedFaqIds = remember { mutableStateListOf<String>() }
    val expandedLicenseIds = remember { mutableStateListOf<String>() }
    var clearDataDialogVisible by remember { mutableStateOf(false) }
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "header") {
                    SettingsPageHeader(
                        versionLabel = content?.appVersionLabel,
                    )
                }

                item(key = "appearance") {
                    SettingsGroup(
                        title = "Appearance",
                    ) {
                        ThemeModeSelector(
                            selectedMode = uiState.themeMode,
                            onThemeModeSelected = onThemeModeSelected,
                        )
                    }
                }

                item(key = "camera") {
                    SettingsGroup(
                        title = "Camera",
                    ) {
                        SettingsToggleRow(
                            title = "Show confidence %",
                            subtitle = "Display detection confidence and scan time in the camera preview.",
                            checked = uiState.showDetectionStats,
                            onCheckedChange = onShowDetectionStatsChanged,
                        )
                    }
                }

                item(key = "storage") {
                    SettingsGroup(
                        title = "Storage",
                    ) {
                        StorageUsageRow(
                            storageUsage = uiState.storageUsage,
                            isLoading = uiState.isLoadingStorage,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsDestructiveRow(
                            icon = Icons.Filled.DeleteOutline,
                            title = "Clear all data",
                            subtitle = "Delete all documents, folders, and pages",
                            enabled = !uiState.isClearingData,
                            onClick = { clearDataDialogVisible = true },
                        )
                    }
                }

                item(key = "about") {
                    SettingsGroup(
                        title = "About",
                    ) {
                        AboutHero(
                            versionLabel = content?.appVersionLabel,
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsUpdateRow(
                            appUpdateUiState = appUpdateUiState,
                            onCheckForUpdates = onCheckForUpdates,
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsLinkRow(
                            icon = Icons.Filled.Person,
                            title = "Developer",
                            subtitle = "jeevaprakash g",
                            showExternalLink = false,
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsLinkRow(
                            icon = Icons.Filled.Email,
                            title = "Contact Support",
                            subtitle = SUPPORT_EMAIL,
                            onClick = { onOpenWebsite("mailto:$SUPPORT_EMAIL") },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsLinkRow(
                            icon = Icons.Filled.Language,
                            title = "Portfolio",
                            subtitle = DEVELOPER_PORTFOLIO_URL,
                            onClick = { onOpenWebsite(DEVELOPER_PORTFOLIO_URL) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsLinkRow(
                            icon = Icons.Filled.Public,
                            title = "Project website",
                            subtitle = PROJECT_WEBSITE_URL,
                            onClick = { onOpenWebsite(PROJECT_WEBSITE_URL) },
                        )
                    }
                }

                item(key = "legal") {
                    SettingsGroup(
                        title = "Legal",
                    ) {
                        SettingsLinkRow(
                            icon = Icons.Filled.Policy,
                            title = "Privacy Policy",
                            subtitle = "How Scanly handles your data",
                            showExternalLink = false,
                            onClick = { onOpenLegalDocument(LegalDocumentType.Privacy) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsLinkRow(
                            icon = Icons.Filled.Gavel,
                            title = "Terms & Conditions",
                            subtitle = "Rules for using Scanly",
                            showExternalLink = false,
                            onClick = { onOpenLegalDocument(LegalDocumentType.Terms) },
                        )
                    }
                }

                if (!content?.faqs.isNullOrEmpty()) {
                    item(key = "faq") {
                        SettingsGroup(
                            title = "Help",
                        ) {
                            content!!.faqs.forEachIndexed { index, faq ->
                                val expanded = faq.id in expandedFaqIds
                                ExpandableRow(
                                    title = faq.question,
                                    body = faq.answer,
                                    expanded = expanded,
                                    onToggle = {
                                        if (expanded) {
                                            expandedFaqIds.remove(faq.id)
                                        } else {
                                            expandedFaqIds.add(faq.id)
                                        }
                                    },
                                )
                                if (index < content.faqs.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }

                if (!content?.licenses.isNullOrEmpty()) {
                    item(key = "licenses") {
                        SettingsGroup(
                            title = "Open source",
                        ) {
                            content!!.licenses.forEachIndexed { index, license ->
                                val expanded = license.id in expandedLicenseIds
                                LicenseRow(
                                    licenseInfo = license,
                                    expanded = expanded,
                                    onToggle = {
                                        if (expanded) {
                                            expandedLicenseIds.remove(license.id)
                                        } else {
                                            expandedLicenseIds.add(license.id)
                                        }
                                    },
                                    onOpenWebsite = onOpenWebsite,
                                )
                                if (index < content.licenses.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
            } // end adaptive Box
        }
    }

    if (clearDataDialogVisible) {
        ScanlyConfirmDialog(
            title = "Clear all data?",
            text = "This permanently deletes all documents, folders, and pages from Scanly. " +
                "This cannot be undone. Your theme and camera settings will be kept.",
            confirmLabel = "Delete",
            onDismiss = {
                if (!uiState.isClearingData) {
                    clearDataDialogVisible = false
                }
            },
            onConfirm = {
                clearDataDialogVisible = false
                onClearAllData()
            },
            confirmDestructive = true,
            dismissEnabled = !uiState.isClearingData,
            confirmEnabled = !uiState.isClearingData,
        )
    }
}

@Composable
private fun SettingsPageHeader(
    versionLabel: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = versionLabel?.let { "Scanly $it" } ?: "Preferences and app info",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text(
                        text = buildString {
                            append("Documents ")
                            append(StorageFormatter.formatBytes(storageUsage.documentsBytes))
                            append(" · Cache ")
                            append(StorageFormatter.formatBytes(storageUsage.exportCacheBytes))
                            append(" · Database ")
                            append(StorageFormatter.formatBytes(storageUsage.databaseBytes))
                        },
                        style = MaterialTheme.typography.bodySmall,
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
    val updateAvailable = checkResult?.updateAvailable == true
    val title = if (updateAvailable) {
        "Update available"
    } else {
        "Check for updates"
    }
    val subtitle = when {
        appUpdateUiState.isChecking -> "Checking GitHub releases..."
        updateAvailable -> {
            "Scanly ${checkResult!!.latestRelease.tagName} is ready to download. Tap to view release notes."
        }

        checkResult != null -> {
            "You are on ${versionLabel(checkResult.installedVersionName)}. Latest is ${checkResult.latestRelease.tagName}."
        }

        else -> "Compare this install with the latest GitHub release."
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
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.settingsRowSurface { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun ThemeModeSelector(
    selectedMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackShape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(SettingsRowPadding)
            .clip(trackShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeMode.entries.forEach { themeMode ->
            ThemeModeOption(
                label = themeMode.label,
                icon = themeMode.icon(),
                selected = themeMode == selectedMode,
                onClick = { onThemeModeSelected(themeMode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeModeOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)
    } else {
        Color.Transparent
    }
    val optionShape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .clip(optionShape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), optionShape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else contentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Filled.Brightness4
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.DARK -> Icons.Filled.DarkMode
}

@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

@Composable
private fun ExpandableRow(
    title: String,
    body: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(SettingsRowPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LicenseRow(
    licenseInfo: LicenseInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(SettingsRowPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = licenseInfo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = licenseInfo.license,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = licenseInfo.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                licenseInfo.websiteUrl?.let { website ->
                    Text(
                        text = website,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenWebsite(website) },
                    )
                }
            }
        }
    }
}
