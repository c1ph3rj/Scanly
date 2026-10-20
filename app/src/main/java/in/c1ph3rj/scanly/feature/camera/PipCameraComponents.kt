package `in`.c1ph3rj.scanly.feature.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.feature.pip.LocalPipPreferences
import `in`.c1ph3rj.scanly.feature.pip.Pip
import `in`.c1ph3rj.scanly.feature.pip.PipLines
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import `in`.c1ph3rj.scanly.feature.pip.PipSays
import kotlinx.coroutines.delay

/** What Pip shows and says in the camera guide for one detection state. */
internal data class PipCoach(
    val mood: PipMood,
    val line: String,
    /** How close auto-capture is, 0..1, or null when nothing is counting. */
    val progress: Float?,
    val warning: Boolean = false,
)

/**
 * Maps the live detection state to Pip's coaching. Pure, so every phase is unit tested. The
 * stability tracker's own status message is untouched and still drives the plain guide when
 * coaching is switched off.
 */
internal fun pipCoachFor(
    phase: AutoCapturePhase,
    sceneIssue: CaptureSceneIssue?,
    countdownValue: Int?,
    hasDocument: Boolean,
): PipCoach = when {
    sceneIssue == CaptureSceneIssue.LENS_BLOCKED ->
        PipCoach(PipMood.DIZZY, "everything's dark. is something over the lens?", null, warning = true)
    sceneIssue == CaptureSceneIssue.TOO_DARK ->
        PipCoach(PipMood.SLEEP, "bit dark in here. more light, or try the flash?", null, warning = true)
    sceneIssue == CaptureSceneIssue.BLURRY ->
        PipCoach(PipMood.DIZZY, "it's all fuzzy. tap the page to focus.", null, warning = true)
    phase == AutoCapturePhase.CAPTURING ->
        PipCoach(PipMood.HAPPY, "snap! saving that page…", 1f)
    phase == AutoCapturePhase.COOLDOWN ->
        PipCoach(PipMood.HAPPY, "got it! on to the next page.", null)
    phase == AutoCapturePhase.OFF && hasDocument ->
        PipCoach(PipMood.WOW, "found the edges. tap the shutter whenever.", null)
    phase == AutoCapturePhase.OFF ->
        PipCoach(PipMood.IDLE, "auto-capture is off. tap the shutter when ready.", null)
    phase == AutoCapturePhase.HOLD_STEADY ->
        PipCoach(PipMood.WOW, "ooh, edges! hold still a sec…", 0.2f)
    phase == AutoCapturePhase.COUNTDOWN -> PipCoach(
        mood = PipMood.WOW,
        line = if (countdownValue != null) "hold it… $countdownValue" else "hold it… almost there.",
        progress = when (countdownValue) {
            3 -> 0.45f
            2 -> 0.7f
            else -> 0.92f
        },
    )
    else -> PipCoach(PipMood.IDLE, "show me the whole page, all four corners.", null)
}

/**
 * The camera guide with Pip in it. Same place and size class as the plain guide it replaces, so
 * the preview, edge overlay, grid and countdown are exactly as before.
 */
@Composable
internal fun PipCameraCoach(
    liveDetection: LiveDetectionUiState,
    modifier: Modifier = Modifier,
) {
    val coach = pipCoachFor(
        phase = liveDetection.phase,
        sceneIssue = liveDetection.sceneIssue,
        countdownValue = liveDetection.countdownValue,
        hasDocument = liveDetection.hasOverlay,
    )
    // Pip talks for a moment whenever it has something new to say, then settles.
    var talking by remember { mutableStateOf(false) }
    LaunchedEffect(coach.line) {
        talking = true
        delay(900L)
        talking = false
    }
    val mood = if (talking && coach.mood == PipMood.IDLE) PipMood.TALK else coach.mood
    val progress by animateFloatAsState(
        targetValue = coach.progress ?: 0f,
        animationSpec = tween(320),
        label = "pip_coach_progress",
    )
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.widthIn(max = 420.dp),
        color = Color.Black.copy(alpha = 0.68f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (coach.warning) PipCoachWarning.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Pip(mood = mood, size = 32.dp)
            }
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = coach.line,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (coach.progress != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(accent),
                        )
                    }
                }
            }
        }
    }
}

private val PipCoachWarning = Color(0xFFFFB74D)

/**
 * Camera permission screen. Pip naps in an empty viewfinder until it may use the camera; when the
 * permission is blocked it explains the trip to Settings. Scrolls so it fits landscape phones.
 */
@Composable
internal fun CameraPermissionPrompt(
    isPermanentlyDenied: Boolean,
    onAllowCamera: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val pipEnabled = LocalPipPreferences.current.enabled
    val contentMaxWidth = if (windowSizeInfo.isTablet) 440.dp else 400.dp
    val muted = Color.White.copy(alpha = 0.72f)

    Column(
        modifier = modifier
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateUp,
                containerColor = Color.Transparent,
                contentColor = Color.White,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                EmptyViewfinder(modifier = Modifier.fillMaxWidth()) {
                    if (pipEnabled) {
                        PipSays(
                            line = if (isPermanentlyDenied) PipLines.CAMERA_BLOCKED else PipLines.CAMERA_PERMISSION,
                            mood = if (isPermanentlyDenied) PipMood.DIZZY else PipMood.SLEEP,
                            size = 112.dp,
                            bubbleMaxWidth = 260.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Camera access needed",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = if (isPermanentlyDenied) {
                            "Camera permission is blocked for Scanly. Open Settings, " +
                                "tap Permissions, and turn Camera on to scan documents."
                        } else {
                            "Scanly needs your camera to capture and straighten document pages."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = muted,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isPermanentlyDenied) {
                        PermissionPoint("Settings › Permissions › Camera › Allow")
                        PermissionPoint("Come back here and Pip will be ready")
                    } else {
                        PermissionPoint("Live preview with edge detection")
                        PermissionPoint("Camera active only while scanning")
                        PermissionPoint("Photos stay on your device until you export or share them")
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = onAllowCamera,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Text(
                            text = if (isPermanentlyDenied) "Open Settings" else "Allow camera access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Not now", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** A dashed, empty viewfinder: where the camera picture would be. */
@Composable
private fun EmptyViewfinder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dash = Color.White.copy(alpha = 0.22f)
    Box(
        modifier = modifier
            .heightIn(min = 280.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = dash,
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                )
            }
            .padding(vertical = 28.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PermissionPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = PermissionCheckTint,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The bright brand teal reads on black in both app themes. */
private val PermissionCheckTint = Color(0xFF2DD4BF)

/** Pip's version of the "document missing" card shown when the scan target is gone. */
@Composable
internal fun PipMissingDocumentNote(modifier: Modifier = Modifier) {
    if (!LocalPipPreferences.current.enabled) return
    PipSays(
        line = PipLines.DOCUMENT_MISSING,
        mood = PipMood.DIZZY,
        size = 80.dp,
        modifier = modifier,
    )
}
