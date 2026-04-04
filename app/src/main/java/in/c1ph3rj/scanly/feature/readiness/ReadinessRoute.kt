package `in`.c1ph3rj.scanly.feature.readiness

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun ReadinessRoute(
    onNavigateUp: () -> Unit,
    viewModel: ReadinessViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SprintZeroReadinessScreen(
        readinessReport = uiState.readinessReport,
        runtimeProbeState = uiState.runtimeProbeState,
        onNavigateUp = onNavigateUp,
    )
}
