package `in`.c1ph3rj.scanly.feature.readiness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.core.ml.ArtifactReadiness
import `in`.c1ph3rj.scanly.core.ml.LiteRtProbeReport
import `in`.c1ph3rj.scanly.core.ml.LiteRtProbeUiState
import `in`.c1ph3rj.scanly.core.ml.ScanlyModelAssets
import `in`.c1ph3rj.scanly.core.ml.SprintZeroReadinessReport
import `in`.c1ph3rj.scanly.core.ml.toReadableFileSize

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SprintZeroReadinessScreen(
    readinessReport: SprintZeroReadinessReport = SprintZeroReadinessReport.preview(),
    runtimeProbeState: LiteRtProbeUiState = LiteRtProbeUiState.Loading,
    onNavigateUp: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Sprint 0 Readiness")
                },
                navigationIcon = {
                    if (onNavigateUp != null) {
                        TextButton(onClick = onNavigateUp) {
                            Text(text = "Back")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Sprint 0 Readiness",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item {
                Text(
                    text = "This milestone validates the mobile model path before we build scanner flows on top of it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                StatusCard(
                    title = "Overall status",
                    value = readinessReport.readiness.label(),
                    supportingText = readinessReport.runtimeSummary,
                )
            }
            item {
                ArtifactCard(
                    title = "Model asset",
                    value = if (readinessReport.modelPresent) "Ready" else "Missing",
                    supportingText = buildString {
                        append(ScanlyModelAssets.modelAssetPath)
                        readinessReport.modelSizeBytes?.let { size ->
                            append(" - ")
                            append(size.toReadableFileSize())
                        }
                    },
                )
            }
            item {
                ArtifactCard(
                    title = "Validation images",
                    value = readinessReport.validationImageCount.toString(),
                    supportingText = ScanlyModelAssets.validationImagesDirectory,
                )
            }
            item {
                ArtifactCard(
                    title = "Expected corners",
                    value = readinessReport.expectedCornersCount.toString(),
                    supportingText = "${ScanlyModelAssets.expectedCornersDirectory} (*${ScanlyModelAssets.expectedCornersExtension})",
                )
            }
            item {
                RuntimeProbeCard(runtimeProbeState = runtimeProbeState)
            }
            item {
                SectionCard(
                    title = "What this sprint locks down",
                    lines = listOf(
                        "Runtime family and fallback path",
                        "Corner ordering contract: TL -> TR -> BR -> BL",
                        "Validation dataset location and labeling format",
                        "Performance budget for preview and capture inference",
                    ),
                )
            }
            item {
                SectionCard(
                    title = "Your next actions",
                    lines = readinessReport.userActions,
                )
            }
        }
    }
}

@Composable
private fun RuntimeProbeCard(
    runtimeProbeState: LiteRtProbeUiState,
) {
    when (runtimeProbeState) {
        LiteRtProbeUiState.Loading -> {
            SectionCard(
                title = "Runtime probe",
                lines = listOf(
                    "Loading the LiteRT model on this device.",
                    "If this stays here for too long, check whether the app build includes the .tflite asset.",
                ),
            )
        }

        is LiteRtProbeUiState.Failure -> {
            SectionCard(
                title = "Runtime probe failed",
                lines = listOf(
                    runtimeProbeState.message,
                    "Next step: confirm the model exported cleanly and rerun the app on a physical device.",
                ),
            )
        }

        is LiteRtProbeUiState.Success -> {
            RuntimeSuccessCard(report = runtimeProbeState.report)
        }
    }
}

@Composable
private fun RuntimeSuccessCard(
    report: LiteRtProbeReport,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "Runtime probe", style = MaterialTheme.typography.titleMedium)
            Text(
                text = report.deviceName,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = report.androidVersion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Runtime ${report.runtimeVersion} | Schema ${report.schemaVersion}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Smoke inference: ${"%.1f".format(report.smokeInferenceMillis)} ms",
                style = MaterialTheme.typography.bodyLarge,
            )
            SectionText(title = "Input tensors", lines = report.inputTensors.map { it.asDisplayLine() })
            SectionText(title = "Output tensors", lines = report.outputTensors.map { it.asDisplayLine() })
            report.validationReport?.let { validation ->
                SectionText(
                    title = "Validation summary",
                    lines = buildList {
                        add("Samples evaluated: ${validation.samplesEvaluated}")
                        add("Detections found: ${validation.detectionsFound}")
                        add("Average confidence: ${"%.3f".format(validation.averageConfidence)}")
                        add("Mean corner error: ${"%.4f".format(validation.meanCornerErrorNormalized)} normalized")
                        add("Mean corner error: ${"%.2f".format(validation.meanCornerErrorPixels)} px")
                        validation.bestSample?.let { best ->
                            add("Best sample: ${best.imageName} (${ "%.2f".format(best.meanCornerErrorPixels)} px)")
                        }
                        validation.worstSample?.let { worst ->
                            add("Worst sample: ${worst.imageName} (${ "%.2f".format(worst.meanCornerErrorPixels)} px)")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    supportingText: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtifactCard(
    title: String,
    value: String,
    supportingText: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    lines: List<String>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { line ->
                Text(
                    text = "- $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionText(
    title: String,
    lines: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        lines.forEach { line ->
            Text(
                text = "- $line",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ArtifactReadiness.label(): String = when (this) {
    ArtifactReadiness.READY -> "Ready to benchmark"
    ArtifactReadiness.PARTIAL -> "Waiting on a few artifacts"
    ArtifactReadiness.MISSING -> "Artifacts not added yet"
}
