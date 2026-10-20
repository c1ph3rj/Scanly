package `in`.c1ph3rj.scanly.feature.tools.qr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.domain.model.QrPayload
import `in`.c1ph3rj.scanly.feature.tools.ToolCard

/** Type icon for a decoded code (Material outlined set, neutral by design). */
fun qrKindIcon(payload: QrPayload): ImageVector = when (payload) {
    is QrPayload.Link -> Icons.Outlined.Link
    is QrPayload.Upi -> Icons.Outlined.Payments
    is QrPayload.Wifi -> Icons.Outlined.Wifi
    is QrPayload.Contact -> Icons.Outlined.Person
    is QrPayload.Email -> Icons.Outlined.Email
    is QrPayload.Phone -> Icons.Outlined.Call
    is QrPayload.Text -> Icons.AutoMirrored.Outlined.Notes
}

/** Icon for the card's primary button, matching what that action does. */
fun qrPrimaryActionIcon(action: QrPrimaryAction): ImageVector = when (action) {
    QrPrimaryAction.OpenLink -> Icons.AutoMirrored.Outlined.OpenInNew
    QrPrimaryAction.PayUpi -> Icons.Outlined.Payments
    QrPrimaryAction.JoinWifi -> Icons.Outlined.Wifi
    QrPrimaryAction.SaveContact -> Icons.Outlined.PersonAdd
    QrPrimaryAction.SendEmail -> Icons.Outlined.Email
    QrPrimaryAction.Call -> Icons.Outlined.Call
    QrPrimaryAction.CopyText -> Icons.Outlined.ContentCopy
}

/**
 * Rounded pill action for the QR screens' top and bottom bars ("Create", "Scan", "From photo",
 * "History"), so every QR action shares one shape. [dark] is used over the camera; otherwise it
 * follows the theme.
 */
@Composable
fun QrPillButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (dark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** One row of a definition list: label column, value, optional trailing control. */
data class QrDetailField(
    val label: String,
    val value: String,
    val monospace: Boolean = false,
    val muted: Boolean = false,
    val trailing: (@Composable () -> Unit)? = null,
)

/** Bordered card of label/value rows with hairline separators (design "dl" blocks). */
@Composable
fun QrDetailCard(
    fields: List<QrDetailField>,
    modifier: Modifier = Modifier,
) {
    ToolCard(modifier = modifier.fillMaxWidth()) {
        Column {
            fields.forEachIndexed { index, field ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
                QrDetailRow(field)
            }
        }
    }
}

@Composable
private fun QrDetailRow(field: QrDetailField) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 14.dp,
                end = if (field.trailing != null) 4.dp else 14.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = field.value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                fontFamily = if (field.monospace) FontFamily.Monospace else FontFamily.Default,
            ),
            fontWeight = if (field.muted) FontWeight.Normal else FontWeight.SemiBold,
            color = if (field.muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
        )
        field.trailing?.invoke()
    }
}

/** Empty-state block: a bold line and a muted explanation, centred. */
@Composable
fun QrEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    pipLine: String? = null,
    pipMood: `in`.c1ph3rj.scanly.feature.pip.PipMood = `in`.c1ph3rj.scanly.feature.pip.PipMood.IDLE,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (pipLine != null && `in`.c1ph3rj.scanly.feature.pip.LocalPipPreferences.current.enabled) {
            `in`.c1ph3rj.scanly.feature.pip.PipSays(
                line = pipLine,
                mood = pipMood,
                size = 80.dp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
