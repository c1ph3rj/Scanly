package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.LiteRtProbeReport
import `in`.c1ph3rj.scanly.core.ml.SprintZeroReadinessReport
import `in`.c1ph3rj.scanly.domain.repository.SprintZeroDiagnosticsRepository
import javax.inject.Inject

class InspectLiteRtRuntimeUseCase @Inject constructor(
    private val repository: SprintZeroDiagnosticsRepository,
) {
    suspend operator fun invoke(
        readinessReport: SprintZeroReadinessReport,
    ): ScanlyResult<LiteRtProbeReport> = repository.inspectRuntime(readinessReport)
}
