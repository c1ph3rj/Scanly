package `in`.c1ph3rj.scanly.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerDetector
import `in`.c1ph3rj.scanly.core.ml.DocumentGateClass
import `in`.c1ph3rj.scanly.core.ml.DocumentGateDetector
import `in`.c1ph3rj.scanly.core.ml.DocumentGatePolicy
import `in`.c1ph3rj.scanly.core.ml.DocumentQuadPolicy
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BenchmarkMeasurement(
    val imageName: String,
    val model: DocumentCornerModel,
    val detected: Boolean,
    val confidence: Float,
    val preprocessingMs: Double,
    val inferenceMs: Double,
    val postprocessingMs: Double,
    val totalMs: Double,
    val gateClass: DocumentGateClass? = null,
    val gatePhysicalProbability: Float = 0f,
    val gateTotalMs: Double = 0.0,
    val gateAccepted: Boolean = false,
    val pipelineDetected: Boolean = false,
    val pipelineTotalMs: Double = 0.0,
    val error: String? = null,
)

data class ModelBenchmarkUiState(
    val running: Boolean = false,
    val completedImages: Int = 0,
    val totalImages: Int = 0,
    val measurements: List<BenchmarkMeasurement> = emptyList(),
)

@HiltViewModel
class ModelBenchmarkViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val detector: DocumentCornerDetector,
    private val gateDetector: DocumentGateDetector,
) : ViewModel() {
    private var warmedUp = false
    private val _uiState = MutableStateFlow(ModelBenchmarkUiState())
    val uiState = _uiState.asStateFlow()

    fun benchmark(uris: List<Uri>) {
        if (uris.isEmpty() || _uiState.value.running) return
        viewModelScope.launch {
            _uiState.value = ModelBenchmarkUiState(running = true, totalImages = uris.size)
            uris.forEachIndexed { imageIndex, uri ->
                val imageName = displayName(uri)
                val bitmap = runCatching { withContext(Dispatchers.IO) { decodeOriented(uri) } }.getOrNull()
                if (bitmap == null) {
                    _uiState.update { state ->
                        state.copy(
                            completedImages = imageIndex + 1,
                            measurements = state.measurements + DocumentCornerModel.entries.map { model ->
                                BenchmarkMeasurement(imageName, model, false, 0f, 0.0, 0.0, 0.0, 0.0, error = "Could not decode image")
                            },
                        )
                    }
                } else {
                    try {
                        if (!warmedUp) {
                            runCatching { gateDetector.classify(bitmap) }
                            DocumentCornerModel.entries.forEach { model -> runCatching { detector.detect(bitmap, model) } }
                            warmedUp = true
                        }
                        val gate = runCatching { gateDetector.classify(bitmap) }.getOrNull()
                        val gateAccepted = gate?.acceptsPhysicalDocument(DocumentGatePolicy.POST_PROCESSING_THRESHOLD) == true
                        DocumentCornerModel.entries.forEach { model ->
                            val measurement = runCatching {
                                val result = detector.detect(bitmap, model)
                                BenchmarkMeasurement(
                                    imageName = imageName,
                                    model = model,
                                    detected = result.quad != null,
                                    confidence = result.confidence,
                                    preprocessingMs = result.timing.preprocessingMillis,
                                    inferenceMs = result.timing.inferenceMillis,
                                    postprocessingMs = result.timing.postprocessingMillis,
                                    totalMs = result.timing.totalMillis,
                                    gateClass = gate?.predictedClass,
                                    gatePhysicalProbability = gate?.physicalDocumentProbability ?: 0f,
                                    gateTotalMs = gate?.timing?.totalMillis ?: 0.0,
                                    gateAccepted = gateAccepted,
                                    pipelineDetected = gateAccepted && result.quad?.let(DocumentQuadPolicy::isCaptureReady) == true,
                                    pipelineTotalMs = (gate?.timing?.totalMillis ?: 0.0) +
                                        if (gateAccepted) result.timing.totalMillis else 0.0,
                                )
                            }.getOrElse { error ->
                                BenchmarkMeasurement(imageName, model, false, 0f, 0.0, 0.0, 0.0, 0.0, error = error.message ?: "Model failed")
                            }
                            _uiState.update { it.copy(measurements = it.measurements + measurement) }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    _uiState.update { it.copy(completedImages = imageIndex + 1) }
                }
            }
            _uiState.update { it.copy(running = false) }
        }
    }

    private fun displayName(uri: Uri): String = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment.orEmpty().ifBlank { "Image" }

    private fun decodeOriented(uri: Uri): Bitmap {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Image unavailable")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Image decode failed")
        val rotation = ExifInterface(bytes.inputStream()).rotationDegrees
        if (rotation == 0) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            .also { source.recycle() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelBenchmarkRoute(
    onNavigateUp: () -> Unit,
    viewModel: ModelBenchmarkViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
        viewModel::benchmark,
    )
    val hasResults = state.measurements.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Model benchmark",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                if (hasResults || state.running) {
                    BenchmarkActionCard(
                        running = state.running,
                        completedImages = state.completedImages,
                        totalImages = state.totalImages,
                        onChooseImages = { picker.launch("image/*") },
                    )
                } else {
                    BenchmarkEmptyState(
                        onChooseImages = { picker.launch("image/*") },
                    )
                }
            }
            if (hasResults) {
                item(key = "summary") { BenchmarkSummary(state.measurements) }
                items(state.measurements, key = { "${it.imageName}-${it.model.storageValue}-${it.totalMs}" }) { measurement ->
                    MeasurementCard(measurement)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun BenchmarkEmptyState(
    onChooseImages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = "Compare detectors on your photos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Pick real documents, phone screens, and other rectangular objects. Scanly times the semantic gate and all four corner models, then reports the combined pipeline result.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onChooseImages,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    imageVector = Icons.Filled.ImageSearch,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Choose images and run")
            }
            Text(
                text = "Images stay on this device. Nothing is uploaded.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BenchmarkActionCard(
    running: Boolean,
    completedImages: Int,
    totalImages: Int,
    onChooseImages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Run another pass",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Select more photos to measure gate latency, corner detection, and full-pipeline timing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onChooseImages,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(if (running) "Benchmark running…" else "Choose images and run")
            }
            if (running) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "$completedImages / $totalImages images processed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkSummary(items: List<BenchmarkMeasurement>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Aggregate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val gateValues = items.distinctBy { it.imageName }.filter { it.gateClass != null }
            val gateTimes = gateValues.map { it.gateTotalMs }.sorted()
            SummaryMetricRow(
                label = "Document gate",
                value = "avg %.1f · P95 %.1f ms · accepted %d/%d".format(
                    gateTimes.average().takeIf { !it.isNaN() } ?: 0.0,
                    percentile(gateTimes, 0.95),
                    gateValues.count { it.gateAccepted },
                    gateValues.size,
                ),
            )
            DocumentCornerModel.entries.forEach { model ->
                val values = items.filter { it.model == model && it.error == null }
                val times = values.map { it.pipelineTotalMs }.sorted()
                val average = times.average().takeIf { !it.isNaN() } ?: 0.0
                SummaryMetricRow(
                    label = model.displayName,
                    value = "avg %.1f · P50 %.1f · P95 %.1f ms · detected %d/%d · failed %d".format(
                        average,
                        percentile(times, 0.50),
                        percentile(times, 0.95),
                        values.count { it.pipelineDetected },
                        values.size,
                        items.count { it.model == model && it.error != null },
                    ),
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun percentile(sortedValues: List<Double>, fraction: Double): Double {
    if (sortedValues.isEmpty()) return 0.0
    val index = kotlin.math.ceil((sortedValues.size - 1) * fraction).toInt().coerceIn(sortedValues.indices)
    return sortedValues[index]
}

@Composable
private fun MeasurementCard(item: BenchmarkMeasurement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${item.imageName} — ${item.model.displayName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (item.error != null) {
                Text(item.error, color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    text = "Gate: ${item.gateClass?.displayName ?: "Unavailable"} · physical %.1f%% · %.1f ms".format(
                        item.gatePhysicalProbability * 100,
                        item.gateTotalMs,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Corner: ${if (item.detected) "detected" else "not detected"} · confidence %.1f%%".format(
                        item.confidence * 100,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "pre %.1f · inference %.1f · post %.1f · total %.1f ms".format(
                        item.preprocessingMs,
                        item.inferenceMs,
                        item.postprocessingMs,
                        item.totalMs,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Pipeline: ${if (item.pipelineDetected) "accepted" else "rejected"} · %.1f ms".format(
                        item.pipelineTotalMs,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (item.pipelineDetected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
