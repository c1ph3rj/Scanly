package `in`.c1ph3rj.scanly.feature.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.LiteRtProbeUiState
import `in`.c1ph3rj.scanly.core.ml.SprintZeroReadinessReport
import `in`.c1ph3rj.scanly.domain.usecase.InspectLiteRtRuntimeUseCase
import `in`.c1ph3rj.scanly.domain.usecase.InspectSprintZeroReadinessUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReadinessUiState(
    val readinessReport: SprintZeroReadinessReport = SprintZeroReadinessReport.preview(),
    val runtimeProbeState: LiteRtProbeUiState = LiteRtProbeUiState.Loading,
)

@HiltViewModel
class ReadinessViewModel @Inject constructor(
    private val inspectReadinessUseCase: InspectSprintZeroReadinessUseCase,
    private val inspectLiteRtRuntimeUseCase: InspectLiteRtRuntimeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadinessUiState())
    val uiState: StateFlow<ReadinessUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val readinessResult = inspectReadinessUseCase()) {
                is ScanlyResult.Success -> {
                    val readinessReport = readinessResult.value
                    _uiState.value = ReadinessUiState(
                        readinessReport = readinessReport,
                        runtimeProbeState = LiteRtProbeUiState.Loading,
                    )
                    _uiState.value = _uiState.value.copy(
                        runtimeProbeState = when (val runtimeResult = inspectLiteRtRuntimeUseCase(readinessReport)) {
                            is ScanlyResult.Success -> LiteRtProbeUiState.Success(runtimeResult.value)
                            is ScanlyResult.Failure -> LiteRtProbeUiState.Failure(runtimeResult.error.message)
                        },
                    )
                }

                is ScanlyResult.Failure -> {
                    _uiState.value = ReadinessUiState(
                        runtimeProbeState = LiteRtProbeUiState.Failure(readinessResult.error.message),
                    )
                }
            }
        }
    }
}
