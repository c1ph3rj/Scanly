package `in`.c1ph3rj.scanly.data.text.indra

import android.content.Context
import android.util.Log
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.TensorFlowLite
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

internal class TensorFeed(val name: String, val shape: IntArray, val buffer: ByteBuffer)

internal class TensorResult(val shape: IntArray, val buffer: ByteBuffer) {
    fun floats(): FloatArray {
        val out = FloatArray(shape.fold(1) { a, b -> a * b })
        buffer.rewind()
        buffer.asFloatBuffer().get(out)
        return out
    }

    fun ints(): IntArray {
        val out = IntArray(shape.fold(1) { a, b -> a * b })
        buffer.rewind()
        buffer.asIntBuffer().get(out)
        return out
    }
}

internal fun floatFeed(name: String, shape: IntArray, data: FloatArray): TensorFeed {
    val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
    buffer.asFloatBuffer().put(data)
    return TensorFeed(name, shape, buffer)
}

internal fun intFeed(name: String, shape: IntArray, data: IntArray): TensorFeed {
    val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
    buffer.asIntBuffer().put(data)
    return TensorFeed(name, shape, buffer)
}

/**
 * One LiteRT model with named `serving_default` tensors. The names are only used to find each
 * tensor's interpreter position; inference goes through the main interpreter, because the
 * signature runner keeps its own tensors and does not see [InterpreterApi.resizeInput]. Dynamic
 * dimensions are resized per call.
 */
internal class SignatureModel(
    private val label: String,
    private val interpreter: InterpreterApi,
    expectedInputs: Set<String>,
    expectedOutputs: Set<String>,
) : AutoCloseable {
    private val inputPositions: Map<String, Int>
    private val outputPositions: Map<String, Int>

    init {
        val keys = interpreter.signatureKeys.toSet()
        check(SIGNATURE in keys) { "Indra model $label has no '$SIGNATURE' signature (found $keys)." }
        val inputs = interpreter.getSignatureInputs(SIGNATURE).toSet()
        val outputs = interpreter.getSignatureOutputs(SIGNATURE).toSet()
        check(inputs.containsAll(expectedInputs)) {
            "Indra model $label inputs $inputs do not include $expectedInputs."
        }
        check(outputs.containsAll(expectedOutputs)) {
            "Indra model $label outputs $outputs do not include $expectedOutputs."
        }
        inputPositions = expectedInputs.associateWith { name ->
            val tensorIndex = interpreter.getInputTensorFromSignature(name, SIGNATURE).index()
            (0 until interpreter.inputTensorCount).firstOrNull { interpreter.getInputTensor(it).index() == tensorIndex }
                ?: error("Indra model $label input '$name' is not an interpreter input.")
        }
        check(inputPositions.size == interpreter.inputTensorCount) {
            "Indra model $label has ${interpreter.inputTensorCount} inputs; expected ${inputPositions.keys}."
        }
        outputPositions = expectedOutputs.associateWith { name ->
            val tensorIndex = interpreter.getOutputTensorFromSignature(name, SIGNATURE).index()
            (0 until interpreter.outputTensorCount).firstOrNull { interpreter.getOutputTensor(it).index() == tensorIndex }
                ?: error("Indra model $label output '$name' is not an interpreter output.")
        }
    }

    fun run(feeds: List<TensorFeed>, outputNames: List<String>): Map<String, TensorResult> {
        val inputs = arrayOfNulls<Any>(interpreter.inputTensorCount)
        feeds.forEach { feed ->
            val position = inputPositions.getValue(feed.name)
            if (!interpreter.getInputTensor(position).shape().contentEquals(feed.shape)) {
                interpreter.resizeInput(position, feed.shape)
            }
            feed.buffer.rewind()
            inputs[position] = feed.buffer
        }
        check(inputs.all { it != null }) { "Indra model $label is missing an input feed." }
        // No allocateTensors() here: LiteRT refreshes the cached output shapes only when the run
        // itself does the allocation. Output shapes are resolved during invoke, so nothing can be
        // pre-sized: null placeholders skip the copy, and the tensors are read back afterwards.
        val placeholders = HashMap<Int, Any?>()
        outputPositions.values.forEach { placeholders[it] = null }
        @Suppress("UNCHECKED_CAST")
        interpreter.runForMultipleInputsOutputs(inputs, placeholders as Map<Int, Any>)
        return outputNames.associateWith { name ->
            val tensor = interpreter.getOutputTensor(outputPositions.getValue(name))
            val source = tensor.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
            val copy = ByteBuffer.allocateDirect(source.remaining()).order(ByteOrder.nativeOrder())
            copy.put(source)
            val shape = tensor.shape()
            check(shape.fold(1) { acc, d -> acc * d } * 4 == copy.capacity()) {
                "Indra model $label output '$name' shape ${shape.toList()} does not match ${copy.capacity()} bytes."
            }
            TensorResult(shape, copy.also { it.rewind() })
        }
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        const val SIGNATURE = "serving_default"
    }
}

/** Lazily created interpreters and vocabularies, so a page only loads the recognizers it needs. */
internal class IndraModels(private val context: Context) {
    private val models = HashMap<String, SignatureModel>()
    private val vocabs = HashMap<String, IndraVocab>()

    fun detector() = model("det", setOf("x"), setOf("out"))

    fun orienter() = model("orient", setOf("x"), setOf("p"))

    fun script() = model("script", setOf("x", "widths"), setOf("p"))

    fun recognizer(name: String) = model(name, setOf("x", "widths"), setOf("logits", "lengths"))

    fun vocab(suffix: String): IndraVocab = vocabs.getOrPut(suffix) {
        val json = context.assets.open("$ASSET_DIR/vocab_$suffix.json").bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        IndraVocab.fromJson(json)
    }

    private fun model(name: String, inputs: Set<String>, outputs: Set<String>): SignatureModel =
        models.getOrPut(name) {
            TensorFlowLite.init()
            val buffer = mapAsset("$ASSET_DIR/$name.tflite")
            val interpreter = try {
                InterpreterApi.create(buffer, InterpreterApi.Options().apply { setNumThreads(THREAD_COUNT) })
            } catch (e: IllegalStateException) {
                // The recognizers' placeholder shapes (width 1) cannot be prepared by XNNPACK at
                // creation time; the built-in CPU kernels handle them.
                Log.w(TAG, "Indra model $name: XNNPACK unavailable, using built-in kernels.", e)
                InterpreterApi.create(
                    buffer,
                    InterpreterApi.Options().apply {
                        setNumThreads(THREAD_COUNT)
                        setUseXNNPACK(false)
                    },
                )
            }
            try {
                SignatureModel(name, interpreter, inputs, outputs)
            } catch (e: Throwable) {
                interpreter.close()
                throw e
            }
        }

    private fun mapAsset(path: String): ByteBuffer = context.assets.openFd(path).use { descriptor ->
        FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
        }
    }

    fun close() {
        models.values.forEach { runCatching { it.close() } }
        models.clear()
        vocabs.clear()
    }

    private companion object {
        const val TAG = "IndraOcr"
        const val ASSET_DIR = "models/indra"
        const val THREAD_COUNT = 4
    }
}
