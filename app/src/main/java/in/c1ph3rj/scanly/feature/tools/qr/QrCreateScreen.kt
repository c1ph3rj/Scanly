package `in`.c1ph3rj.scanly.feature.tools.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.domain.model.QrCreateType
import `in`.c1ph3rj.scanly.domain.model.WifiSecurity
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolChoiceChip
import `in`.c1ph3rj.scanly.feature.tools.ToolColorSwatch
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSecondaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSegmented
import `in`.c1ph3rj.scanly.feature.tools.ToolTextField

private val PaperInk = Color(0xFF59636A)

/**
 * Create tab: pick a type, fill its fields, and see the code update. The preview is always
 * printed on white paper; the caption and colour choice apply to the saved image.
 */
@Composable
fun QrCreateScreen(
    draft: QrCreateDraft,
    preview: Bitmap?,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSelectScan: () -> Unit,
    onUpdate: ((QrCreateDraft) -> QrCreateDraft) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val payload = remember(draft) { qrCreatePayload(draft) }
    val canExport = payload != null && !isSaving

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = "Create QR",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            QrPillButton(
                label = "Scan",
                icon = Icons.Outlined.CameraAlt,
                onClick = onSelectScan,
                dark = false,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QrCreateType.entries.forEach { type ->
                ToolChoiceChip(
                    label = type.label,
                    selected = type == draft.type,
                    onClick = { onUpdate { it.copy(type = type) } },
                    solidWhenSelected = true,
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                QrPreviewPaper(
                    preview = preview,
                    hasPayload = payload != null,
                    ink = Color(draft.ink.argb),
                    caption = draft.caption.trim(),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                QrCreateFields(draft = draft, onUpdate = onUpdate)

                ToolFieldLabel("Caption under the code · optional", muted = true)
                ToolTextField(
                    value = draft.caption,
                    onValueChange = { value ->
                        if (value.length <= QR_CREATE_CAPTION_MAX_LENGTH) {
                            onUpdate { it.copy(caption = value) }
                        }
                    },
                    placeholder = "Shown under the code",
                    supportingText = {
                        Text(
                            text = "${draft.caption.length} / $QR_CREATE_CAPTION_MAX_LENGTH",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ToolFieldLabel("Colour", muted = true, modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QrInkColor.entries.forEach { ink ->
                            ToolColorSwatch(
                                color = Color(ink.argb),
                                name = ink.label,
                                selected = ink == draft.ink,
                                onClick = { onUpdate { it.copy(ink = ink) } },
                            )
                        }
                    }
                }
                ToolNote(text = "Only dark colours, so every camera can read the code.")
            }
        }

        ToolBottomBar {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolSecondaryButton(
                    text = "Save image",
                    onClick = onSave,
                    enabled = canExport,
                    icon = Icons.Outlined.FileDownload,
                    modifier = Modifier.weight(1f),
                )
                ToolPrimaryButton(
                    text = "Share",
                    onClick = onShare,
                    enabled = canExport,
                    icon = Icons.Outlined.Share,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Saved to QR history · PNG, 1024 px",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** White paper card: the code (or a placeholder) with the caption printed in the ink colour. */
@Composable
private fun QrPreviewPaper(
    preview: Bitmap?,
    hasPayload: Boolean,
    ink: Color,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(240.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(196.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null && !preview.isRecycled) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "QR code preview",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCode2,
                        contentDescription = null,
                        tint = PaperInk.copy(alpha = 0.4f),
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = formatQrGeneratePlaceholderHint(!hasPayload),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PaperInk,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Bold,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.heightIn(min = 17.dp),
        )
    }
}

@Composable
private fun QrCreateFields(
    draft: QrCreateDraft,
    onUpdate: ((QrCreateDraft) -> QrCreateDraft) -> Unit,
) {
    when (draft.type) {
        QrCreateType.Link -> QrLinkFields(draft.url) { value -> onUpdate { it.copy(url = value) } }
        QrCreateType.Text -> QrTextFields(draft.text) { value ->
            if (value.length <= QR_CREATE_TEXT_MAX_LENGTH) onUpdate { it.copy(text = value) }
        }
        QrCreateType.Wifi -> QrWifiFields(draft, onUpdate)
        QrCreateType.Upi -> QrUpiFields(draft, onUpdate)
        QrCreateType.Contact -> QrContactFields(draft, onUpdate)
    }
}

@Composable
private fun QrLinkFields(value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ToolFieldLabel("Web address", muted = true)
        ToolTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "https://example.com",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.testTag(ScanlyTestTags.QR_GENERATE_INPUT),
        )
    }
}

@Composable
private fun QrTextFields(value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ToolFieldLabel("Text", muted = true)
        ToolTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "Anything up to $QR_CREATE_TEXT_MAX_LENGTH characters",
            singleLine = false,
            minLines = 3,
            supportingText = {
                Text(
                    text = "${value.length} / $QR_CREATE_TEXT_MAX_LENGTH characters",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Composable
private fun QrWifiFields(
    draft: QrCreateDraft,
    onUpdate: ((QrCreateDraft) -> QrCreateDraft) -> Unit,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolFieldLabel("Network name", muted = true)
            ToolTextField(
                value = draft.wifiSsid,
                onValueChange = { value -> onUpdate { it.copy(wifiSsid = value) } },
                placeholder = "Network name",
            )
        }
        if (draft.wifiSecurity != WifiSecurity.OPEN) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolFieldLabel("Password", muted = true)
                ToolTextField(
                    value = draft.wifiPassword,
                    onValueChange = { value -> onUpdate { it.copy(wifiPassword = value) } },
                    placeholder = "Network password",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(44.dp),
                        ) {
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
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolFieldLabel("Security", muted = true)
            ToolSegmented(
                options = WifiSecurity.entries,
                selected = draft.wifiSecurity,
                onSelect = { security -> onUpdate { it.copy(wifiSecurity = security) } },
                label = { security -> security.label },
            )
        }
    }
}

@Composable
private fun QrUpiFields(
    draft: QrCreateDraft,
    onUpdate: ((QrCreateDraft) -> QrCreateDraft) -> Unit,
) {
    val amountValid = qrUpiAmountIsValid(draft.upiAmount)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolFieldLabel("UPI ID", muted = true)
            ToolTextField(
                value = draft.upiAddress,
                onValueChange = { value -> onUpdate { it.copy(upiAddress = value) } },
                placeholder = "name@bank",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolFieldLabel("Payee name", muted = true)
                ToolTextField(
                    value = draft.upiPayee,
                    onValueChange = { value -> onUpdate { it.copy(upiPayee = value) } },
                    placeholder = "Shop name",
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolFieldLabel("Amount · optional", muted = true)
                ToolTextField(
                    value = draft.upiAmount,
                    onValueChange = { value -> onUpdate { it.copy(upiAmount = value) } },
                    placeholder = "₹ Any",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !amountValid,
                    supportingText = if (amountValid) null else ({ Text("Enter an amount above zero") }),
                )
            }
        }
        Text(
            text = "Leave the amount empty so customers can enter what they owe.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QrContactFields(
    draft: QrCreateDraft,
    onUpdate: ((QrCreateDraft) -> QrCreateDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolFieldLabel("Name", muted = true)
            ToolTextField(
                value = draft.contactName,
                onValueChange = { value -> onUpdate { it.copy(contactName = value) } },
                placeholder = "Full name",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolFieldLabel("Phone", muted = true)
                ToolTextField(
                    value = draft.contactPhone,
                    onValueChange = { value -> onUpdate { it.copy(contactPhone = value) } },
                    placeholder = "Phone number",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolFieldLabel("Email", muted = true)
                ToolTextField(
                    value = draft.contactEmail,
                    onValueChange = { value -> onUpdate { it.copy(contactEmail = value) } },
                    placeholder = "Email",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
        }
    }
}
