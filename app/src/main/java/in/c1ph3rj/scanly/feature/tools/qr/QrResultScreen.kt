package `in`.c1ph3rj.scanly.feature.tools.qr

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.domain.model.QrOrigin
import `in`.c1ph3rj.scanly.domain.model.QrPayload
import `in`.c1ph3rj.scanly.domain.model.QrPayloadParser
import `in`.c1ph3rj.scanly.domain.model.WifiSecurity
import `in`.c1ph3rj.scanly.domain.model.headline
import `in`.c1ph3rj.scanly.domain.model.kindLabel
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolIconWell
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSecondaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSectionLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolTopBar
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString
import androidx.compose.ui.res.stringResource

/** Result of one decoded or saved code: type-shaped details, raw content and the actions. */
@Composable
fun QrResultScreen(
    item: QrResultItem,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val payload: QrPayload = remember(item.raw) { QrPayloadParser.parse(item.raw) }
    val primary = qrPrimaryActionFor(payload)
    val secondary = resultSecondaryAction(payload, context)
    var rawExpanded by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val verb = if (item.origin == QrOrigin.Created) stringResource(R.string.qr_history_created) else stringResource(R.string.qr_history_scanned)
    val savedNote = if (item.id != null) stringResource(R.string.qr_result_saved_to_history) else ""
    val now = System.currentTimeMillis()
    val stepText = "$verb ${formatQrRelativeTime(item.createdAtMillis, now)}$savedNote"

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolTopBar(
            onBack = onBack,
            stepText = stepText,
            trailing = {
                IconButton(onClick = onDelete, enabled = item.id != null, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.qr_result_delete_from_history),
                    )
                }
            },
        )

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                QrResultHeader(payload)
                QrResultDetails(
                    payload = payload,
                    passwordVisible = passwordVisible,
                    onTogglePassword = { passwordVisible = !passwordVisible },
                )
                QrRawContent(
                    raw = item.raw,
                    expanded = rawExpanded,
                    onToggle = { rawExpanded = !rawExpanded },
                )
            }
        }

        // Short windows put all three actions on one row instead of stacking two rows of buttons.
        val oneRow = rememberWindowSizeInfo().isHeightConstrained
        ToolBottomBar {
            if (!oneRow) {
                ToolPrimaryButton(
                    text = primary.resultLabel,
                    onClick = { runQrPrimaryAction(context, payload)?.let(onMessage) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (oneRow) {
                    ToolPrimaryButton(
                        text = primary.resultLabel,
                        onClick = { runQrPrimaryAction(context, payload)?.let(onMessage) },
                        modifier = Modifier.weight(1.4f),
                    )
                }
                secondary?.let { action ->
                    ToolSecondaryButton(
                        text = action.label,
                        onClick = { action.run()?.let(onMessage) },
                        modifier = Modifier.weight(1f),
                    )
                }
                ToolSecondaryButton(
                    text = stringResource(R.string.document_detail_share_2),
                    onClick = { shareQrText(context, item.raw)?.let(onMessage) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The action beside Share. */
private class QrSecondaryAction(val label: String, val run: () -> String?)

private fun resultSecondaryAction(payload: QrPayload, context: Context): QrSecondaryAction? =
    when (payload) {
        is QrPayload.Link -> QrSecondaryAction(appString(R.string.qr_result_copy_link)) {
            copyQrText(context, appString(R.string.qr_payload_link), payload.url)
        }
        is QrPayload.Upi -> QrSecondaryAction(appString(R.string.qr_result_copy_upi_id)) {
            copyQrText(context, appString(R.string.qr_create_upi_id), payload.payeeAddress)
        }
        is QrPayload.Wifi -> {
            val password = payload.password
            if (password != null) {
                QrSecondaryAction(appString(R.string.qr_result_copy_password)) { copyQrText(context, appString(R.string.qr_actions_wi_fi_password), password) }
            } else {
                QrSecondaryAction(appString(R.string.qr_result_copy_network_name)) { copyQrText(context, appString(R.string.qr_create_network_name), payload.ssid) }
            }
        }
        is QrPayload.Contact -> payload.phone?.let { phone ->
            QrSecondaryAction(appString(R.string.qr_result_call)) { dialQrNumber(context, phone) }
        }
        is QrPayload.Email -> QrSecondaryAction(appString(R.string.qr_result_copy_address)) {
            copyQrText(context, appString(R.string.qr_result_email_address), payload.address)
        }
        is QrPayload.Phone -> QrSecondaryAction(appString(R.string.qr_result_copy_number)) {
            copyQrText(context, appString(R.string.qr_payload_phone_number), payload.number)
        }
        is QrPayload.Text -> QrSecondaryAction(appString(R.string.qr_result_search_the_web)) {
            webSearchQr(context, payload.raw.take(200))
        }
    }

@Composable
private fun QrResultHeader(payload: QrPayload) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToolIconWell(
            icon = qrKindIcon(payload),
            size = 44.dp,
            iconSize = 22.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolSectionLabel(payload.kindLabel)
            Text(
                text = payload.headline,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    letterSpacing = (-0.5).sp,
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun QrResultDetails(
    payload: QrPayload,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
) {
    when (payload) {
        is QrPayload.Link -> QrLinkDetails(payload)
        is QrPayload.Upi -> QrUpiDetails(payload)
        is QrPayload.Wifi -> QrWifiDetails(payload, passwordVisible, onTogglePassword)
        is QrPayload.Contact -> QrContactDetails(payload)
        is QrPayload.Email -> QrDetailCard(
            fields = buildList {
                add(QrDetailField(stringResource(R.string.qr_result_address), payload.address))
                payload.subject?.let { add(QrDetailField(stringResource(R.string.qr_result_subject), it)) }
            },
        )
        is QrPayload.Phone -> QrDetailCard(
            fields = listOf(QrDetailField(stringResource(R.string.qr_result_number), payload.number, monospace = true)),
        )
        is QrPayload.Text -> QrTextBody(payload.raw)
    }
}

@Composable
private fun QrLinkDetails(payload: QrPayload.Link) {
    val address = qrLinkAddressParts(payload.url)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ToolCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.qr_result_full_address),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = muted,
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = muted)) { append(address.scheme) }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(address.host) }
                    append(address.rest)
                },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    ToolNote(
        text = if (payload.isSecure) {
            stringResource(R.string.qr_result_check_the_domain_is_the)
        } else {
            stringResource(R.string.qr_result_this_link_isn_t_encrypted)
        },
        icon = if (payload.isSecure) Icons.Outlined.Info else Icons.Outlined.Warning,
        warning = !payload.isSecure,
    )
}

@Composable
private fun QrUpiDetails(payload: QrPayload.Upi) {
    QrDetailCard(
        fields = listOf(
            QrDetailField(stringResource(R.string.qr_result_payee), payload.payeeName ?: stringResource(R.string.qr_result_not_in_the_code)),
            QrDetailField(stringResource(R.string.qr_create_upi_id), payload.payeeAddress, monospace = true),
            QrDetailField(
                label = stringResource(R.string.qr_result_amount),
                value = payload.amount?.let { "₹$it" } ?: stringResource(R.string.qr_result_not_set_you_ll_enter),
                muted = payload.amount == null,
            ),
        ),
    )
    ToolNote(
        text = stringResource(R.string.qr_result_scanly_doesn_t_make_payments),
        icon = Icons.Outlined.Warning,
        warning = true,
    )
}

@Composable
private fun QrWifiDetails(
    payload: QrPayload.Wifi,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
) {
    val password = payload.password
    val fields = buildList {
        add(QrDetailField(stringResource(R.string.qr_result_network), payload.ssid.ifBlank { stringResource(R.string.qr_result_not_in_the_code) }))
        add(QrDetailField(stringResource(R.string.qr_create_security), payload.security.label))
        if (password != null) {
            add(
                QrDetailField(
                    label = stringResource(R.string.pdf_reader_password),
                    value = if (passwordVisible) password else qrWifiPasswordMask(password),
                    monospace = true,
                    trailing = {
                        IconButton(onClick = onTogglePassword, modifier = Modifier.size(48.dp)) {
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
                ),
            )
        }
    }
    QrDetailCard(fields = fields)
    if (payload.security == WifiSecurity.WEP) {
        ToolNote(
            text = stringResource(R.string.qr_result_wi_fi_settings_can_t),
            icon = Icons.Outlined.Info,
        )
    }
}

@Composable
private fun QrContactDetails(payload: QrPayload.Contact) {
    val fields = buildList {
        payload.name?.let { add(QrDetailField(stringResource(R.string.qr_create_name), it)) }
        payload.phone?.let { add(QrDetailField(stringResource(R.string.qr_payload_phone), it, monospace = true)) }
        payload.email?.let { add(QrDetailField(stringResource(R.string.qr_payload_email), it)) }
        payload.organization?.let { add(QrDetailField(stringResource(R.string.qr_result_organisation), it)) }
    }
    if (fields.isNotEmpty()) QrDetailCard(fields = fields)
}

@Composable
private fun QrTextBody(text: String) {
    ToolCard(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

/** Disclosure for the exact scanned text. Selectable so it can be copied by hand. */
@Composable
private fun QrRawContent(
    raw: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    ToolCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.qr_result_raw_content),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.qr_result_hide_raw_content) else stringResource(R.string.qr_result_show_raw_content),
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            if (expanded) {
                SelectionContainer {
                    Text(
                        text = raw,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    )
                }
            }
        }
    }
}
