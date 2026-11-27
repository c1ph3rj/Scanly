package `in`.c1ph3rj.scanly.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.BuildConfig
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.runCatchingCancellable
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AutomaticDocumentModelSelection(
    val liveModel: DocumentCornerModel,
    val postProcessingModel: DocumentCornerModel,
    val medianLatencyMillis: Map<DocumentCornerModel, Double>,
)

@Singleton
class AutomaticDocumentModelSelector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val detector: DocumentCornerDetector,
    private val dispatchers: ScanlyDispatchers,
) {
    private val calibrationMutex = Mutex()

    @Volatile
    private var cachedSelection: AutomaticDocumentModelSelection? = null

    suspend fun selection(): AutomaticDocumentModelSelection =
        cachedSelection ?: calibrationMutex.withLock {
            cachedSelection ?: loadOrCalibrate().also { cachedSelection = it }
        }

    private suspend fun loadOrCalibrate(): AutomaticDocumentModelSelection = withContext(dispatchers.io) {
        val preferences = context.getSharedPreferences("corner_model_calibration", Context.MODE_PRIVATE)
        val version = "${BuildConfig.VERSION_CODE}:${Build.FINGERPRINT}"
        if (preferences.getString("version", null) == version) {
            val saved = candidateModels.associateWith { model ->
                Double.fromBits(preferences.getLong(model.storageValue, 0L))
            }
            if (saved.values.all { it.isFinite() && it > 0 }) return@withContext selectionFrom(saved)
        }

        calibrate().also { selection ->
            val timings = selection.medianLatencyMillis
            // A transient failure or a cancelled calibration should not lock in a fallback.
            if (timings.size == candidateModels.size && timings.values.all { it.isFinite() && it > 0 }) {
                val editor = preferences.edit().putString("version", version)
                timings.forEach { (model, millis) -> editor.putLong(model.storageValue, millis.toBits()) }
                editor.apply()
            }
        }
    }

    private suspend fun calibrate(): AutomaticDocumentModelSelection = withContext(dispatchers.default) {
        val calibrationFrame = createCalibrationFrame()
        try {
            val timings = buildMap {
                candidateModels.forEach { model ->
                    benchmarkModel(calibrationFrame, model)?.let { medianMillis ->
                        put(model, medianMillis)
                    }
                }
            }
            selectionFrom(timings)
        } finally {
            calibrationFrame.recycle()
        }
    }

    private fun selectionFrom(timings: Map<DocumentCornerModel, Double>) = AutomaticDocumentModelSelection(
        liveModel = AutomaticDocumentModelSelectionPolicy.choose(timings, LIVE_CORNER_BUDGET_MILLIS),
        postProcessingModel = AutomaticDocumentModelSelectionPolicy.choose(timings, POST_PROCESSING_CORNER_BUDGET_MILLIS),
        medianLatencyMillis = timings,
    )

    private suspend fun benchmarkModel(
        bitmap: Bitmap,
        model: DocumentCornerModel,
    ): Double? = runCatchingCancellable {
        detector.detect(bitmap, model)
        List(MEASURED_RUNS) {
            val startedAt = System.nanoTime()
            detector.detect(bitmap, model)
            (System.nanoTime() - startedAt) / 1_000_000.0
        }.sorted()[MEASURED_RUNS / 2]
    }.getOrNull()

    private fun createCalibrationFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(58, 62, 66))
        val document = Path().apply {
            moveTo(130f, 55f)
            lineTo(535f, 82f)
            lineTo(570f, 425f)
            lineTo(92f, 405f)
            close()
        }
        canvas.drawPath(document, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 239, 229) })
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 110, 110)
            strokeWidth = 4f
        }
        repeat(9) { index ->
            val y = 120f + index * 27f
            canvas.drawLine(165f, y, 490f, y + 9f, linePaint)
        }
        return bitmap
    }

    private companion object {
        const val MEASURED_RUNS = 3
        const val LIVE_CORNER_BUDGET_MILLIS = 35.0
        const val POST_PROCESSING_CORNER_BUDGET_MILLIS = 120.0
        // Accurate (YOLO-pose) stays manual-only for compatibility; calibrate the
        // corner-regression ladder only.
        val candidateModels = listOf(
            DocumentCornerModel.LITE,
            DocumentCornerModel.STANDARD,
            DocumentCornerModel.HIGH,
        )
    }
}

internal object AutomaticDocumentModelSelectionPolicy {
    private val accuracyOrder = listOf(
        DocumentCornerModel.HIGH,
        DocumentCornerModel.STANDARD,
        DocumentCornerModel.LITE,
    )

    fun choose(
        medianLatencyMillis: Map<DocumentCornerModel, Double>,
        latencyBudgetMillis: Double,
    ): DocumentCornerModel {
        val measuredCandidates = medianLatencyMillis.filterKeys { it in accuracyOrder }
        return accuracyOrder.firstOrNull { model ->
            measuredCandidates[model]?.let { it <= latencyBudgetMillis } == true
        } ?: measuredCandidates.minByOrNull { it.value }?.key
        ?: DocumentCornerModel.LITE
    }
}
