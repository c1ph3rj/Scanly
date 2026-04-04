package `in`.c1ph3rj.scanly.data.diagnostics

import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.LiteRtModelInspector
import `in`.c1ph3rj.scanly.core.ml.LiteRtProbeReport
import `in`.c1ph3rj.scanly.core.ml.SprintZeroReadinessReport
import `in`.c1ph3rj.scanly.domain.repository.SprintZeroDiagnosticsRepository
import `in`.c1ph3rj.scanly.feature.readiness.SprintZeroArtifactInspector
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSprintZeroDiagnosticsRepository @Inject constructor(
    private val artifactInspector: SprintZeroArtifactInspector,
    private val modelInspector: LiteRtModelInspector,
    private val dispatchers: ScanlyDispatchers,
) : SprintZeroDiagnosticsRepository {

    override suspend fun inspectReadiness(): ScanlyResult<SprintZeroReadinessReport> = runCatching {
        withContext(dispatchers.io) {
            artifactInspector.inspect()
        }
    }.fold(
        onSuccess = { report -> ScanlyResult.Success(report) },
        onFailure = { throwable ->
            ScanlyResult.Failure(
                ScanlyError(
                    message = throwable.message ?: "Could not inspect Sprint 0 artifacts.",
                    cause = throwable,
                ),
            )
        },
    )

    override suspend fun inspectRuntime(
        readinessReport: SprintZeroReadinessReport,
    ): ScanlyResult<LiteRtProbeReport> = runCatching {
        modelInspector.inspect(readinessReport)
    }.fold(
        onSuccess = { report -> ScanlyResult.Success(report) },
        onFailure = { throwable ->
            ScanlyResult.Failure(
                ScanlyError(
                    message = throwable.message ?: "Could not complete the LiteRT runtime probe.",
                    cause = throwable,
                ),
            )
        },
    )
}
