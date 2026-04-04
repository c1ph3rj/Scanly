package `in`.c1ph3rj.scanly.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.TensorFlowLite
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
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
        val modelBuffer = loadModelFile(ScanlyModelAssets.modelAssetPath)
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
        )
    }

    private fun loadModelFile(assetPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetPath)
        return fileDescriptor.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { fileChannel ->
                fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength,
                )
            }
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
        val preparedImage = prepareInput(
            bitmap = bitmap,
            inputWidth = runtime.contract.inputWidth,
            inputHeight = runtime.contract.inputHeight,
        )
        val outputBuffer = runtime.allocateOutputBuffer()
        val inferenceTimeMillis = measureTimeMillis {
            synchronized(runtime.lock) {
                runtime.interpreter.run(preparedImage.inputBuffer, outputBuffer)
            }
        }
        val decoded = decodeBestPrediction(
            outputBuffer = outputBuffer,
            predictionCount = runtime.contract.predictionCount,
            preparedImage = preparedImage,
            inputWidth = runtime.contract.inputWidth,
            inputHeight = runtime.contract.inputHeight,
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
        )

        return CornerDetectionResult(
            quad = decoded.quad,
            confidence = decoded.confidence,
            inferenceTimeMillis = inferenceTimeMillis,
            modelName = runtime.contract.modelName,
        )
    }

    private data class LiteRtDetectionRuntime(
        val interpreter: InterpreterApi,
        val contract: LiteRtModelContract,
        val lock: Any = Any(),
    ) {
        fun allocateOutputBuffer(): ByteBuffer =
            ByteBuffer.allocateDirect(interpreter.getOutputTensor(0).numBytes())
                .order(java.nio.ByteOrder.nativeOrder())
    }

    private companion object {
        const val RGBA_PIXEL_STRIDE = 4
    }
}
