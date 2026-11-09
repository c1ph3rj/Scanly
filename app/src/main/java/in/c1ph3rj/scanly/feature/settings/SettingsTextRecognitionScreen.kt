package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.res.stringResource
import `in`.c1ph3rj.scanly.core.common.appString
import androidx.compose.ui.res.pluralStringResource

internal const val DATALAB_URL = "https://www.datalab.to"
internal const val ML_KIT_URL = "https://developers.google.com/ml-kit/vision/text-recognition/v2"

/** A writing system an engine reads, with well-known languages written in it. */
internal data class OcrScriptCoverage(
    val script: String,
    val languages: List<String>,
)

internal val MlKitCoverage get() = listOf(
    OcrScriptCoverage(
        script = appString(R.string.settings_text_recognition_latin),
        languages = listOf(
            appString(R.string.settings_text_recognition_english), appString(R.string.settings_text_recognition_spanish), appString(R.string.settings_text_recognition_french), appString(R.string.settings_text_recognition_german), appString(R.string.settings_text_recognition_italian), appString(R.string.settings_text_recognition_portuguese), appString(R.string.settings_text_recognition_dutch),
            appString(R.string.settings_text_recognition_polish), appString(R.string.settings_text_recognition_turkish), appString(R.string.settings_text_recognition_indonesian), appString(R.string.settings_text_recognition_malay), appString(R.string.settings_text_recognition_vietnamese), appString(R.string.settings_text_recognition_filipino), appString(R.string.settings_text_recognition_swahili),
            appString(R.string.settings_text_recognition_swedish), appString(R.string.settings_text_recognition_czech), appString(R.string.settings_text_recognition_romanian), appString(R.string.settings_text_recognition_and_more),
        ),
    ),
)

/** Mirrors the four Indra recognizers (base, CJK, right-to-left, world); see `assets/models/indra/vocab_*.json`. */
internal val IndraCoverage get() = listOf(
    OcrScriptCoverage(
        script = appString(R.string.settings_text_recognition_latin_cyrillic_greek),
        languages = listOf(appString(R.string.settings_text_recognition_english), appString(R.string.settings_text_recognition_spanish), appString(R.string.settings_text_recognition_french), appString(R.string.settings_text_recognition_german), appString(R.string.settings_text_recognition_russian), appString(R.string.settings_text_recognition_ukrainian), appString(R.string.settings_text_recognition_bulgarian), appString(R.string.settings_text_recognition_serbian), appString(R.string.settings_text_recognition_greek)),
    ),
    OcrScriptCoverage(
        script = appString(R.string.settings_text_recognition_indian_scripts),
        languages = listOf(
            appString(R.string.settings_text_recognition_hindi), appString(R.string.settings_text_recognition_marathi), appString(R.string.settings_text_recognition_nepali), appString(R.string.settings_text_recognition_bengali), appString(R.string.settings_text_recognition_tamil), appString(R.string.settings_text_recognition_telugu), appString(R.string.settings_text_recognition_kannada),
            appString(R.string.settings_text_recognition_malayalam), appString(R.string.settings_text_recognition_gujarati), appString(R.string.settings_text_recognition_punjabi), appString(R.string.settings_text_recognition_odia), appString(R.string.settings_text_recognition_sinhala),
        ),
    ),
    OcrScriptCoverage(
        script = appString(R.string.settings_text_recognition_east_asian),
        languages = listOf(appString(R.string.settings_text_recognition_chinese), appString(R.string.settings_text_recognition_japanese), appString(R.string.settings_text_recognition_korean)),
    ),
    OcrScriptCoverage(
        script = appString(R.string.settings_text_recognition_right_to_left),
        languages = listOf(appString(R.string.settings_text_recognition_arabic), appString(R.string.settings_text_recognition_persian), appString(R.string.settings_text_recognition_urdu), appString(R.string.settings_text_recognition_hebrew)),
    ),
    OcrScriptCoverage(
        script = appString(R.string.settings_text_recognition_southeast_asia_more),
        languages = listOf(appString(R.string.settings_text_recognition_thai), appString(R.string.settings_text_recognition_lao), appString(R.string.settings_text_recognition_khmer), appString(R.string.settings_text_recognition_burmese), appString(R.string.settings_text_recognition_armenian), appString(R.string.settings_text_recognition_georgian), appString(R.string.settings_text_recognition_amharic)),
    ),
)

@Composable
fun SettingsTextRecognitionRoute(
    onNavigateUp: () -> Unit,
    parentEntry: NavBackStackEntry,
    viewModel: SettingsViewModel = hiltViewModel(parentEntry),
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

    SettingsTextRecognitionScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onOcrEngineSelected = viewModel::setOcrEngine,
        onRereadClick = viewModel::requestReread,
        onRereadConfirm = viewModel::confirmReread,
        onRereadDismiss = viewModel::dismissRereadPrompt,
        onOpenDatalab = { uriHandler.openUriSafely(DATALAB_URL) },
        onOpenMlKit = { uriHandler.openUriSafely(ML_KIT_URL) },
    )
}

@Composable
fun SettingsTextRecognitionScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onOcrEngineSelected: (OcrEngine) -> Unit,
    onRereadClick: () -> Unit,
    onRereadConfirm: () -> Unit,
    onRereadDismiss: () -> Unit,
    onOpenDatalab: () -> Unit,
    onOpenMlKit: () -> Unit,
) {
    val window = rememberWindowSizeInfo()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    ScanlyDetailScaffold(
        title = stringResource(R.string.settings_text_recognition),
        onNavigateUp = onNavigateUp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = { showHelp = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = stringResource(R.string.settings_text_recognition_about_text_recognition),
                )
            }
        },
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
            item("engine") {
                SettingsGroup(title = stringResource(R.string.settings_text_recognition_engine)) {
                    OcrEngineSelector(
                        selected = uiState.ocrEngine,
                        onSelected = onOcrEngineSelected,
                    )
                    OcrEngineStatusRows(
                        uiState = uiState,
                        onRereadClick = onRereadClick,
                    )
                }
            }
            item("languages") {
                SettingsGroup(title = stringResource(R.string.settings_text_recognition_supported_languages)) {
                    OcrLanguageCoverage(selected = uiState.ocrEngine)
                }
            }
            item("credits") {
                SettingsGroup(title = stringResource(R.string.settings_text_recognition_credits)) {
                    DatalabCreditCard(onOpenDatalab = onOpenDatalab)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsLinkRow(
                        icon = Icons.Filled.TextFields,
                        title = stringResource(R.string.settings_text_recognition_google_ml_kit),
                        subtitle = stringResource(R.string.settings_text_recognition_on_device_text_recognition_v2),
                        onClick = onOpenMlKit,
                    )
                }
            }
        }
    }

    if (showHelp) {
        TextRecognitionHelpSheet(onDismiss = { showHelp = false })
    }

    if (uiState.showRereadPrompt && uiState.pagesNotReadWithSelectedEngine > 0) {
        val count = uiState.pagesNotReadWithSelectedEngine
        ScanlyConfirmDialog(
            title = pluralStringResource(R.plurals.settings_text_recognition_reread_pages, count, count),
            text = stringResource(R.string.settings_text_recognition_runs_in_the_background_while),
            confirmLabel = stringResource(R.string.settings_text_recognition_re_read),
            onDismiss = onRereadDismiss,
            onConfirm = onRereadConfirm,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextRecognitionHelpSheet(onDismiss: () -> Unit) {
    ScanlyModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.settings_text_recognition),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_text_recognition_also_called_ocr),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HelpSection(
                title = stringResource(R.string.settings_text_recognition_what_it_does),
                body = stringResource(R.string.settings_text_recognition_after_you_scan_scanly_reads),
            )
            HelpSection(
                title = stringResource(R.string.settings_text_recognition_choosing_an_engine),
                body = stringResource(R.string.settings_text_recognition_ml_kit_is_the_default),
            )

            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    OcrFactRow(
                        icon = Icons.Filled.Lock,
                        title = stringResource(R.string.settings_text_recognition_stays_on_your_phone),
                        body = stringResource(R.string.settings_text_recognition_both_engines_run_on_the),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    OcrFactRow(
                        icon = Icons.Filled.SwapHoriz,
                        title = stringResource(R.string.settings_text_recognition_ml_kit_is_the_safety),
                        body = stringResource(R.string.settings_text_recognition_if_indra_ocr_can_t),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    OcrFactRow(
                        icon = Icons.Filled.EditNote,
                        title = stringResource(R.string.settings_text_recognition_your_edits_are_kept),
                        body = stringResource(R.string.settings_text_recognition_switching_engines_only_affects_new),
                    )
                }
            }

            HelpSection(
                title = stringResource(R.string.settings_text_recognition_tip),
                body = stringResource(R.string.settings_text_recognition_if_a_page_s_text),
            )
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun OcrEngineSelector(
    selected: OcrEngine,
    onSelected: (OcrEngine) -> Unit,
) {
    data class Option(val engine: OcrEngine, val title: String, val badge: String, val subtitle: String)
    val options = listOf(
        Option(
            OcrEngine.ML_KIT,
            stringResource(R.string.settings_ml_kit),
            stringResource(R.string.settings_text_recognition_default),
            stringResource(R.string.settings_text_recognition_google_s_fast_on_device),
        ),
        Option(
            OcrEngine.INDRA,
            stringResource(R.string.settings_indra_ocr),
            stringResource(R.string.settings_text_recognition_experimental),
            stringResource(R.string.settings_text_recognition_reads_100_languages_across_many),
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.engine == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelected(option.engine) },
                        role = Role.RadioButton,
                    )
                    .padding(SettingsRowPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        EngineBadge(
                            label = option.badge,
                            emphasized = option.engine == OcrEngine.ML_KIT,
                        )
                    }
                    Text(
                        text = option.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SelectionDot(selected = isSelected)
            }
            if (index < options.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun EngineBadge(label: String, emphasized: Boolean) {
    val container = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun SelectionDot(selected: Boolean) {
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

/**
 * Language lists for both engines behind a two-tab switch. It opens on the selected engine but can be
 * flipped without changing the engine, so people can compare before choosing.
 */
@Composable
private fun OcrLanguageCoverage(selected: OcrEngine) {
    var shown by rememberSaveable(selected) { mutableStateOf(selected) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(SettingsRowPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(OcrEngine.ML_KIT to stringResource(R.string.settings_ml_kit), OcrEngine.INDRA to stringResource(R.string.settings_indra_ocr)).forEach { (engine, label) ->
                val active = engine == shown
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .selectable(selected = active, role = Role.Tab, onClick = { shown = engine })
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        val coverage = if (shown == OcrEngine.INDRA) IndraCoverage else MlKitCoverage
        Text(
            text = if (shown == OcrEngine.INDRA) {
                stringResource(R.string.settings_text_recognition_5_script_families_read_in)
            } else {
                stringResource(R.string.settings_text_recognition_latin_script_only_pages_in)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        coverage.forEach { group -> ScriptCoverageBlock(group) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptCoverageBlock(group: OcrScriptCoverage) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = group.script,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            group.languages.forEach { language ->
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun OcrFactRow(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier.settingsRowSurface(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Datalab's mark is black on white, so it sits on its own white tile in both themes. Fit with inner
 * padding keeps the whole mark visible instead of cropping it at the tile edge.
 */
@Composable
private fun DatalabLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.datalab_logo),
            contentDescription = stringResource(R.string.settings_text_recognition_datalab_logo),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatalabCreditCard(onOpenDatalab: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DatalabLogo(modifier = Modifier.size(56.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_text_recognition_indra_ocr_by),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_text_recognition_datalab),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_text_recognition_a_big_thank_you_to),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onOpenDatalab)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_text_recognition_visit_datalab_to),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = stringResource(R.string.settings_text_recognition_weights_openrail_m_code_apache),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
