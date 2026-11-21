package `in`.c1ph3rj.scanly.feature.naming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.NamingTemplateRenderer
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.NamingTemplate
import `in`.c1ph3rj.scanly.domain.model.NamingTemplateRules
import `in`.c1ph3rj.scanly.domain.model.NamingToken
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import `in`.c1ph3rj.scanly.feature.components.ScanlyFormDialogShell
import `in`.c1ph3rj.scanly.feature.settings.SettingsGroup
import `in`.c1ph3rj.scanly.feature.settings.SettingsRowPadding
import `in`.c1ph3rj.scanly.feature.settings.settingsRowSurface
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

/** Settings → Naming templates (`settings/naming`). */
@Composable
fun NamingTemplatesRoute(
    onNavigateUp: () -> Unit,
    viewModel: NamingTemplatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is NamingTemplatesEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    NamingTemplatesScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onDefaultSelected = viewModel::setDefaultTemplate,
        onAddTemplate = viewModel::startAddingTemplate,
        onEditTemplate = viewModel::startEditingTemplate,
        onDeleteTemplate = viewModel::requestDelete,
        onPatternEdited = viewModel::onPatternEdited,
        onSaveTemplate = viewModel::saveTemplate,
        onDismissEditor = viewModel::dismissEditor,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
    )
}

@Composable
fun NamingTemplatesScreen(
    uiState: NamingTemplatesUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onDefaultSelected: (templateId: String?) -> Unit,
    onAddTemplate: () -> Unit,
    onEditTemplate: (NamingTemplate) -> Unit,
    onDeleteTemplate: (NamingTemplate) -> Unit,
    onPatternEdited: () -> Unit,
    onSaveTemplate: (String) -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    val window = rememberWindowSizeInfo()
    // One timestamp for every example on screen, so they all describe the same moment.
    val now = remember { System.currentTimeMillis() }
    val locale = Locale.getDefault()
    val builtInExample = remember(now, locale) {
        DocumentPresentationFormatter.formatTitle(DocumentTitleFormat.default, now)
    }

    ScanlyDetailScaffold(
        title = stringResource(R.string.naming_settings_entry_title),
        onNavigateUp = onNavigateUp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            if (uiState.isLoaded) {
                IconButton(onClick = onAddTemplate) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.naming_add_template),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (!uiState.isLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            NamingTemplatesContent(
                uiState = uiState,
                innerPadding = innerPadding,
                horizontalPadding = window.horizontalPadding,
                builtInExample = builtInExample,
                now = now,
                locale = locale,
                onDefaultSelected = onDefaultSelected,
                onAddTemplate = onAddTemplate,
                onEditTemplate = onEditTemplate,
                onDeleteTemplate = onDeleteTemplate,
            )
        }
    }

    uiState.editor?.let { editor ->
        NamingTemplateEditorDialog(
            editor = editor,
            templates = uiState.templates,
            now = now,
            locale = locale,
            onPatternEdited = onPatternEdited,
            onSave = onSaveTemplate,
            onDismiss = onDismissEditor,
        )
    }

    uiState.pendingDelete?.let { template ->
        val message = if (template.isDefault) {
            stringResource(
                R.string.naming_delete_default_message,
                template.pattern,
                DocumentTitleFormat.default.label,
            )
        } else {
            stringResource(R.string.naming_delete_message, template.pattern)
        }
        ScanlyConfirmDialog(
            title = stringResource(R.string.naming_delete_title),
            text = message,
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete,
            confirmLabel = stringResource(R.string.naming_delete_confirm),
            dismissLabel = stringResource(R.string.naming_cancel),
            confirmDestructive = true,
        )
    }
}

@Composable
private fun NamingTemplatesContent(
    uiState: NamingTemplatesUiState,
    innerPadding: PaddingValues,
    horizontalPadding: Dp,
    builtInExample: String,
    now: Long,
    locale: Locale,
    onDefaultSelected: (templateId: String?) -> Unit,
    onAddTemplate: () -> Unit,
    onEditTemplate: (NamingTemplate) -> Unit,
    onDeleteTemplate: (NamingTemplate) -> Unit,
) {
    val defaultTemplate = uiState.defaultTemplate
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = 8.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("default") {
            SettingsGroup(title = stringResource(R.string.naming_default_section)) {
                Text(
                    text = stringResource(R.string.naming_default_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
                Column(modifier = Modifier.selectableGroup()) {
                    DefaultChoiceRow(
                        title = DocumentTitleFormat.default.label,
                        example = builtInExample,
                        selected = defaultTemplate == null,
                        onClick = { onDefaultSelected(null) },
                    )
                    uiState.templates.forEach { template ->
                        DefaultChoiceRow(
                            title = template.pattern,
                            example = renderExample(template.pattern, now, locale),
                            selected = template.id == defaultTemplate?.id,
                            onClick = { onDefaultSelected(template.id) },
                        )
                    }
                }
            }
        }
        item("templates") {
            SettingsGroup(title = stringResource(R.string.naming_templates_section)) {
                if (uiState.templates.isEmpty()) {
                    NamingTemplatesEmptyState(onAddTemplate = onAddTemplate)
                } else {
                    uiState.templates.forEach { template ->
                        NamingTemplateRow(
                            template = template,
                            example = renderExample(template.pattern, now, locale),
                            onEdit = { onEditTemplate(template) },
                            onDelete = { onDeleteTemplate(template) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    AddTemplateRow(onClick = onAddTemplate)
                }
            }
        }
    }
}

private fun renderExample(pattern: String, now: Long, locale: Locale): String =
    NamingTemplateRenderer.render(pattern, now, locale, 1)

@Composable
private fun DefaultChoiceRow(
    title: String,
    example: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(SettingsRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.naming_example, example),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NamingTemplateRow(
    template: NamingTemplate,
    example: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.settingsRowSurface(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.DriveFileRenameOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = template.pattern,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.naming_example, example),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (template.isDefault) {
                Text(
                    text = stringResource(R.string.naming_default_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.naming_edit_template_description, template.pattern),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.naming_delete_template_description, template.pattern),
            )
        }
    }
}

@Composable
private fun AddTemplateRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.settingsRowSurface(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(R.string.naming_add_template),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun NamingTemplatesEmptyState(onAddTemplate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.naming_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.naming_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.naming_empty_suggest_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        FilledTonalButton(onClick = onAddTemplate) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.naming_add_template))
        }
    }
}

@Composable
private fun NamingTemplateEditorDialog(
    editor: NamingTemplateEditor,
    templates: List<NamingTemplate>,
    now: Long,
    locale: Locale,
    onPatternEdited: () -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by rememberSaveable(editor.templateId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = editor.initialPattern,
                selection = TextRange(editor.initialPattern.length),
            ),
        )
    }
    val pattern = field.text
    val problems = remember(pattern) { NamingTemplateRenderer.validate(pattern) }
    val unknownTokens = remember(pattern) { NamingTemplateRenderer.unknownTokens(pattern) }
    // An empty field only disables Save; messages appear once there is something to fix.
    val visibleProblems = if (pattern.isEmpty()) emptySet() else problems
    // Not while saving: the saved template shows up in [templates] just before the dialog closes.
    val isDuplicate = remember(pattern, templates, editor.templateId, editor.isSaving) {
        !editor.isSaving && isDuplicatePattern(pattern, templates, editor.templateId)
    }
    val canSave = problems.isEmpty() && !isDuplicate && !editor.isSaving
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun updateField(value: TextFieldValue) {
        if (value.text != field.text) onPatternEdited()
        field = value
    }

    val save = { if (canSave) onSave(pattern) }

    ScanlyFormDialogShell(onDismiss = onDismiss, dismissible = !editor.isSaving) {
        Text(
            text = stringResource(
                if (editor.templateId == null) R.string.naming_new_template else R.string.naming_edit_template,
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = field,
                onValueChange = ::updateField,
                label = { Text(stringResource(R.string.naming_pattern_label)) },
                placeholder = { Text(stringResource(R.string.naming_pattern_placeholder)) },
                singleLine = true,
                enabled = !editor.isSaving,
                isError = visibleProblems.isNotEmpty() || isDuplicate || editor.saveFailed,
                supportingText = {
                    Text(
                        text = stringResource(
                            R.string.naming_character_count,
                            pattern.trim().length,
                            NamingTemplateRules.MAX_PATTERN_LENGTH,
                        ),
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            NamingTemplateRenderer.Problem.entries
                .filter { it in visibleProblems }
                .forEach { problem ->
                    ErrorLine(text = problemMessage(problem, unknownTokens))
                }
            if (isDuplicate) ErrorLine(text = stringResource(R.string.naming_problem_duplicate))
            if (editor.saveFailed) {
                ErrorLine(text = editor.saveError ?: stringResource(R.string.naming_save_failed))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.naming_insert_token),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NamingToken.entries.forEach { token ->
                    TokenChip(
                        token = token,
                        enabled = !editor.isSaving,
                        onClick = {
                            updateField(field.insertAtCursor(token.placeholder))
                            focusRequester.requestFocus()
                        },
                    )
                }
            }
        }

        NamingPreview(pattern = pattern, problems = problems, now = now, locale = locale)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) {
                Text(stringResource(R.string.naming_cancel))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = save, enabled = canSave) {
                if (editor.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.naming_save))
                }
            }
        }
    }
}

@Composable
private fun TokenChip(
    token: NamingToken,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(token.labelRes())
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = token.placeholder,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun NamingPreview(
    pattern: String,
    problems: Set<NamingTemplateRenderer.Problem>,
    now: Long,
    locale: Locale,
) {
    val preview = if (problems.isEmpty()) renderExample(pattern.trim(), now, locale) else null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.naming_preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = preview ?: stringResource(R.string.naming_preview_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (preview != null) FontWeight.Medium else FontWeight.Normal,
                color = if (preview != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (preview != null && NamingTemplateRenderer.usesNumber(pattern)) {
                Text(
                    text = stringResource(R.string.naming_number_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun problemMessage(
    problem: NamingTemplateRenderer.Problem,
    unknownTokens: List<String>,
): String = when (problem) {
    NamingTemplateRenderer.Problem.BLANK -> stringResource(R.string.naming_problem_blank)
    NamingTemplateRenderer.Problem.TOO_LONG ->
        stringResource(R.string.naming_problem_too_long, NamingTemplateRules.MAX_PATTERN_LENGTH)
    NamingTemplateRenderer.Problem.UNKNOWN_TOKEN ->
        stringResource(R.string.naming_problem_unknown_token, unknownTokens.joinToString(", "))
}

private fun NamingToken.labelRes(): Int = when (this) {
    NamingToken.DATE -> R.string.naming_token_date
    NamingToken.TIME -> R.string.naming_token_time
    NamingToken.DATE_TIME -> R.string.naming_token_date_time
    NamingToken.ISO_DATE -> R.string.naming_token_iso_date
    NamingToken.YEAR -> R.string.naming_token_year
    NamingToken.MONTH -> R.string.naming_token_month
    NamingToken.DAY -> R.string.naming_token_day
    NamingToken.WEEKDAY -> R.string.naming_token_weekday
    NamingToken.NUMBER -> R.string.naming_token_number
}

/** Replaces the selection (or inserts at the cursor) with [text] and puts the cursor after it. */
internal fun TextFieldValue.insertAtCursor(text: String): TextFieldValue {
    val start = minOf(selection.start, selection.end).coerceIn(0, this.text.length)
    val end = maxOf(selection.start, selection.end).coerceIn(0, this.text.length)
    return TextFieldValue(
        text = this.text.replaceRange(start, end, text),
        selection = TextRange(start + text.length),
    )
}
