package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.SprintZeroReadinessReport
import `in`.c1ph3rj.scanly.domain.repository.SprintZeroDiagnosticsRepository
import javax.inject.Inject

class InspectSprintZeroReadinessUseCase @Inject constructor(
    private val repository: SprintZeroDiagnosticsRepository,
) {
    suspend operator fun invoke(): ScanlyResult<SprintZeroReadinessReport> =
        repository.inspectReadiness()
}
