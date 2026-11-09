package `in`.c1ph3rj.scanly.feature.update

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import `in`.c1ph3rj.scanly.R
import androidx.compose.ui.res.stringResource
import `in`.c1ph3rj.scanly.core.common.appString

@Composable
fun FlexibleUpdateSnackbarHost(
    hostState: SnackbarHostState,
    visible: Boolean,
    promptToken: Long,
    onRestartNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(visible, promptToken) {
        if (!visible) {
            hostState.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }

        val result = hostState.showSnackbar(
            message = appString(R.string.flexible_update_snackbar_update_downloaded_restart_scanly_to),
            actionLabel = appString(R.string.flexible_update_snackbar_restart),
        )
        if (result == SnackbarResult.ActionPerformed) {
            onRestartNow()
        }
    }

    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            Snackbar(
                action = {
                    data.visuals.actionLabel?.let { actionLabel ->
                        TextButton(onClick = { data.performAction() }) {
                            Text(text = actionLabel)
                        }
                    }
                },
            ) {
                Text(text = data.visuals.message)
            }
        },
    )
}