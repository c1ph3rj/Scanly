package `in`.c1ph3rj.scanly.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionSupport
import `in`.c1ph3rj.scanly.R
import androidx.compose.ui.res.stringResource

/** Three introduction pages; camera access is contextual and never another pager destination. */
@Composable
fun OnboardingFlow(
    uiState: OnboardingUiState,
    onComplete: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onStartScan: () -> Unit = onComplete,
) {
    val context = LocalContext.current
    var showPermission by rememberSaveable { mutableStateOf(false) }
    val finish = {
        showPermission = false
        onComplete()
    }

    OnboardingScreen(
        uiState = uiState,
        onComplete = {
            if (CameraPermissionSupport.isGranted(context)) onStartScan() else showPermission = true
        },
        onDismissError = onDismissError,
        modifier = modifier,
        interactionEnabled = !showPermission,
    )

    if (showPermission) {
        OnboardingCameraPermissionSheet(
            onDismiss = { showPermission = false },
            onDone = finish,
            onCameraReady = { showPermission = false; onStartScan() },
        )
    }
}

@Composable
private fun OnboardingCameraPermissionSheet(
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    onCameraReady: () -> Unit,
) {
    val context = LocalContext.current
    var openedSettings by rememberSaveable { mutableStateOf(false) }
    var requestInFlight by rememberSaveable { mutableStateOf(false) }
    var blocked by remember {
        mutableStateOf(
            CameraPermissionSupport.shouldOpenSettings(
                CameraPermissionSupport.resolveStatus(CameraPermissionSupport.findActivity(context), context),
            ),
        )
    }
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnCameraReady by rememberUpdatedState(onCameraReady)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        CameraPermissionSupport.markRequested(context)
        requestInFlight = false
        // A denial is a valid choice. Camera and QR screens offer another chance when needed.
        if (granted) currentOnCameraReady() else currentOnDone()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (openedSettings && CameraPermissionSupport.isGranted(context)) {
                    currentOnCameraReady()
                } else {
                    openedSettings = false
                    blocked = CameraPermissionSupport.shouldOpenSettings(
                        CameraPermissionSupport.resolveStatus(CameraPermissionSupport.findActivity(context), context),
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OnboardingPermissionSheet(
        blocked = blocked,
        enabled = !requestInFlight,
        onDismiss = onDismiss,
        onLater = onDone,
        onAllow = {
            if (CameraPermissionSupport.isGranted(context)) {
                onCameraReady()
            } else if (blocked) {
                openedSettings = true
                CameraPermissionSupport.openAppSettings(context)
            } else {
                requestInFlight = true
                launcher.launch(Manifest.permission.CAMERA)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingPermissionSheet(
    blocked: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onLater: () -> Unit,
    onAllow: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (enabled) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 560.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("onboarding_permission_sheet"),
    ) {
        // The whole sheet scrolls on landscape / large text, including its reachable actions.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Text(
                if (blocked) stringResource(R.string.onboarding_flow_camera_access_is_turned_off) else stringResource(R.string.onboarding_flow_ready_for_your_first_scan),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Text(
                if (blocked) {
                    stringResource(R.string.onboarding_flow_open_settings_tap_permissions_and)
                } else {
                    stringResource(R.string.onboarding_flow_enable_the_camera_to_scan)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onAllow,
                enabled = enabled,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("onboarding_allow_camera"),
            ) {
                Text(
                    if (blocked) stringResource(R.string.pip_camera_open_settings) else stringResource(R.string.onboarding_flow_enable_camera),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(
                onClick = onLater,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("onboarding_camera_later"),
            ) {
                Text(stringResource(R.string.pip_camera_not_now), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
