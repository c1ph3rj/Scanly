package `in`.c1ph3rj.scanly.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.TensorFlowLite
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtDocumentCornerDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) : DocumentCornerDetector {
    private val runtimes = mutableMapOf<DocumentCornerModel, Runtime>()

    override suspend fun detect(
        frame: DetectionFrame,
        model: DocumentCornerModel,
    ): CornerDetectionResult = withContext(dispatchers.default) {
        val oriented = frame.toOrientedBitmap()
        try {
            detectBitmap(oriented, getOrCreateRuntime(model))
        } finally {
            oriented.recycle()
        }
    }

    override suspend fun detect(
        bitmap: Bitmap,
        model: DocumentCornerModel,
    ): CornerDetectionResult = withContext(dispatchers.default) {
        detectBitmap(bitmap, getOrCreateRuntime(model))
    }

    private fun getOrCreateRuntime(model: DocumentCornerModel): Runtime = synchronized(runtimes) {
        runtimes[model] ?: createRuntime(model).also { runtimes[model] = it }
    }

    private fun createRuntime(model: DocumentCornerModel): Runtime {
        TensorFlowLite.init()
        val modelContract = ScanlyModelAssets.contract(model)
        val interpreter = InterpreterApi.create(
            loadModelBuffer(modelContract.assetPath),
            InterpreterApi.Options().apply { setNumThreads(LiteRtPoseConstants.DEFAULT_THREAD_COUNT) },
        )
        interpreter.allocateTensors()
        val inputShape = interpreter.getInputTensor(0).shape()
        val legacyPredictionCount = if (modelContract.outputKind == ScanlyModelContract.OutputKind.LEGACY_POSE) {
            interpreter.getOutputTensor(0).shape().getOrElse(2) { error("Legacy output shape is invalid.") }
        } else {
            require(interpreter.outputTensorCount == 2) { "Corner model must have corners and presence outputs." }
            0
        }
        return Runtime(
            interpreter = interpreter,
            model = model,
            modelContract = modelContract,
            inputWidth = inputShape.getOrElse(2) { error("LiteRT input width is missing.") },
            inputHeight = inputShape.getOrElse(1) { error("LiteRT input height is missing.") },
            legacyPredictionCount = legacyPredictionCount,
        )
    }

    private fun detectBitmap(bitmap: Bitmap, runtime: Runtime): CornerDetectionResult {
        val totalStart = System.nanoTime()
        val preprocessingStart = System.nanoTime()
        val prepared = prepareInput(
            bitmap = bitmap,
            inputWidth = runtime.inputWidth,
            inputHeight = runtime.inputHeight,
            normalizeToSignedRange = runtime.modelContract.outputKind == ScanlyModelContract.OutputKind.CORNERS_AND_PRESENCE,
        )
        val preprocessingNanos = System.nanoTime() - preprocessingStart
        val inferenceStart = System.nanoTime()
        val decoded = synchronized(runtime.lock) {
            when (runtime.modelContract.outputKind) {
                ScanlyModelContract.OutputKind.LEGACY_POSE -> runLegacy(runtime, prepared, bitmap)
                ScanlyModelContract.OutputKind.CORNERS_AND_PRESENCE -> runCornerRegression(runtime, prepared, bitmap)
            }
        }
        val inferenceAndRunNanos = System.nanoTime() - inferenceStart
        val inferenceNanos = (inferenceAndRunNanos - decoded.postprocessingNanos).coerceAtLeast(0)
        val timing = CornerDetectionTiming(
            preprocessingNanos = preprocessingNanos,
            inferenceNanos = inferenceNanos,
            postprocessingNanos = decoded.postprocessingNanos,
            totalNanos = System.nanoTime() - totalStart,
        )
        return CornerDetectionResult(
            quad = decoded.quad,
            confidence = decoded.confidence,
            inferenceTimeMillis = (timing.inferenceMillis).toLong(),
            modelName = runtime.modelContract.assetPath.substringAfterLast('/'),
            model = runtime.model,
            timing = timing,
        )
    }

    private fun runLegacy(runtime: Runtime, prepared: PreparedImage, bitmap: Bitmap): Decoded {
        val output = runtime.allocateOutput(0)
        runtime.interpreter.run(prepared.inputBuffer, output)
        val postStart = System.nanoTime()
        val prediction = decodeBestPrediction(
            outputBuffer = output,
            predictionCount = runtime.legacyPredictionCount,
            preparedImage = prepared,
            inputWidth = runtime.inputWidth,
            inputHeight = runtime.inputHeight,
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
        )
        return Decoded(prediction.confidence, prediction.quad, System.nanoTime() - postStart)
    }

    private fun runCornerRegression(runtime: Runtime, prepared: PreparedImage, bitmap: Bitmap): Decoded {
        val outputs: MutableMap<Int, Any> = (0 until runtime.interpreter.outputTensorCount)
            .associateWith { runtime.allocateOutput(it) as Any }
            .toMutableMap()
        runtime.interpreter.runForMultipleInputsOutputs(arrayOf(prepared.inputBuffer), outputs)
        val postStart = System.nanoTime()
        val cornerIndex = (0 until runtime.interpreter.outputTensorCount).first {
            runtime.interpreter.getOutputTensor(it).shape().contentEquals(intArrayOf(1, 4, 2))
        }
        val presenceIndex = (0 until runtime.interpreter.outputTensorCount).first {
            runtime.interpreter.getOutputTensor(it).shape().contentEquals(intArrayOf(1, 1))
        }
        val presenceBuffer = outputs.getValue(presenceIndex) as ByteBuffer
        val cornerBuffer = outputs.getValue(cornerIndex) as ByteBuffer
        val confidence = presenceBuffer.apply { rewind() }.asFloatBuffer().get(0)
        val cornerValues = FloatArray(8)
        cornerBuffer.apply { rewind() }.asFloatBuffer().get(cornerValues)
        val quad = if (confidence >= runtime.modelContract.presenceThreshold) {
            decodeRegressionQuad(cornerValues, prepared, runtime.inputWidth, runtime.inputHeight, bitmap.width, bitmap.height)
                .takeIf(DocumentCornerQuad::isValid)
        } else null
        return Decoded(confidence, quad, System.nanoTime() - postStart)
    }

    private fun loadModelBuffer(assetPath: String): ByteBuffer = loadMappedModelBuffer(assetPath) ?: run {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
    }

    private fun loadMappedModelBuffer(assetPath: String): ByteBuffer? = runCatching {
        context.assets.openFd(assetPath).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
            }
        }
    }.getOrElse { error ->
        Log.w(TAG, "Falling back to streamed model loading for $assetPath.", error)
        null
    }

    private data class Runtime(
        val interpreter: InterpreterApi,
        val model: DocumentCornerModel,
        val modelContract: ScanlyModelContract,
        val inputWidth: Int,
        val inputHeight: Int,
        val legacyPredictionCount: Int,
        val lock: Any = Any(),
    ) {
        fun allocateOutput(index: Int): ByteBuffer = ByteBuffer.allocateDirect(interpreter.getOutputTensor(index).numBytes())
            .order(ByteOrder.nativeOrder())
    }

    private data class Decoded(val confidence: Float, val quad: DocumentCornerQuad?, val postprocessingNanos: Long)

    private companion object {
        const val TAG = "LiteRtCornerDetector"
    }
}

internal fun decodeRegressionQuad(
    values: FloatArray,
    prepared: PreparedImage,
    inputWidth: Int,
    inputHeight: Int,
    originalWidth: Int,
    originalHeight: Int,
): DocumentCornerQuad {
    require(values.size >= 8)
    fun point(index: Int): NormalizedPoint {
        val x = (((values[index * 2] * inputWidth) - prepared.padX) / prepared.scale / originalWidth).coerceIn(0f, 1f)
        val y = (((values[index * 2 + 1] * inputHeight) - prepared.padY) / prepared.scale / originalHeight).coerceIn(0f, 1f)
        return NormalizedPoint(x, y)
    }
    return DocumentCornerQuad(point(0), point(1), point(2), point(3))
}
