package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.feature.components.ScanlyTabScreenHeader
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SettingsRoute(
    onOpenFaqs: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenDetection: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
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

    SettingsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onOpenAppearance = onOpenAppearance,
        onOpenStorage = onOpenStorage,
        onOpenDetection = onOpenDetection,
        onOpenWidgets = onOpenWidgets,
        onOpenFaqs = onOpenFaqs,
        onOpenAbout = onOpenAbout,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onOpenAppearance: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenDetection: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenFaqs: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val content = uiState.content
    val windowSizeInfo = rememberWindowSizeInfo()
    val appearanceSubtitle = buildString {
        append(uiState.themeMode.settingsLabel())
        if (uiState.pureBlackEnabled && uiState.themeMode != ThemeMode.LIGHT) {
            append(" · Pure black")
        }
    }
    val detectionSubtitle = when {
        uiState.isCalibratingModels -> "Calibrating…"
        uiState.automaticModelSelectionEnabled -> "Automatic models"
        else -> "Manual models"
    }.let { base ->
        if (uiState.documentGateEnabled) "$base · Gate on" else "$base · Gate off"
    }
    val storageSubtitle = buildString {
        uiState.storageUsage?.let {
            append(StorageFormatter.formatBytes(it.totalBytes))
            append(" · ")
        }
        append("Backup & export")
    }

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

                    item(key = "preferences") {
                        SettingsGroup(title = "Preferences") {
                            SettingsNavigationRow(
                                icon = Icons.Filled.Palette,
                                title = "Appearance",
                                subtitle = appearanceSubtitle,
                                onClick = onOpenAppearance,
                            )
                        }
                    }

                    item(key = "library") {
                        SettingsGroup(title = "Library") {
                            SettingsNavigationRow(
                                icon = Icons.Filled.Storage,
                                title = "Storage & backup",
                                subtitle = storageSubtitle,
                                onClick = onOpenStorage,
                            )
                        }
                    }

                    item(key = "scanning") {
                        SettingsGroup(title = "Scanning") {
                            SettingsNavigationRow(
                                icon = Icons.Filled.DocumentScanner,
                                title = "Document detection",
                                subtitle = detectionSubtitle,
                                onClick = onOpenDetection,
                            )
                        }
                    }

                    item(key = "home") {
                        SettingsGroup(title = "Home screen") {
                            SettingsNavigationRow(
                                icon = Icons.Filled.Widgets,
                                title = "Widgets",
                                subtitle = "Actions, Scan, and QR",
                                onClick = onOpenWidgets,
                            )
                        }
                    }

                    item(key = "more") {
                        SettingsGroup(title = "More") {
                            if (!content?.faqs.isNullOrEmpty()) {
                                SettingsNavigationRow(
                                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                                    title = "Help & FAQ",
                                    subtitle = "${content!!.faqs.size} topics",
                                    onClick = onOpenFaqs,
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            SettingsNavigationRow(
                                icon = Icons.Filled.Info,
                                title = "About",
                                subtitle = content?.appVersionLabel?.let { "Scanly $it" }
                                    ?: "Version, updates, and legal",
                                onClick = onOpenAbout,
                            )
                        }
                    }
                }
            }
        }
    }
}
