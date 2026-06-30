package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.StayCurrentLandscape
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.MaximumPdfPasswordLength
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfPageMargin
import `in`.c1ph3rj.scanly.domain.model.PdfPageNumber
import `in`.c1ph3rj.scanly.domain.model.PdfPageOrientation
import `in`.c1ph3rj.scanly.domain.model.PdfPageSize
import `in`.c1ph3rj.scanly.domain.model.validationError

/**
 * A single tappable export/share action row used across the document and folder
 * export sheets. Optionally shows a supporting description line.
 */
@Composable
fun ExportActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    val enabledTitleColor = MaterialTheme.colorScheme.onSurface
    val enabledDescriptionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else disabledContentColor,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) enabledTitleColor else disabledContentColor,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) enabledDescriptionColor else disabledContentColor,
                    )
                }
            }
        }
    }
}

/**
 * Bottom sheet that lets the user tune PDF layout options before generating a PDF
 * (for a single document or a whole folder).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOptionsSheet(
    options: PdfExportOptions,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onOptionsChanged: (PdfExportOptions) -> Unit,
    onConfirm: () -> Unit,
    titleText: String = "PDF options",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var passwordConfirmation by remember { mutableStateOf(options.password.orEmpty()) }
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordError = options.validationError()
    val confirmationError = when {
        options.password == null -> null
        passwordConfirmation.isEmpty() -> "Confirm the password."
        passwordConfirmation != options.password -> "Passwords do not match."
        else -> null
    }
    val confirmEnabled = passwordError == null && confirmationError == null
    val configuration = LocalConfiguration.current
    val maxSheetContentHeight = minOf(
        760.dp,
        (configuration.screenHeightDp * 0.94f).dp,
    )
    val scrollState = rememberScrollState()
    val nestedScrollConnection = rememberPdfOptionsNestedScrollConnection(
        scrollState = scrollState,
        sheetState = sheetState,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetGesturesEnabled = false,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxSheetContentHeight)
                    .widthIn(max = 720.dp)
                    .imePadding(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Choose how every page is generated.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection)
                            .verticalScroll(
                                state = scrollState,
                                flingBehavior = ScrollableDefaults.flingBehavior(),
                            )
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        PdfPasswordSection(
                            password = options.password,
                            passwordConfirmation = passwordConfirmation,
                            passwordVisible = passwordVisible,
                            passwordError = passwordError,
                            confirmationError = confirmationError,
                            onProtectionChanged = { enabled ->
                                passwordConfirmation = ""
                                onOptionsChanged(options.copy(password = if (enabled) "" else null))
                            },
                            onPasswordChanged = { password ->
                                onOptionsChanged(options.copy(password = password))
                            },
                            onConfirmationChanged = { confirmation ->
                                passwordConfirmation = confirmation
                            },
                            onPasswordVisibilityChanged = { passwordVisible = !passwordVisible },
                        )

                        PdfOptionSection(
                            title = "Page numbers",
                            description = "Add the page index to the PDF footer.",
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PdfPageNumber.entries.forEach { pageNumber ->
                                    PdfChoiceTile(
                                        label = pageNumber.label,
                                        icon = pageNumber.icon,
                                        selected = options.pageNumber == pageNumber,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onOptionsChanged(options.copy(pageNumber = pageNumber))
                                        },
                                    )
                                }
                            }
                        }

                        PdfOptionSection(
                            title = "Orientation",
                            description = "Auto follows each scanned page.",
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PdfPageOrientation.entries.forEach { orientation ->
                                    PdfChoiceTile(
                                        label = orientation.label,
                                        icon = orientation.icon,
                                        selected = options.orientation == orientation,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onOptionsChanged(options.copy(orientation = orientation))
                                        },
                                    )
                                }
                            }
                        }

                        PdfOptionSection(
                            title = "Page size",
                            description = "Fixed sizes use their real print dimensions.",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PdfPageSize.entries.chunked(2).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        rowItems.forEach { pageSize ->
                                            PdfChoiceTile(
                                                label = pageSize.label,
                                                supportingText = pageSize.dimensionsLabel,
                                                selected = options.pageSize == pageSize,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    onOptionsChanged(options.copy(pageSize = pageSize))
                                                },
                                            )
                                        }
                                        if (rowItems.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        PdfOptionSection(
                            title = "Margins",
                            description = "Add white space around the scanned page.",
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PdfPageMargin.entries.forEach { margin ->
                                    PdfChoiceTile(
                                        label = margin.label,
                                        selected = options.margin == margin,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onOptionsChanged(options.copy(margin = margin))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    ) {
                        Text(text = "Cancel")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = confirmEnabled,
                        onClick = onConfirm,
                    ) {
                        Text(text = confirmLabel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberPdfOptionsNestedScrollConnection(
    scrollState: ScrollState,
    sheetState: SheetState,
): NestedScrollConnection = remember(scrollState, sheetState) {
    object : NestedScrollConnection {
        private fun atBottom(): Boolean =
            scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 2

        private fun atTop(): Boolean = scrollState.value <= 0

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (available.y == 0f) return Offset.Zero
            if (available.y > 0 && atBottom()) {
                return Offset(0f, available.y)
            }
            if (available.y < 0 && atTop()) {
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (available.y > 0 && atBottom()) {
                return available
            }
            if (available.y < 0 && atTop() && sheetState.currentValue == SheetValue.Expanded) {
                return available
            }
            return Velocity.Zero
        }
    }
}

@Composable
private fun PdfOptionSection(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

private val PdfPageNumber.icon: ImageVector
    get() = when (this) {
        PdfPageNumber.NONE -> Icons.Outlined.Block
        PdfPageNumber.BOTTOM_LEFT -> Icons.Outlined.FormatAlignLeft
        PdfPageNumber.BOTTOM_CENTER -> Icons.Outlined.FormatAlignCenter
        PdfPageNumber.BOTTOM_RIGHT -> Icons.Outlined.FormatAlignRight
    }

@Composable
private fun PdfChoiceTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val selectedAccentColor = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                selectedAccentColor.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = if (supportingText == null) 72.dp else 68.dp)
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = if (icon == null) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) selectedAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    selectedAccentColor
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PdfPasswordSection(
    password: String?,
    passwordConfirmation: String,
    passwordVisible: Boolean,
    passwordError: String?,
    confirmationError: String?,
    onProtectionChanged: (Boolean) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmationChanged: (String) -> Unit,
    onPasswordVisibilityChanged: () -> Unit,
) {
    val enabled = password != null
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Password protection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (enabled) "A password will be required to open the PDF." else "Off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onProtectionChanged,
                )
            }

            if (enabled) {
                OutlinedTextField(
                    value = password.orEmpty(),
                    onValueChange = { value ->
                        onPasswordChanged(value.take(MaximumPdfPasswordLength))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PDF password") },
                    singleLine = true,
                    isError = passwordError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = onPasswordVisibilityChanged) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            )
                        }
                    },
                    supportingText = {
                        Text(passwordError ?: "4–64 characters. Send it separately from the PDF.")
                    },
                )
                OutlinedTextField(
                    value = passwordConfirmation,
                    onValueChange = { value ->
                        onConfirmationChanged(value.take(MaximumPdfPasswordLength))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm password") },
                    singleLine = true,
                    isError = confirmationError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        Text(confirmationError ?: "Passwords match.")
                    },
                )
            }
        }
    }
}

private val PdfPageOrientation.icon: ImageVector
    get() = when (this) {
        PdfPageOrientation.AUTO -> Icons.Outlined.ScreenRotation
        PdfPageOrientation.PORTRAIT -> Icons.Outlined.StayCurrentPortrait
        PdfPageOrientation.LANDSCAPE -> Icons.Outlined.StayCurrentLandscape
    }
