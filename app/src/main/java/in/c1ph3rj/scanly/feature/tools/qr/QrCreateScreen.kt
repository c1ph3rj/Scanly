package `in`.c1ph3rj.scanly.feature.tools.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
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
import `in`.c1ph3rj.scanly.R
import androidx.compose.ui.res.stringResource

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
    val window = rememberWindowSizeInfo()
    val twoPane = window.isLandscape && window.widthClass != WindowWidthClass.Compact

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
                    contentDescription = stringResource(R.string.zoomable_image_back),
                )
            }
            Text(
                text = stringResource(R.string.qr_create_create_qr),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            QrPillButton(
                label = stringResource(R.string.home_scan),
                icon = Icons.Outlined.CameraAlt,
                onClick = onSelectScan,
                dark = false,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = if (twoPane) TwoPaneMaxWidth else 640.dp)
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

        val formFields: @Composable ColumnScope.() -> Unit = {
            QrCreateFields(draft = draft, onUpdate = onUpdate)

            ToolFieldLabel(stringResource(R.string.qr_create_caption_under_the_code_optional), muted = true)
            ToolTextField(
                value = draft.caption,
                onValueChange = { value ->
                    if (value.length <= QR_CREATE_CAPTION_MAX_LENGTH) {
                        onUpdate { it.copy(caption = value) }
                    }
                },
                placeholder = stringResource(R.string.qr_create_shown_under_the_code),
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
                ToolFieldLabel(stringResource(R.string.pdf_watermark_colour), muted = true, modifier = Modifier.weight(1f))
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
            ToolNote(text = stringResource(R.string.qr_create_only_dark_colours_so_every))
        }
        val previewCard: @Composable (Modifier) -> Unit = { previewModifier ->
            QrPreviewPaper(
                preview = preview,
                hasPayload = payload != null,
                ink = Color(draft.ink.argb),
                caption = draft.caption.trim(),
                modifier = previewModifier,
            )
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (twoPane) {
                // Wide landscape: the code stays in view beside the fields while you type.
                Row(
                    modifier = Modifier
                        .widthIn(max = TwoPaneMaxWidth)
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        previewCard(Modifier)
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = formFields,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    previewCard(Modifier.align(Alignment.CenterHorizontally))
                    formFields()
                }
            }
        }

        ToolBottomBar {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolSecondaryButton(
                    text = stringResource(R.string.qr_create_save_image),
                    onClick = onSave,
                    enabled = canExport,
                    icon = Icons.Outlined.FileDownload,
                    modifier = Modifier.weight(1f),
                )
                ToolPrimaryButton(
                    text = stringResource(R.string.document_detail_share_2),
                    onClick = onShare,
                    enabled = canExport,
                    icon = Icons.Outlined.Share,
                    modifier = Modifier.weight(1f),
                )
            }
            // Supporting line only where there is height for it.
            if (!window.isHeightConstrained) Text(
                text = stringResource(R.string.qr_create_saved_to_qr_history_png),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val TwoPaneMaxWidth = 1000.dp

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
                    contentDescription = stringResource(R.string.qr_create_qr_code_preview),
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
        ToolFieldLabel(stringResource(R.string.qr_create_web_address), muted = true)
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
        ToolFieldLabel(stringResource(R.string.qr_payload_text), muted = true)
        ToolTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.qr_create_anything_up_to_characters, QR_CREATE_TEXT_MAX_LENGTH),
            singleLine = false,
            minLines = 3,
            supportingText = {
                Text(
                    text = stringResource(R.string.qr_create_characters, value.length, QR_CREATE_TEXT_MAX_LENGTH),
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
            ToolFieldLabel(stringResource(R.string.qr_create_network_name), muted = true)
            ToolTextField(
                value = draft.wifiSsid,
                onValueChange = { value -> onUpdate { it.copy(wifiSsid = value) } },
                placeholder = stringResource(R.string.qr_create_network_name),
            )
        }
        if (draft.wifiSecurity != WifiSecurity.OPEN) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolFieldLabel(stringResource(R.string.pdf_reader_password), muted = true)
                ToolTextField(
                    value = draft.wifiPassword,
                    onValueChange = { value -> onUpdate { it.copy(wifiPassword = value) } },
                    placeholder = stringResource(R.string.qr_create_network_password),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (passwordVisible) stringResource(R.string.export_share_hide_password) else stringResource(R.string.export_share_show_password),
                            )
                        }
                    },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolFieldLabel(stringResource(R.string.qr_create_security), muted = true)
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
            ToolFieldLabel(stringResource(R.string.qr_create_upi_id), muted = true)
            ToolTextField(
                value = draft.upiAddress,
                onValueChange = { value -> onUpdate { it.copy(upiAddress = value) } },
                placeholder = stringResource(R.string.qr_create_name_bank),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolFieldLabel(stringResource(R.string.qr_create_payee_name), muted = true)
                ToolTextField(
                    value = draft.upiPayee,
                    onValueChange = { value -> onUpdate { it.copy(upiPayee = value) } },
                    placeholder = stringResource(R.string.qr_create_shop_name),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolFieldLabel(stringResource(R.string.qr_create_amount_optional), muted = true)
                ToolTextField(
                    value = draft.upiAmount,
                    onValueChange = { value -> onUpdate { it.copy(upiAmount = value) } },
                    placeholder = stringResource(R.string.qr_create_any),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !amountValid,
                    supportingText = if (amountValid) null else ({ Text(stringResource(R.string.qr_create_enter_an_amount_above_zero)) }),
                )
            }
        }
        Text(
            text = stringResource(R.string.qr_create_leave_the_amount_empty_so),
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
            ToolFieldLabel(stringResource(R.string.qr_create_name), muted = true)
            ToolTextField(
                value = draft.contactName,
                onValueChange = { value -> onUpdate { it.copy(contactName = value) } },
                placeholder = stringResource(R.string.qr_create_full_name),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolFieldLabel(stringResource(R.string.qr_payload_phone), muted = true)
                ToolTextField(
                    value = draft.contactPhone,
                    onValueChange = { value -> onUpdate { it.copy(contactPhone = value) } },
                    placeholder = stringResource(R.string.qr_payload_phone_number),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolFieldLabel(stringResource(R.string.qr_payload_email), muted = true)
                ToolTextField(
                    value = draft.contactEmail,
                    onValueChange = { value -> onUpdate { it.copy(contactEmail = value) } },
                    placeholder = stringResource(R.string.qr_payload_email),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
        }
    }
}
