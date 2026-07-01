package `in`.c1ph3rj.scanly.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.TensorFlowLite
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.system.measureTimeMillis
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtDocumentCornerDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) : DocumentCornerDetector {

    @Volatile
    private var cachedRuntime: LiteRtDetectionRuntime? = null

    override suspend fun detect(frame: DetectionFrame): CornerDetectionResult = withContext(dispatchers.default) {
        val runtime = getOrCreateRuntime()
        val sourceBitmap = frame.toBitmap()
        val orientedBitmap = sourceBitmap.rotate(frame.rotationDegrees)
        if (orientedBitmap !== sourceBitmap) {
            sourceBitmap.recycle()
        }

        try {
            detectBitmapInternal(
                bitmap = orientedBitmap,
                runtime = runtime,
            )
        } finally {
            orientedBitmap.recycle()
        }
    }

    override suspend fun detect(bitmap: Bitmap): CornerDetectionResult = withContext(dispatchers.default) {
        detectBitmapInternal(
            bitmap = bitmap,
            runtime = getOrCreateRuntime(),
        )
    }

    private fun getOrCreateRuntime(): LiteRtDetectionRuntime {
        cachedRuntime?.let { return it }
        return synchronized(this) {
            cachedRuntime ?: createRuntime().also { cachedRuntime = it }
        }
    }

    private fun createRuntime(): LiteRtDetectionRuntime {
        TensorFlowLite.init()
        val modelBuffer = loadModelBuffer(ScanlyModelAssets.modelAssetPath)
        val options = InterpreterApi.Options().apply {
            setNumThreads(LiteRtPoseConstants.DEFAULT_THREAD_COUNT)
        }
        val interpreter = InterpreterApi.create(modelBuffer, options)
        interpreter.allocateTensors()

        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        val inputShape = inputTensor.shape()
        val outputShape = outputTensor.shape()
        val contract = LiteRtModelContract(
            inputWidth = inputShape.getOrElse(2) { error("LiteRT input width is missing.") },
            inputHeight = inputShape.getOrElse(1) { error("LiteRT input height is missing.") },
            predictionCount = outputShape.getOrElse(2) { error("LiteRT output prediction count is missing.") },
            modelName = ScanlyModelAssets.modelAssetPath.substringAfterLast('/'),
        )

        return LiteRtDetectionRuntime(
            interpreter = interpreter,
            contract = contract,
            preprocessor = LiteRtImagePreprocessor(contract.inputWidth, contract.inputHeight),
            outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder()),
            outputValues = FloatArray(outputTensor.numBytes() / LiteRtPoseConstants.FLOAT_BYTES),
        )
    }

    private fun loadModelBuffer(assetPath: String): ByteBuffer =
        loadMappedModelBuffer(assetPath) ?: loadStreamedModelBuffer(assetPath)

    private fun loadMappedModelBuffer(assetPath: String): ByteBuffer? =
        runCatching {
            context.assets.openFd(assetPath).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { fileChannel ->
                    fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "Falling back to streamed LiteRT model loading for $assetPath.", error)
            null
        }

    private fun loadStreamedModelBuffer(assetPath: String): ByteBuffer {
        val modelBytes = context.assets.open(assetPath).use { inputStream ->
            inputStream.readBytes()
        }
        return ByteBuffer.allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(modelBytes)
                rewind()
            }
    }

    private fun DetectionFrame.toBitmap(): Bitmap {
        val pixels = IntArray(width * height)
        var offset = 0
        for (index in pixels.indices) {
            val red = bytes[offset].toInt() and 0xFF
            val green = bytes[offset + 1].toInt() and 0xFF
            val blue = bytes[offset + 2].toInt() and 0xFF
            val alpha = bytes[offset + 3].toInt() and 0xFF
            pixels[index] = android.graphics.Color.argb(alpha, red, green, blue)
            offset += RGBA_PIXEL_STRIDE
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        if (normalizedRotation == 0) {
            return this
        }

        val matrix = Matrix().apply {
            postRotate(normalizedRotation.toFloat())
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun detectBitmapInternal(
        bitmap: Bitmap,
        runtime: LiteRtDetectionRuntime,
    ): CornerDetectionResult {
        return synchronized(runtime.lock) {
            val preparedImage = runtime.preprocessor.prepare(bitmap)
            runtime.outputBuffer.clear()
            val inferenceTimeMillis = measureTimeMillis {
                runtime.interpreter.run(preparedImage.inputBuffer, runtime.outputBuffer)
            }
            val decoded = decodeBestPrediction(
                outputBuffer = runtime.outputBuffer,
                predictionCount = runtime.contract.predictionCount,
                preparedImage = preparedImage,
                inputWidth = runtime.contract.inputWidth,
                inputHeight = runtime.contract.inputHeight,
                originalWidth = bitmap.width,
                originalHeight = bitmap.height,
                values = runtime.outputValues,
            )

            CornerDetectionResult(
                quad = decoded.quad,
                confidence = decoded.confidence,
                inferenceTimeMillis = inferenceTimeMillis,
                modelName = runtime.contract.modelName,
            )
        }
    }

    private data class LiteRtDetectionRuntime(
        val interpreter: InterpreterApi,
        val contract: LiteRtModelContract,
        val preprocessor: LiteRtImagePreprocessor,
        val outputBuffer: ByteBuffer,
        val outputValues: FloatArray,
        val lock: Any = Any(),
    )

    private companion object {
        const val TAG = "LiteRtCornerDetector"
        const val RGBA_PIXEL_STRIDE = 4
    }
}
