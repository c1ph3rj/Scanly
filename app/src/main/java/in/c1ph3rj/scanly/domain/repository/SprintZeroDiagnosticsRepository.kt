package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.LiteRtProbeReport
import `in`.c1ph3rj.scanly.core.ml.SprintZeroReadinessReport

interface SprintZeroDiagnosticsRepository {
    suspend fun inspectReadiness(): ScanlyResult<SprintZeroReadinessReport>

    suspend fun inspectRuntime(
        readinessReport: SprintZeroReadinessReport,
    ): ScanlyResult<LiteRtProbeReport>
}
