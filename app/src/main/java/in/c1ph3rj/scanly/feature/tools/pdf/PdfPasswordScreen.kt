package `in`.c1ph3rj.scanly.feature.tools.pdf

import androidx.compose.material.icons.outlined.Visibility
import `in`.c1ph3rj.scanly.feature.tools.ToolSecondaryButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfPasswordMode
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.feature.tools.ToolBadge
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolCompactHeader
import `in`.c1ph3rj.scanly.feature.tools.ToolDetailRow
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPaperPage
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolTextField
import `in`.c1ph3rj.scanly.feature.tools.ToolTokens
import `in`.c1ph3rj.scanly.feature.tools.ToolWorkingState
import `in`.c1ph3rj.scanly.feature.tools.toolWarningColors
import kotlinx.coroutines.flow.collectLatest
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes

/** Paper stays white in both themes, so the ink drawn on it is fixed as well. */
private val PaperInk = Color(0xFF59636A)

private val MaxStepWidth = 640.dp

/** Steps are derived from the ViewModel state, so a rotation keeps the step the user was on. */
private enum class PasswordStep { Pick, Inspecting, Set, Unlock, Working, Done }

private fun PdfToolUiState.passwordStep(): PasswordStep = when {
    result != null -> PasswordStep.Done
    isProcessing -> PasswordStep.Working
    sources.isEmpty() -> PasswordStep.Pick
    isPdfAlreadyProtected() -> PasswordStep.Unlock
    info != null -> PasswordStep.Set
    else -> PasswordStep.Inspecting
}

@Composable
fun PdfPasswordRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
    initialSource: PdfToolSource? = null,
) {
    val viewModel: PdfPasswordViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val restrictEditing by viewModel.restrictEditing.collectAsStateWithLifecycle()
    val passwordError by viewModel.passwordError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val step = uiState.passwordStep()

    // Opened from the reader's menu with that PDF: skip the picker, and back returns to the reader.
    val fromReader = initialSource != null
    var initialSourceApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialSource) {
        if (initialSource != null && !initialSourceApplied) {
            initialSourceApplied = true
            viewModel.onSourcesChosen(listOf(initialSource))
        }
    }
    val backFromFile: () -> Unit = { if (fromReader) onNavigateUp() else viewModel.reset() }
    val isProtect = uiState.passwordMode == PdfPasswordMode.Protect
    val fileName = uiState.sources.firstOrNull()?.label().orEmpty()
    val pageCountText = uiState.info?.pageCount?.takeIf { it > 0 }?.let(::formatPdfPageCount)
    val fileSubtitle = listOfNotNull(fileName.ifBlank { null }, pageCountText).joinToString(" · ")

    val devicePicker = rememberSinglePdfPicker { uri ->
        viewModel.onSourcesChosen(
            listOf(PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri))),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            // Wrong-password messages are already shown inline under the field.
            if (event is PdfToolEvent.ShowMessage && event.message != viewModel.passwordError.value) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    BackHandler(enabled = step == PasswordStep.Working) {
        // Locking or unlocking cannot be cancelled safely; stay on the working state.
    }
    BackHandler(enabled = step == PasswordStep.Set || step == PasswordStep.Unlock || step == PasswordStep.Inspecting) {
        backFromFile()
    }
    BackHandler(enabled = step == PasswordStep.Done, onBack = onNavigateUp)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (step) {
            PasswordStep.Pick -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                PdfFilePickerScreen(
                    title = stringResource(R.string.export_share_pdf_password),
                    subtitle = stringResource(R.string.pdf_password_lock_a_pdf_or_remove),
                    stepText = stringResource(R.string.pdf_compress_step_1_of_3_choose),
                    documents = uiState.libraryDocuments,
                    onBack = onNavigateUp,
                    onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
                    onPickLibrary = { document ->
                        viewModel.onSourcesChosen(
                            listOf(PdfToolSource.LibraryDocument(document.id, document.title)),
                        )
                    },
                    modifier = Modifier.widthIn(max = MaxStepWidth).fillMaxWidth(),
                )
            }
            PasswordStep.Inspecting -> InspectingStep(
                subtitle = fileSubtitle,
                onBack = backFromFile,
            )
            PasswordStep.Set -> SetPasswordStep(
                source = uiState.sources.firstOrNull(),
                subtitle = fileSubtitle,
                password = uiState.password,
                confirmPassword = uiState.confirmPassword,
                restrictEditing = restrictEditing,
                onPasswordChange = viewModel::setPassword,
                onConfirmPasswordChange = viewModel::setConfirmPassword,
                onRestrictEditingChange = viewModel::setRestrictEditing,
                onChangeFile = viewModel::reset,
                onLock = viewModel::submit,
            )
            PasswordStep.Unlock -> UnlockPasswordStep(
                subtitle = fileSubtitle,
                password = uiState.currentPassword,
                error = passwordError,
                onPasswordChange = viewModel::setCurrentPassword,
                onChangeFile = viewModel::reset,
                onRemove = viewModel::submit,
            )
            PasswordStep.Working -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                ToolWorkingState(
                    title = if (isProtect) stringResource(R.string.pdf_password_locking) else stringResource(R.string.pdf_password_unlocking),
                    caption = fileName,
                    modifier = Modifier.widthIn(max = MaxStepWidth).fillMaxSize(),
                )
            }
            PasswordStep.Done -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                PdfToolDoneScreen(
                    title = if (isProtect) stringResource(R.string.pdf_password_pdf_locked) else stringResource(R.string.pdf_password_password_removed),
                    subtitle = if (isProtect) {
                        stringResource(R.string.pdf_password_anyone_opening_it_will_need)
                    } else {
                        stringResource(R.string.pdf_password_it_now_opens_without_a)
                    },
                    preview = uiState.resultPreview,
                    outputName = uiState.outputName,
                    onOutputNameChange = viewModel::setOutputName,
                    details = passwordResultDetails(uiState, restrictEditing),
                    onClose = onNavigateUp,
                    onOpenPreview = { uiState.namedResult()?.let(onPreviewPdf) },
                    onSave = viewModel::saveResult,
                    onShare = {
                        viewModel.prepareNamedShare { artifact ->
                            shareExportArtifact(
                                context,
                                artifact,
                                if (isProtect) appString(R.string.pdf_password_protected_pdf) else appString(R.string.pdf_password_unlocked_pdf),
                            )
                        }
                    },
                    // The shared result screen caps its own column and splits on wide windows.
                    modifier = Modifier.fillMaxWidth(),
                    isSaving = uiState.isProcessing && uiState.result != null,
                    savedDestinationLabel = uiState.savedDestinationLabel,
                    previewBadge = { PasswordResultBadge(locked = isProtect) },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )
    }
}

@Composable
private fun InspectingStep(
    subtitle: String,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MaxStepWidth).fillMaxSize()) {
            ToolCompactHeader(
                title = stringResource(R.string.export_share_pdf_password),
                subtitle = subtitle.ifBlank { null },
                onBack = onBack,
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.pdf_password_checking_the_file),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetPasswordStep(
    source: PdfToolSource?,
    subtitle: String,
    password: String,
    confirmPassword: String,
    restrictEditing: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onRestrictEditingChange: (Boolean) -> Unit,
    onChangeFile: () -> Unit,
    onLock: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    if (showPreview && source != null) {
        PdfPeekDialog(source = source, password = null, onDismiss = { showPreview = false })
    }
    val strength = pdfPasswordStrength(password)
    val lengthOk = password.length in PDF_PASSWORD_MIN_LENGTH..PDF_PASSWORD_MAX_LENGTH
    val matches = password.isNotEmpty() && password == confirmPassword

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MaxStepWidth).fillMaxSize()) {
            ToolCompactHeader(
                title = stringResource(R.string.pdf_password_add_password),
                subtitle = subtitle.ifBlank { null },
                onBack = onChangeFile,
                actionLabel = stringResource(R.string.storage_change),
                onAction = onChangeFile,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ToolTokens.ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Check it is the right file before locking it.
                if (source != null) {
                    ToolSecondaryButton(
                        text = stringResource(R.string.pdf_peek_preview),
                        onClick = { showPreview = true },
                        icon = Icons.Outlined.Visibility,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolFieldLabel(stringResource(R.string.pdf_password_new_password))
                    ToolTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = stringResource(R.string.pdf_password_at_least_characters, PDF_PASSWORD_MIN_LENGTH),
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                        trailingIcon = {
                            PasswordVisibilityToggle(
                                visible = showPassword,
                                onToggle = { showPassword = !showPassword },
                            )
                        },
                        supportingText = { PasswordStrengthMeter(strength) },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolFieldLabel(stringResource(R.string.export_share_confirm_password))
                    ToolTextField(
                        value = confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        placeholder = stringResource(R.string.pdf_password_type_it_again),
                        visualTransformation = if (showConfirm) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        trailingIcon = {
                            PasswordVisibilityToggle(
                                visible = showConfirm,
                                onToggle = { showConfirm = !showConfirm },
                            )
                        },
                        supportingText = if (confirmPassword.isEmpty()) {
                            null
                        } else {
                            { PasswordMatchLine(matches = matches) }
                        },
                    )
                }
                ToolCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = restrictEditing,
                                role = Role.Switch,
                                onValueChange = onRestrictEditingChange,
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.pdf_password_restrict_editing),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.pdf_password_others_can_open_and_print),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = restrictEditing, onCheckedChange = null)
                    }
                }
                ToolNote(
                    text = stringResource(R.string.pdf_password_scanly_can_t_recover_this),
                    icon = Icons.Filled.Warning,
                    warning = true,
                )
            }
            ToolBottomBar {
                ToolPrimaryButton(
                    text = stringResource(R.string.pdf_password_lock_pdf),
                    onClick = onLock,
                    enabled = lengthOk && matches,
                    icon = Icons.Filled.Lock,
                )
            }
        }
    }
}

@Composable
private fun UnlockPasswordStep(
    subtitle: String,
    password: String,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onChangeFile: () -> Unit,
    onRemove: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MaxStepWidth).fillMaxSize()) {
            ToolCompactHeader(
                title = stringResource(R.string.pdf_password_remove_password),
                subtitle = subtitle.ifBlank { null },
                onBack = onChangeFile,
                actionLabel = stringResource(R.string.storage_change),
                onAction = onChangeFile,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ToolTokens.ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ToolPaperPage(
                        bitmap = null,
                        modifier = Modifier.width(120.dp),
                        overlay = {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = stringResource(R.string.pdf_password_locked_pdf),
                                tint = PaperInk,
                                modifier = Modifier.size(36.dp),
                            )
                        },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolFieldLabel(stringResource(R.string.pdf_password_current_password))
                    ToolTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = stringResource(R.string.pdf_password_password_for_this_pdf),
                        isError = error != null,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        trailingIcon = {
                            PasswordVisibilityToggle(
                                visible = showPassword,
                                onToggle = { showPassword = !showPassword },
                            )
                        },
                        supportingText = if (error != null) {
                            { PasswordErrorLine(message = error) }
                        } else {
                            {
                                Text(
                                    text = stringResource(R.string.pdf_password_bank_and_portal_pdfs_often),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
                ToolNote(
                    text = stringResource(R.string.pdf_password_the_unlocked_copy_opens_for),
                )
            }
            ToolBottomBar {
                ToolPrimaryButton(
                    text = stringResource(R.string.pdf_password_remove_password),
                    onClick = onRemove,
                    enabled = password.isNotBlank(),
                    icon = Icons.Filled.LockOpen,
                )
            }
        }
    }
}

@Composable
private fun PasswordVisibilityToggle(
    visible: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (visible) stringResource(R.string.export_share_hide_password) else stringResource(R.string.export_share_show_password),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PasswordErrorLine(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun PasswordMatchLine(matches: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (matches) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = if (matches) stringResource(R.string.pdf_password_passwords_match) else stringResource(R.string.pdf_password_doesn_t_match_yet),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PasswordStrengthMeter(strength: PdfPasswordStrength) {
    val tone = when (strength) {
        PdfPasswordStrength.Empty -> MaterialTheme.colorScheme.onSurfaceVariant
        PdfPasswordStrength.TooShort,
        PdfPasswordStrength.TooLong,
        PdfPasswordStrength.Weak,
        -> MaterialTheme.colorScheme.error
        PdfPasswordStrength.Fair -> toolWarningColors().content
        PdfPasswordStrength.Strong -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index < strength.filledSegments) {
                                    tone
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            ),
                    )
                }
            }
            Text(
                text = strength.label.orEmpty(),
                modifier = Modifier.widthIn(min = 64.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tone,
                textAlign = TextAlign.End,
            )
        }
        Text(
            text = stringResource(R.string.pdf_password_a_short_phrase_you_ll),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoxScope.PasswordResultBadge(locked: Boolean) {
    ToolBadge(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 12.dp, y = 12.dp),
        size = 40.dp,
    ) {
        Icon(
            imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Only facts the flow actually knows: the protection applied, the edit switch and real sizes. */
private fun passwordResultDetails(
    state: PdfToolUiState,
    restrictEditing: Boolean,
): List<ToolDetailRow> = buildList {
    val isProtect = state.passwordMode == PdfPasswordMode.Protect
    add(ToolDetailRow(appString(R.string.pdf_password_protection), if (isProtect) appString(R.string.pdf_password_aes_256_password) else appString(R.string.pdf_password_no_password)))
    if (isProtect) {
        add(ToolDetailRow(appString(R.string.pdf_password_editing), if (restrictEditing) appString(R.string.pdf_password_restricted) else appString(R.string.pdf_password_allowed)))
    }
    val file = listOfNotNull(
        state.info?.pageCount?.takeIf { it > 0 }?.let(::formatPdfPageCount),
        state.resultSizeBytes?.let { StorageFormatter.formatBytes(it) },
    ).joinToString(" · ")
    if (file.isNotEmpty()) add(ToolDetailRow(appString(R.string.pdf_password_file), file))
    add(ToolDetailRow(appString(R.string.filter_customize_original), appString(R.string.pdf_merge_kept_unchanged)))
}

/** Strength levels for the New password meter. Length follows the backend's 4-64 rule. */
internal enum class PdfPasswordStrength(@param:StringRes val labelRes: Int?, val filledSegments: Int) {
    Empty(null, 0),
    TooShort(R.string.pdf_password_strength_too_short, 1),
    TooLong(R.string.pdf_password_strength_too_long, 4),
    Weak(R.string.pdf_password_strength_weak, 1),
    Fair(R.string.pdf_password_strength_fair, 2),
    Strong(R.string.pdf_password_strength_strong, 4),
    ;

    val label: String? get() = labelRes?.let { appString(it) }
}

/** Scores a new password: length (8, 12), mixed case, and digits or symbols. */
internal fun pdfPasswordStrength(password: String): PdfPasswordStrength {
    val length = password.length
    return when {
        length == 0 -> PdfPasswordStrength.Empty
        length < PDF_PASSWORD_MIN_LENGTH -> PdfPasswordStrength.TooShort
        length > PDF_PASSWORD_MAX_LENGTH -> PdfPasswordStrength.TooLong
        else -> {
            var score = 0
            if (length >= 8) score++
            if (length >= 12) score++
            if (password.any { it.isLowerCase() } && password.any { it.isUpperCase() }) score++
            if (password.any { it.isDigit() } || password.any { !it.isLetterOrDigit() }) score++
            when {
                score <= 1 -> PdfPasswordStrength.Weak
                score == 2 -> PdfPasswordStrength.Fair
                else -> PdfPasswordStrength.Strong
            }
        }
    }
}
