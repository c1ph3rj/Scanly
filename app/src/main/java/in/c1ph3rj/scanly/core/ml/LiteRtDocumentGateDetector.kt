package `in`.c1ph3rj.scanly.core.ml

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
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
class LiteRtDocumentGateDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) : DocumentGateDetector {
    private val runtime: Runtime by lazy(LazyThreadSafetyMode.SYNCHRONIZED, ::createRuntime)

    override suspend fun classify(bitmap: Bitmap): DocumentGateResult = withContext(dispatchers.default) {
        val activeRuntime = runtime
        val totalStart = System.nanoTime()
        val preprocessingStart = System.nanoTime()
        val prepared = prepareInput(
            bitmap = bitmap,
            inputWidth = activeRuntime.inputWidth,
            inputHeight = activeRuntime.inputHeight,
            normalizeToSignedRange = true,
        )
        val preprocessingNanos = System.nanoTime() - preprocessingStart
        val output = ByteBuffer.allocateDirect(activeRuntime.outputBytes).order(ByteOrder.nativeOrder())
        val inferenceStart = System.nanoTime()
        synchronized(activeRuntime.lock) {
            activeRuntime.interpreter.run(prepared.inputBuffer, output)
        }
        val inferenceNanos = System.nanoTime() - inferenceStart
        val postprocessingStart = System.nanoTime()
        output.rewind()
        val values = FloatArray(DocumentGateClass.entries.size)
        output.asFloatBuffer().get(values)
        val probabilities = DocumentGateClass.entries.associateWith { className ->
            values[className.ordinal].coerceIn(0f, 1f)
        }
        val postprocessingNanos = System.nanoTime() - postprocessingStart
        DocumentGateResult(
            probabilities = probabilities,
            timing = DocumentGateTiming(
                preprocessingNanos = preprocessingNanos,
                inferenceNanos = inferenceNanos,
                postprocessingNanos = postprocessingNanos,
                totalNanos = System.nanoTime() - totalStart,
            ),
        )
    }

    private fun createRuntime(): Runtime {
        TensorFlowLite.init()
        val interpreter = InterpreterApi.create(
            loadMappedModelBuffer(),
            InterpreterApi.Options().apply { setNumThreads(THREAD_COUNT) },
        )
        interpreter.allocateTensors()
        val inputShape = interpreter.getInputTensor(0).shape()
        require(inputShape.contentEquals(intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3))) {
            "Document gate input must be [1, 160, 160, 3]."
        }
        require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 3))) {
            "Document gate output must be [1, 3]."
        }
        return Runtime(
            interpreter = interpreter,
            inputWidth = inputShape[2],
            inputHeight = inputShape[1],
            outputBytes = interpreter.getOutputTensor(0).numBytes(),
        )
    }

    private fun loadMappedModelBuffer(): ByteBuffer = context.assets.openFd(ASSET_PATH).use { descriptor ->
        FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
        }
    }

    private data class Runtime(
        val interpreter: InterpreterApi,
        val inputWidth: Int,
        val inputHeight: Int,
        val outputBytes: Int,
        val lock: Any = Any(),
    )

    private companion object {
        const val ASSET_PATH = "models/scanly_document_gate_float16.tflite"
        const val INPUT_SIZE = 160
        const val THREAD_COUNT = 4
    }
}
