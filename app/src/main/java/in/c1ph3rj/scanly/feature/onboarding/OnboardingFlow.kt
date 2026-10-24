package `in`.c1ph3rj.scanly.feature.onboarding

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionSupport
import `in`.c1ph3rj.scanly.feature.pip.PipLines
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import `in`.c1ph3rj.scanly.feature.pip.PipSays

private enum class OnboardingStep { INTRO, CAMERA }

/**
 * First run: the intro, then — only if the camera is not allowed yet — a short camera step, so a
 * fresh install (or cleared app data) asks once, with context, before the first scan. Either way
 * the user can go on without it; the scan screen asks again when it is actually needed.
 */
@Composable
fun OnboardingFlow(
    uiState: OnboardingUiState,
    onComplete: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(OnboardingStep.INTRO) }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState == OnboardingStep.CAMERA
            (fadeIn(tween(260)) + slideInHorizontally(tween(320)) { if (forward) it / 6 else -it / 6 }) togetherWith
                (fadeOut(tween(180)) + slideOutHorizontally(tween(320)) { if (forward) -it / 6 else it / 6 })
        },
        modifier = modifier,
        label = "onboarding_step",
    ) { current ->
        when (current) {
            OnboardingStep.INTRO -> OnboardingScreen(
                uiState = uiState,
                onComplete = {
                    if (CameraPermissionSupport.isGranted(context)) onComplete() else step = OnboardingStep.CAMERA
                },
                onDismissError = onDismissError,
            )
            OnboardingStep.CAMERA -> OnboardingCameraStep(
                isCompleting = uiState.isCompleting,
                onBack = { step = OnboardingStep.INTRO },
                onDone = onComplete,
            )
        }
    }
}

@Composable
private fun OnboardingCameraStep(
    isCompleting: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { CameraPermissionSupport.findActivity(context) }
    val blocked = remember(context) {
        CameraPermissionSupport.shouldOpenSettings(CameraPermissionSupport.resolveStatus(activity, context))
    }
    val currentOnDone by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Granted or not, the choice is made; the scan screen handles a later "no".
        CameraPermissionSupport.markRequested(context)
        currentOnDone()
    }
    // Back from Android settings with the camera now allowed: carry on.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && blocked && CameraPermissionSupport.isGranted(context)) {
                currentOnDone()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = onBack)

    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colorScheme.background, colorScheme.primaryContainer.copy(alpha = 0.22f), colorScheme.background),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("onboarding_camera_step"),
        contentAlignment = Alignment.TopCenter,
    ) {
        val window = rememberWindowSizeInfo()
        // Wide landscape: Pip on one side, the ask and its buttons on the other, instead of a
        // narrow column with empty space either side (and a scroll on a phone held sideways).
        val sideBySide = window.isLandscape && window.widthClass != WindowWidthClass.Compact
        val topRow: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Step 2 of 2",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        val pip: @Composable (Dp) -> Unit = { size ->
            PipSays(
                line = if (blocked) PipLines.CAMERA_BLOCKED else PipLines.ONBOARDING_CAMERA,
                mood = if (blocked) PipMood.DIZZY else PipMood.WOW,
                size = size,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val copy: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Let Pip use the camera",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                )
                Text(
                    text = if (blocked) {
                        "The camera is turned off for Scanly. Open Settings, tap Permissions, and allow Camera."
                    } else {
                        "Scanly needs the camera to capture and straighten pages. You can change this anytime."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CameraStepPoint(Icons.Outlined.CameraAlt, "Only on while you're scanning")
                CameraStepPoint(Icons.Outlined.Lock, "Photos stay on this phone until you share them")
                CameraStepPoint(Icons.Outlined.PhotoLibrary, "Importing from your gallery works without it")
            }
        }
        val actions: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = {
                        if (blocked) {
                            CameraPermissionSupport.openAppSettings(context)
                        } else {
                            launcher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    enabled = !isCompleting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("onboarding_allow_camera"),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = colorScheme.primary,
                        disabledContentColor = colorScheme.onPrimary,
                    ),
                ) {
                    if (isCompleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = if (blocked) "Open Settings" else "Allow camera",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                TextButton(
                    onClick = onDone,
                    enabled = !isCompleting,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("onboarding_camera_later"),
                ) {
                    Text("Maybe later", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (sideBySide) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1000.dp)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                topRow()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        pip(if (window.isHeightConstrained) 88.dp else 140.dp)
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(if (window.isHeightConstrained) 12.dp else 20.dp),
                        ) {
                            copy()
                        }
                        Spacer(Modifier.height(16.dp))
                        actions()
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            ) {
                topRow()
                // Centred in the space above the buttons, so a tall tablet has no empty band.
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val viewportHeight = maxHeight
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = viewportHeight),
                        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        pip(if (viewportHeight >= 800.dp) 140.dp else 112.dp)
                        copy()
                        Spacer(Modifier.height(8.dp))
                    }
                }
                actions()
            }
        }
    }
}

@Composable
private fun CameraStepPoint(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}
