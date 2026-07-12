package `in`.c1ph3rj.scanly.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerDetector
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
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
                                BenchmarkMeasurement(imageName, model, false, 0f, 0.0, 0.0, 0.0, 0.0, "Could not decode image")
                            },
                        )
                    }
                } else {
                    try {
                        if (!warmedUp) {
                            DocumentCornerModel.entries.forEach { model -> runCatching { detector.detect(bitmap, model) } }
                            warmedUp = true
                        }
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
                                )
                            }.getOrElse { error ->
                                BenchmarkMeasurement(imageName, model, false, 0f, 0.0, 0.0, 0.0, 0.0, error.message ?: "Model failed")
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents(), viewModel::benchmark)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model benchmark") },
                navigationIcon = { IconButton(onClick = onNavigateUp) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select several real document and non-document images. Every image is tested with Legacy, Lite, Standard, and Accurate.")
                    Button(onClick = { picker.launch("image/*") }, enabled = !state.running) { Text("Choose images and run") }
                    if (state.running) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("${state.completedImages}/${state.totalImages} images")
                    }
                }
            }
            if (state.measurements.isNotEmpty()) {
                item { BenchmarkSummary(state.measurements) }
                items(state.measurements) { measurement -> MeasurementCard(measurement) }
            }
        }
    }
}

@Composable
private fun BenchmarkSummary(items: List<BenchmarkMeasurement>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Aggregate", style = MaterialTheme.typography.titleMedium)
            DocumentCornerModel.entries.forEach { model ->
                val values = items.filter { it.model == model && it.error == null }
                val times = values.map { it.totalMs }.sorted()
                val average = times.average().takeIf { !it.isNaN() } ?: 0.0
                Text(
                    "${model.displayName}: avg %.1f • P50 %.1f • P95 %.1f ms • detected %d/%d • failed %d".format(
                        average,
                        percentile(times, 0.50),
                        percentile(times, 0.95),
                        values.count { it.detected },
                        values.size,
                        items.count { it.model == model && it.error != null },
                    ),
                )
            }
        }
    }
}

private fun percentile(sortedValues: List<Double>, fraction: Double): Double {
    if (sortedValues.isEmpty()) return 0.0
    val index = kotlin.math.ceil((sortedValues.size - 1) * fraction).toInt().coerceIn(sortedValues.indices)
    return sortedValues[index]
}

@Composable
private fun MeasurementCard(item: BenchmarkMeasurement) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${item.imageName} — ${item.model.displayName}", style = MaterialTheme.typography.titleSmall)
            if (item.error != null) Text(item.error, color = MaterialTheme.colorScheme.error) else {
                Text("${if (item.detected) "Detected" else "Not detected"} • confidence %.1f%%".format(item.confidence * 100))
                Text("pre %.1f • inference %.1f • post %.1f • total %.1f ms".format(item.preprocessingMs, item.inferenceMs, item.postprocessingMs, item.totalMs))
            }
        }
    }
}
