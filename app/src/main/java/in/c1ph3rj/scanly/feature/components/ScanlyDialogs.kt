package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import kotlinx.coroutines.launch

// ─── Dialogs ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanlyFormDialogShell(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = 24.dp,
    maxWidth: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val adaptiveMaxWidth = if (windowSizeInfo.isTablet) windowSizeInfo.dialogMaxWidth else maxWidth
    val horizontalPadding = if (windowSizeInfo.isTablet) 32.dp else horizontalMargin

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .widthIn(max = adaptiveMaxWidth)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun ScanlyConfirmDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    confirmDestructive: Boolean = false,
    dismissEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
) {
    ScanlyFormDialogShell(onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = dismissEnabled,
            ) {
                Text(dismissLabel)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
            ) {
                Text(
                    text = confirmLabel,
                    color = if (confirmDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
fun ScanlySheetContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = modifier
                .then(
                    if (windowSizeInfo.isTablet) {
                        Modifier.widthIn(max = windowSizeInfo.sheetMaxWidth)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
                .padding(horizontal = if (windowSizeInfo.isTablet) 24.dp else 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ScanlyDialogActions(
    onDismiss: () -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
        ) { Text(confirmLabel) }
    }
}

@Composable
fun DocumentTitleSuggestRow(
    onSuggestTitle: suspend (DocumentTitleFormat) -> String,
    onSuggested: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isSuggesting by remember { mutableStateOf(false) }
    var formatIndex by rememberSaveable { mutableIntStateOf(0) }
    val activeFormat = DocumentTitleFormat.entries[formatIndex]

    fun suggestWithActiveFormat() {
        if (isSuggesting) return
        val format = activeFormat
        scope.launch {
            isSuggesting = true
            try {
                onSuggested(onSuggestTitle(format))
                formatIndex = (formatIndex + 1) % DocumentTitleFormat.entries.size
            } finally {
                isSuggesting = false
            }
        }
    }

    OutlinedButton(
        onClick = ::suggestWithActiveFormat,
        enabled = !isSuggesting,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSuggesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Suggest name",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = activeFormat.shortLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DocumentTitleDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onSuggestTitle: (suspend (DocumentTitleFormat) -> String)? = null,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    ScanlyFormDialogShell(onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onSuggestTitle != null) {
            DocumentTitleSuggestRow(
                onSuggestTitle = onSuggestTitle,
                onSuggested = { value = it },
            )
        }
        ScanlyDialogActions(
            onDismiss = onDismiss,
            confirmLabel = confirmLabel,
            confirmEnabled = value.isNotBlank(),
            onConfirm = { if (value.isNotBlank()) onConfirm(value) },
        )
    }
}

@Composable
fun GroupTitleSuggestRow(
    onSuggestTitle: suspend (GroupTitleFormat) -> String,
    onSuggested: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isSuggesting by remember { mutableStateOf(false) }
    var formatIndex by rememberSaveable { mutableIntStateOf(0) }
    val activeFormat = GroupTitleFormat.entries[formatIndex]

    fun suggestWithActiveFormat() {
        if (isSuggesting) return
        val format = activeFormat
        scope.launch {
            isSuggesting = true
            try {
                onSuggested(onSuggestTitle(format))
                formatIndex = (formatIndex + 1) % GroupTitleFormat.entries.size
            } finally {
                isSuggesting = false
            }
        }
    }

    OutlinedButton(
        onClick = ::suggestWithActiveFormat,
        enabled = !isSuggesting,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSuggesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Suggest name",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = activeFormat.shortLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun GroupNameDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmLabel: String = "Create",
    onSuggestTitle: (suspend (GroupTitleFormat) -> String)? = null,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    ScanlyFormDialogShell(onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("Folder name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onSuggestTitle != null) {
            GroupTitleSuggestRow(
                onSuggestTitle = onSuggestTitle,
                onSuggested = { value = it },
            )
        }
        ScanlyDialogActions(
            onDismiss = onDismiss,
            confirmLabel = confirmLabel,
            confirmEnabled = value.isNotBlank(),
            onConfirm = { if (value.isNotBlank()) onConfirm(value) },
        )
    }
}
