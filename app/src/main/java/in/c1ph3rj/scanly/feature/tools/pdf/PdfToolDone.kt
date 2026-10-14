package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolDetailList
import `in`.c1ph3rj.scanly.feature.tools.ToolDetailRow
import `in`.c1ph3rj.scanly.feature.tools.ToolFileNameField
import `in`.c1ph3rj.scanly.feature.tools.ToolPaperPage
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSecondaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolTopBar

/**
 * Shared "Step 3 of 3 · Done" screen for PDF tools (design: CompressDone, MergeDone,
 * PasswordDone, WatermarkDone).
 *
 * Layout: close + step label; a real first-page preview of the output (tap opens the
 * reader); headline + one-line summary; editable file name; a detail list of what was
 * applied; optional [extraContent] (shortcut tiles); and a footer with
 * "Save to phone" (outlined) + "Share" (primary).
 */
@Composable
fun PdfToolDoneScreen(
    title: String,
    subtitle: String,
    preview: Bitmap?,
    outputName: String,
    onOutputNameChange: (String) -> Unit,
    details: List<ToolDetailRow>,
    onClose: () -> Unit,
    onOpenPreview: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    stepText: String? = "Step 3 of 3 · Done",
    isSaving: Boolean = false,
    savedDestinationLabel: String? = null,
    footnote: String = "Your original file is unchanged",
    previewOverlay: @Composable BoxScope.() -> Unit = {},
    previewBadge: (@Composable BoxScope.() -> Unit)? = null,
    headerExtra: (@Composable ColumnScope.() -> Unit)? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        ToolTopBar(
            onBack = onClose,
            stepText = stepText,
            backIcon = Icons.Outlined.Close,
            backContentDescription = "Close",
            centerStep = true,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box {
                    ToolPaperPage(
                        bitmap = preview,
                        loading = preview == null,
                        elevation = 10.dp,
                        contentDescription = "Open the finished PDF",
                        modifier = Modifier
                            .width(128.dp)
                            .clickable(role = Role.Button, onClickLabel = "Open PDF", onClick = onOpenPreview),
                        overlay = previewOverlay,
                    )
                    previewBadge?.invoke(this)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, letterSpacing = (-0.6).sp),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                headerExtra?.invoke(this)
            }
            ToolFileNameField(value = outputName, onValueChange = onOutputNameChange)
            if (details.isNotEmpty()) {
                ToolDetailList(rows = details)
            }
            extraContent?.invoke(this)
        }
        ToolBottomBar {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolSecondaryButton(
                    text = "Save to phone",
                    icon = Icons.Outlined.FileDownload,
                    onClick = onSave,
                    loading = isSaving,
                    modifier = Modifier.weight(1f),
                )
                ToolPrimaryButton(
                    text = "Share",
                    icon = Icons.Outlined.Share,
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = savedDestinationLabel?.let { "Saved to $it" } ?: footnote,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = if (savedDestinationLabel != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
