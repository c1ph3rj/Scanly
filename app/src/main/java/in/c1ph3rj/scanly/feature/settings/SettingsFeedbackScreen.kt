package `in`.c1ph3rj.scanly.feature.settings

import android.content.ClipData
import android.graphics.BitmapFactory
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.BuildConfig
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachment
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachmentLimits
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionSupport
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FeedbackKind(val label: Int, val emailLabel: String) {
    SUGGESTION(R.string.feedback_suggestion, "Suggestion"),
    ISSUE(R.string.feedback_issue, "Issue"),
    OTHER(R.string.feedback_other, "Other"),
}

@Composable
fun SettingsFeedbackRoute(onNavigateUp: () -> Unit, viewModel: FeedbackViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val attachments by viewModel.uiState.collectAsStateWithLifecycle()
    val handoff by viewModel.handoffState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentContext by rememberUpdatedState(context)
    val navigateUp by rememberUpdatedState(onNavigateUp)
    val noEmail = stringResource(R.string.feedback_no_email)
    val copied = stringResource(R.string.feedback_copied)
    val copyImagesNote = stringResource(R.string.feedback_copy_images_note)
    val chooserTitle = stringResource(R.string.feedback_send)
    val emailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Email apps do not report whether a message was sent. Ask the user after return instead.
        viewModel.onEmailReturned()
    }
    val remainingSlots = (FeedbackAttachmentLimits.MAX_IMAGES - attachments.images.size).coerceAtLeast(0)
    // Photo picker enforces the remaining count before confirmation; retain the ViewModel cap for
    // older devices whose document-picker fallback ignores the selection limit.
    val multipleImagesContract = remember(remainingSlots) {
        ActivityResultContracts.PickMultipleVisualMedia(remainingSlots.coerceAtLeast(2))
    }
    val multipleImagesPicker = rememberLauncherForActivityResult(multipleImagesContract) { uris ->
        viewModel.addImages(uris.map { it.toString() })
    }
    val singleImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addImages(listOf(it.toString())) }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onHostStopped(
                    CameraPermissionSupport.findActivity(currentContext)?.isChangingConfigurations == true,
                )
                Lifecycle.Event.ON_RESUME -> viewModel.onHostResumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }
    key(handoff.draftEpoch) {
        SettingsFeedbackScreen(
            onNavigateUp = onNavigateUp,
            snackbarHostState = snackbar,
            attachmentState = attachments,
            onAddImages = {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                when {
                    remainingSlots == 1 -> singleImagePicker.launch(request)
                    remainingSlots > 1 -> multipleImagesPicker.launch(request)
                }
            },
            onRemoveImage = viewModel::removeImage,
            onSend = { subject, body ->
                scope.launch {
                    val images = viewModel.imagesForEmail() ?: return@launch
                    if (openFeedbackEmail(context, subject, body, images, chooserTitle,
                            launch = { emailLauncher.launch(it) })) {
                        viewModel.onEmailLaunched()
                    } else {
                        snackbar.showSnackbar(noEmail)
                    }
                }
            },
            onCopy = { subject, body ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(
                        subject, "$SUPPORT_EMAIL\n\n$subject\n\n$body",
                    )))
                    snackbar.showSnackbar(if (attachments.images.isEmpty()) copied else copyImagesNote)
                }
            },
        )
    }
    // The new keyed screen composes with empty rememberSaveable fields before navigation runs.
    LaunchedEffect(handoff.closeRequested, handoff.draftEpoch) {
        if (handoff.closeRequested) {
            viewModel.consumeCloseRequest()
            navigateUp()
        }
    }
    if (handoff.showConfirmation) {
        AlertDialog(
            modifier = Modifier.testTag("feedback_sent_dialog"),
            onDismissRequest = viewModel::keepDraft,
            title = { Text(stringResource(R.string.feedback_sent_title)) },
            text = { Text(stringResource(R.string.feedback_sent_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSent, modifier = Modifier.testTag("feedback_sent_yes")) {
                    Text(stringResource(R.string.feedback_sent_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::keepDraft, modifier = Modifier.testTag("feedback_sent_not_yet")) {
                    Text(stringResource(R.string.feedback_sent_not_yet))
                }
            },
        )
    }
}

@Composable
fun SettingsFeedbackScreen(
    onNavigateUp: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onSend: (String, String) -> Unit,
    onCopy: (String, String) -> Unit,
    attachmentState: FeedbackAttachmentState = FeedbackAttachmentState(),
    onAddImages: () -> Unit = {},
    onRemoveImage: (String) -> Unit = {},
) {
    var kindName by rememberSaveable { mutableStateOf(FeedbackKind.SUGGESTION.name) }
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var previewPath by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = FeedbackKind.valueOf(kindName)
    val window = rememberWindowSizeInfo()
    val context = LocalContext.current
    val emailSubject = "Scanly ${kind.emailLabel}" + subject.trim().takeIf { it.isNotEmpty() }
        ?.let { ": $it" }.orEmpty()
    val emailBody = message.trim() + "\n\n" + stringResource(
        R.string.feedback_app_details, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.RELEASE,
    )
    val canSend = message.isNotBlank() && !attachmentState.isPreparing &&
        FeedbackAttachmentLimits.canSend(attachmentState.images)
    val canAdd = !attachmentState.isPreparing && attachmentState.images.size < FeedbackAttachmentLimits.MAX_IMAGES

    val sendAction: @Composable (Modifier) -> Unit = { modifier ->
        Button(
            onClick = { onSend(emailSubject, emailBody) },
            enabled = canSend,
            modifier = modifier.heightIn(min = 52.dp).testTag("feedback_send"),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.feedback_send))
        }
    }
    val copyAction: @Composable (Modifier) -> Unit = { modifier ->
        TextButton(
            onClick = { onCopy(emailSubject, emailBody) },
            enabled = message.isNotBlank() && !attachmentState.isPreparing,
            modifier = modifier.testTag("feedback_copy"),
        ) { Text(stringResource(R.string.feedback_copy)) }
    }

    ScanlyDetailScaffold(
        title = stringResource(R.string.feedback_title),
        onNavigateUp = onNavigateUp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
                    val actionsModifier = Modifier
                        .widthIn(max = minOf(window.contentMaxWidth, 720.dp))
                        .fillMaxWidth()
                        .padding(horizontal = window.horizontalPadding, vertical = 8.dp)
                    if (window.isHeightConstrained) {
                        Row(
                            modifier = actionsModifier,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            sendAction(Modifier.weight(1f))
                            copyAction(Modifier.widthIn(min = 88.dp, max = 180.dp))
                        }
                    } else {
                        Column(
                            modifier = actionsModifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            sendAction(Modifier.fillMaxWidth())
                            copyAction(Modifier)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = window.horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Outlined.Email, null, Modifier.padding(14.dp).size(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.feedback_description), style = MaterialTheme.typography.bodyMedium)
                    Text(SUPPORT_EMAIL, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            FeedbackSection {
                Text(stringResource(R.string.feedback_type), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackKind.entries.forEach { option ->
                        FilterChip(selected = kind == option, onClick = { kindName = option.name },
                            label = { Text(stringResource(option.label)) })
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it.replace('\n', ' ').replace('\r', ' ').take(120) },
                    modifier = Modifier.fillMaxWidth().testTag("feedback_subject"),
                    label = { Text(stringResource(R.string.feedback_subject)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(5000) },
                    modifier = Modifier.fillMaxWidth().testTag("feedback_message"),
                    label = { Text(stringResource(R.string.feedback_message)) },
                    supportingText = { Text(stringResource(R.string.feedback_character_count, message.length, 5000)) },
                    minLines = 4,
                    maxLines = 10,
                    shape = RoundedCornerShape(14.dp),
                )
            }
            FeedbackSection(Modifier.testTag("feedback_attachments")) {
                Text(stringResource(R.string.feedback_attachments_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.feedback_attachment_limits, FeedbackAttachmentLimits.MAX_IMAGES,
                        Formatter.formatShortFileSize(context, FeedbackAttachmentLimits.MAX_IMAGE_BYTES),
                        Formatter.formatShortFileSize(context, FeedbackAttachmentLimits.MAX_TOTAL_BYTES)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (attachmentState.images.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(attachmentState.images, key = { _, image -> image.path }) { index, image ->
                            AttachmentTile(image, index + 1, !attachmentState.isPreparing,
                                onPreview = { previewPath = image.path }, onRemove = { onRemoveImage(image.path) })
                        }
                    }
                    Text(stringResource(R.string.feedback_attachment_summary, attachmentState.images.size,
                        FeedbackAttachmentLimits.MAX_IMAGES,
                        Formatter.formatShortFileSize(context, attachmentState.images.sumOf { it.sizeBytes })),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (attachmentState.isPreparing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.feedback_preparing_images), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onAddImages, enabled = canAdd,
                    modifier = Modifier.fillMaxWidth().testTag("feedback_add_images"), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.feedback_add_images))
                }
            }
            Text(stringResource(R.string.feedback_email_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val preview = attachmentState.images.firstOrNull { it.path == previewPath }
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { previewPath = null },
            title = { Text(stringResource(R.string.feedback_image_label, attachmentState.images.indexOf(preview) + 1)) },
            text = { FeedbackImage(preview.path, Modifier.fillMaxWidth().heightIn(max = 460.dp), 1024) },
            confirmButton = {
                TextButton(onClick = { previewPath = null }) { Text(stringResource(R.string.feedback_close_preview)) }
            },
        )
    }
}

@Composable
private fun FeedbackSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun AttachmentTile(image: FeedbackAttachment, number: Int, enabled: Boolean, onPreview: () -> Unit, onRemove: () -> Unit) {
    val label = stringResource(R.string.feedback_image_label, number)
    Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            FeedbackImage(image.path, Modifier.size(112.dp).clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = label, onClick = onPreview)
                .semantics { contentDescription = label })
            Surface(Modifier.align(Alignment.TopEnd).padding(4.dp), shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(48.dp).testTag("feedback_remove_$number")) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.feedback_remove_image, number), Modifier.size(20.dp))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(Formatter.formatShortFileSize(LocalContext.current, image.sizeBytes),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeedbackImage(path: String, modifier: Modifier, edge: Int = 256) {
    val image by produceState<ImageBitmap?>(null, path, edge) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height),
            contentScale = ContentScale.Fit) }
    }
}
