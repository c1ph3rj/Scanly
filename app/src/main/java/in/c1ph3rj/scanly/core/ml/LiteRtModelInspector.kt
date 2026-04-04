package `in`.c1ph3rj.scanly.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.TensorFlowLite
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.hypot
import kotlin.system.measureNanoTime
import javax.inject.Inject

class LiteRtModelInspector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun inspect(readinessReport: SprintZeroReadinessReport): LiteRtProbeReport =
        withContext(Dispatchers.Default) {
            if (!readinessReport.modelPresent) {
                error("The model asset is missing from ${ScanlyModelAssets.modelAssetPath}.")
            }

            TensorFlowLite.init()
            val modelBuffer = loadModelFile(ScanlyModelAssets.modelAssetPath)
            val options = InterpreterApi.Options().apply {
                setNumThreads(LiteRtPoseConstants.DEFAULT_THREAD_COUNT)
            }

            val interpreter = InterpreterApi.create(modelBuffer, options)
            try {
                interpreter.allocateTensors()

                val inputTensors = (0 until interpreter.inputTensorCount)
                    .map { index -> interpreter.getInputTensor(index).toSpec(index) }
                val outputTensors = (0 until interpreter.outputTensorCount)
                    .map { index -> interpreter.getOutputTensor(index).toSpec(index) }
                val smokeInferenceMillis = runSmokeInference(interpreter)
                val validationReport = runValidation(interpreter, inputTensors, outputTensors)

                LiteRtProbeReport(
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    runtimeVersion = TensorFlowLite.runtimeVersion(),
                    schemaVersion = TensorFlowLite.schemaVersion(),
                    inputTensors = inputTensors,
                    outputTensors = outputTensors,
                    smokeInferenceMillis = smokeInferenceMillis,
                    validationReport = validationReport,
                )
            } finally {
                interpreter.close()
            }
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

    private fun runSmokeInference(interpreter: InterpreterApi): Double {
        val inputs = Array(interpreter.inputTensorCount) { index ->
            allocateTensorBuffer(interpreter.getInputTensor(index))
        }
        val outputs = buildMap<Int, Any> {
            repeat(interpreter.outputTensorCount) { index ->
                put(index, allocateTensorBuffer(interpreter.getOutputTensor(index)))
            }
        }

        val elapsedNanos = measureNanoTime {
            interpreter.runForMultipleInputsOutputs(inputs, outputs)
        }
        return elapsedNanos / 1_000_000.0
    }

    private fun allocateTensorBuffer(tensor: Tensor): ByteBuffer =
        ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())

    private fun Tensor.toSpec(index: Int): LiteRtTensorSpec = LiteRtTensorSpec(
        index = index,
        dataType = dataType().name,
        shape = shape().toList(),
        byteCount = numBytes(),
    )

    private fun runValidation(
        interpreter: InterpreterApi,
        inputTensors: List<LiteRtTensorSpec>,
        outputTensors: List<LiteRtTensorSpec>,
    ): LiteRtValidationReport? {
        val inputTensor = inputTensors.firstOrNull() ?: return null
        val outputTensor = outputTensors.firstOrNull() ?: return null
        val inputHeight = inputTensor.shape.getOrNull(1) ?: return null
        val inputWidth = inputTensor.shape.getOrNull(2) ?: return null
        val predictionCount = outputTensor.shape.getOrNull(2) ?: return null
        val channelCount = outputTensor.shape.getOrNull(1) ?: return null
        if (channelCount < 17) {
            return null
        }

        val validationFiles = context.assets.list(ScanlyModelAssets.expectedCornersDirectory)
            ?.filter { it.endsWith(ScanlyModelAssets.expectedCornersExtension, ignoreCase = true) }
            .orEmpty()
            .sorted()

        val outcomes = validationFiles.mapNotNull { fileName ->
            runCatching {
                evaluateValidationFile(
                    interpreter = interpreter,
                    inputWidth = inputWidth,
                    inputHeight = inputHeight,
                    predictionCount = predictionCount,
                    fileName = fileName,
                )
            }.getOrNull()
        }

        if (outcomes.isEmpty()) {
            return null
        }

        val detected = outcomes.filter { it.confidence >= LiteRtPoseConstants.DETECTION_CONFIDENCE_THRESHOLD }
        if (detected.isEmpty()) {
            return LiteRtValidationReport(
                samplesEvaluated = outcomes.size,
                detectionsFound = 0,
                averageConfidence = 0.0,
                meanCornerErrorNormalized = 0.0,
                meanCornerErrorPixels = 0.0,
                bestSample = null,
                worstSample = null,
            )
        }

        return LiteRtValidationReport(
            samplesEvaluated = outcomes.size,
            detectionsFound = detected.size,
            averageConfidence = detected.map { it.confidence }.average(),
            meanCornerErrorNormalized = detected.map { it.meanCornerErrorNormalized }.average(),
            meanCornerErrorPixels = detected.map { it.meanCornerErrorPixels }.average(),
            bestSample = detected.minByOrNull { it.meanCornerErrorNormalized },
            worstSample = detected.maxByOrNull { it.meanCornerErrorNormalized },
        )
    }

    private fun evaluateValidationFile(
        interpreter: InterpreterApi,
        inputWidth: Int,
        inputHeight: Int,
        predictionCount: Int,
        fileName: String,
    ): ValidationSampleOutcome {
        val jsonPath = "${ScanlyModelAssets.expectedCornersDirectory}/$fileName"
        val expected = context.assets.open(jsonPath).use { input ->
            JSONObject(input.bufferedReader().readText())
        }
        val imageName = expected.getString("image")
        val imagePath = "${ScanlyModelAssets.validationImagesDirectory}/$imageName"
        val expectedCorners = readExpectedCorners(expected)
        val bitmap = context.assets.open(imagePath).use(BitmapFactory::decodeStream)
            ?: error("Could not decode $imagePath")

        val prepared = prepareInput(bitmap, inputWidth = inputWidth, inputHeight = inputHeight)
        val inputBuffer = prepared.inputBuffer
        val outputTensor = interpreter.getOutputTensor(0)
        val outputBuffer = allocateTensorBuffer(outputTensor)
        interpreter.run(inputBuffer, outputBuffer)
        val bestPrediction = decodeBestPrediction(
            outputBuffer = outputBuffer,
            predictionCount = predictionCount,
            preparedImage = prepared,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
        )

        val quad = checkNotNull(bestPrediction.quad) {
            "Expected a valid quad for ${expected.getString("image")}."
        }

        return ValidationSampleOutcome(
            imageName = imageName,
            confidence = bestPrediction.confidence.toDouble(),
            meanCornerErrorNormalized = meanCornerErrorNormalized(
                predicted = quad,
                expected = expectedCorners,
            ),
            meanCornerErrorPixels = meanCornerErrorPixels(
                predicted = quad,
                expected = expectedCorners,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
            ),
        )
    }

    private fun readExpectedCorners(jsonObject: JSONObject): DocumentCornerQuad {
        val corners = jsonObject.getJSONObject("corners")
        return DocumentCornerQuad(
            topLeft = corners.getPoint("TL"),
            topRight = corners.getPoint("TR"),
            bottomRight = corners.getPoint("BR"),
            bottomLeft = corners.getPoint("BL"),
        )
    }

    private fun JSONObject.getPoint(key: String): NormalizedPoint {
        val pointArray = getJSONArray(key)
        return NormalizedPoint(
            x = pointArray.getDouble(0).toFloat(),
            y = pointArray.getDouble(1).toFloat(),
        )
    }

    private fun meanCornerErrorNormalized(
        predicted: DocumentCornerQuad,
        expected: DocumentCornerQuad,
    ): Double = predicted.points()
        .zip(expected.points())
        .map { (predictedPoint, expectedPoint) ->
            hypot(
                (predictedPoint.x - expectedPoint.x).toDouble(),
                (predictedPoint.y - expectedPoint.y).toDouble(),
            )
        }
        .average()

    private fun meanCornerErrorPixels(
        predicted: DocumentCornerQuad,
        expected: DocumentCornerQuad,
        imageWidth: Int,
        imageHeight: Int,
    ): Double = predicted.points()
        .zip(expected.points())
        .map { (predictedPoint, expectedPoint) ->
            hypot(
                ((predictedPoint.x - expectedPoint.x) * imageWidth).toDouble(),
                ((predictedPoint.y - expectedPoint.y) * imageHeight).toDouble(),
            )
        }
        .average()

    private fun DocumentCornerQuad.points(): List<NormalizedPoint> = listOf(
        topLeft,
        topRight,
        bottomRight,
        bottomLeft,
    )
    private companion object
}
