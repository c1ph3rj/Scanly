package `in`.c1ph3rj.scanly.feature.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentTag
import `in`.c1ph3rj.scanly.domain.model.DocumentTagRules
import `in`.c1ph3rj.scanly.feature.components.FullScreenLoader
import `in`.c1ph3rj.scanly.feature.components.NameTextField
import `in`.c1ph3rj.scanly.feature.components.PipEmptyState
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import `in`.c1ph3rj.scanly.feature.components.ScanlyFormDialogShell
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import `in`.c1ph3rj.scanly.feature.settings.SettingsGroup
import kotlinx.coroutines.flow.collectLatest

/** Settings → Tags (`settings/tags`): every tag with its document count; create, rename, delete. */
@Composable
fun TagsManageRoute(
    onNavigateUp: () -> Unit,
    viewModel: TagsManageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TagsManageEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    TagsManageScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onCreate = viewModel::startCreate,
        onRename = viewModel::startRename,
        onDelete = viewModel::requestDelete,
        onNameChanged = viewModel::clearNameError,
        onSubmitName = viewModel::submitName,
        onDismissEditor = viewModel::dismissEditor,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
    )
}

@Composable
fun TagsManageScreen(
    uiState: TagsManageUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onCreate: () -> Unit,
    onRename: (DocumentTag) -> Unit,
    onDelete: (DocumentTag) -> Unit,
    onNameChanged: () -> Unit,
    onSubmitName: (String) -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    val window = rememberWindowSizeInfo()
    ScanlyDetailScaffold(
        title = stringResource(R.string.tags_manage_title),
        onNavigateUp = onNavigateUp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tags_manage_new_tag))
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            FullScreenLoader(modifier = Modifier.padding(innerPadding))
            return@ScanlyDetailScaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = window.horizontalPadding,
                end = window.horizontalPadding,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.tags.isEmpty()) {
                item(key = "empty") {
                    PipEmptyState(
                        pipLine = stringResource(R.string.tags_manage_empty_pip),
                        pipMood = PipMood.IDLE,
                        title = stringResource(R.string.tags_manage_empty_title),
                        description = stringResource(R.string.tags_manage_empty_description),
                        actionLabel = stringResource(R.string.tags_manage_new_tag),
                        onAction = onCreate,
                        actionIcon = Icons.Filled.Add,
                    )
                }
            } else {
                item(key = "tags") {
                    SettingsGroup(title = stringResource(R.string.tags_manage_title)) {
                        uiState.tags.forEachIndexed { index, tag ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            TagRow(tag = tag, onRename = { onRename(tag) }, onDelete = { onDelete(tag) })
                        }
                    }
                }
            }
        }
    }

    uiState.editor?.let { editor ->
        TagNameDialog(
            title = when (editor) {
                TagNameEditor.Create -> stringResource(R.string.tags_manage_new_tag)
                is TagNameEditor.Rename -> stringResource(R.string.tags_manage_rename_title)
            },
            initialValue = (editor as? TagNameEditor.Rename)?.currentName.orEmpty(),
            confirmLabel = when (editor) {
                TagNameEditor.Create -> stringResource(R.string.tags_manage_create)
                is TagNameEditor.Rename -> stringResource(R.string.tags_manage_rename)
            },
            error = uiState.nameError,
            isSaving = uiState.isSaving,
            onValueChange = onNameChanged,
            onConfirm = onSubmitName,
            onDismiss = onDismissEditor,
        )
    }

    uiState.pendingDelete?.let { tag ->
        ScanlyConfirmDialog(
            title = stringResource(R.string.tags_manage_delete_title),
            text = stringResource(R.string.tags_manage_delete_message, tag.name),
            confirmLabel = stringResource(R.string.tags_manage_delete),
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete,
            confirmDestructive = true,
        )
    }
}

/** One tag: tap to rename; the overflow offers rename and delete. */
@Composable
private fun TagRow(
    tag: DocumentTag,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val renameLabel = stringResource(R.string.tags_manage_rename)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = renameLabel, onClick = onRename)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Label,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.common_documents, tag.documentCount, tag.documentCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.tags_manage_options, tag.name),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(renameLabel) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.tags_manage_delete), color = MaterialTheme.colorScheme.error)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** Name dialog for creating or renaming a tag; stays open with [error] when the name is refused. */
@Composable
private fun TagNameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    error: String?,
    isSaving: Boolean,
    onValueChange: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    val canConfirm = DocumentTagRules.normalizeName(value).isNotEmpty() && !isSaving
    val confirm = { if (canConfirm) onConfirm(value) }

    ScanlyFormDialogShell(onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NameTextField(
                value = value,
                onValueChange = {
                    // Longer names are cut to DocumentTagRules.MAX_NAME_LENGTH when saved.
                    value = it
                    onValueChange()
                },
                label = stringResource(R.string.tags_manage_name_label),
                onKeyboardDone = confirm,
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.export_share_cancel))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = confirm, enabled = canConfirm) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(confirmLabel)
                }
            }
        }
    }
}
