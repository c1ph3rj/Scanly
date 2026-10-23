package `in`.c1ph3rj.scanly.data.text.indra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.data.text.PageTextRecognizer
import `in`.c1ph3rj.scanly.data.text.RecognizedPage
import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import `in`.c1ph3rj.scanly.domain.model.PageTextBlock
import `in`.c1ph3rj.scanly.domain.model.PageTextLine
import `in`.c1ph3rj.scanly.domain.model.TextBox
import `in`.c1ph3rj.scanly.domain.text.TextReflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device OCR using the LiteRT port of datalab-to/indra-ocr: orientation and skew, line
 * detection, script routing, four CTC recognizers and XY-cut reading order. Models are created on
 * first use and closed by [release]; calls are serialized.
 *
 * Not implemented (see the port spec): the order model, n-gram language models, gutter splitting,
 * bullet joining, and the vertical-CJK page mode (orientation class 4 is treated as upright).
 */
@Singleton
class IndraPageTextRecognizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) : PageTextRecognizer {
    private val mutex = Mutex()
    private var models: IndraModels? = null

    override suspend fun recognize(imagePath: String): RecognizedPage = mutex.withLock {
        withContext(dispatchers.default) { process(imagePath) }
    }

    override suspend fun release() = mutex.withLock {
        models?.close()
        models = null
    }

    /**
     * The first call loads the models every page needs, so a load failure surfaces as
     * [IndraUnavailableException] before any page work starts.
     */
    private fun models(): IndraModels {
        models?.let { return it }
        val created = IndraModels(context)
        try {
            created.detector()
            created.orienter()
            created.script()
        } catch (e: Throwable) {
            runCatching { created.close() }
            throw IndraUnavailableException("Indra OCR models could not be loaded.", e)
        }
        models = created
        return created
    }

    private fun ensureOpenCv() {
        try {
            IndraImageOps.ensureInitialized()
        } catch (e: Throwable) {
            throw IndraUnavailableException("OpenCV could not be initialized for Indra OCR.", e)
        }
    }

    private class Line(val box: IndraBox, val dark: Boolean, val crop1: CropGeometry, val crop3: CropGeometry) {
        fun crop(window: Int) = if (window == 1) crop1 else crop3
    }

    private class Reading(val text: String, val conf: Float, val needsFlip: Boolean)

    private class BatchInput(val ink: FloatArray, val widths: IntArray, val paddedWidth: Int, val height: Int)

    private suspend fun process(imagePath: String): RecognizedPage {
        ensureOpenCv()
        models()
        val tracked = ArrayList<Mat>()
        fun Mat.track(): Mat = also { tracked += it }
        try {
            val bitmap = decodeForRecognition(imagePath)
                ?: throw IllegalStateException("Page image could not be read.")
            var gray = try {
                IndraImageOps.grayFromBitmap(bitmap)
            } finally {
                bitmap.recycle()
            }
            gray = IndraImageOps.rotateClockwise(gray, exifRotation(imagePath)).track()
            val originalWidth = gray.cols()
            val originalHeight = gray.rows()
            currentCoroutineContext().ensureActive()

            // 1. Preview, skew, orientation.
            val small = IndraImageOps.preview(gray).track()
            var angle = IndraImageOps.estimateSkew(small)
            val orientSource = if (IndraImageOps.isTilted(angle)) {
                IndraImageOps.rotatePage(small, angle).track()
            } else {
                small
            }
            val turns = detectTurns(orientSource)
            if (turns == 1 || turns == 3) {
                angle = IndraImageOps.estimateSkew(IndraImageOps.turn(small, turns).track())
            }
            if (!IndraImageOps.isTilted(angle)) angle = 0.0
            var page = if (turns == 0) gray else IndraImageOps.turn(gray, turns).track()
            if (angle != 0.0) page = IndraImageOps.rotatePage(page, angle).track()
            val transform = IndraPageTransform(originalWidth, originalHeight, turns, angle)
            currentCoroutineContext().ensureActive()

            // 2. Line detection.
            val boxes = detectLines(page)
            Log.d(TAG, "page ${page.cols()}x${page.rows()} turns=$turns angle=$angle lines=${boxes.size}")
            if (boxes.isEmpty()) return RecognizedPage("", emptyList(), originalWidth, originalHeight, OcrEngine.INDRA)
            val lines = boxes.map { box ->
                Line(
                    box = box,
                    dark = IndraGeometry.isDarkLine(IndraImageOps.histogram(page, box)),
                    crop1 = IndraGeometry.cropGeometry(box, 1),
                    crop3 = IndraGeometry.cropGeometry(box, 3),
                )
            }
            currentCoroutineContext().ensureActive()

            // 3-5. Route by script, read, decode.
            val readings = readLines(page, lines)
            val rawTexts = readings.map { it?.text.orEmpty() }
            Log.d(TAG, "read ${readings.count { !it?.text.isNullOrEmpty() }} of ${lines.size} lines")
            val pageRtl = IndraRtl.isPageRtl(rawTexts)
            val texts = readings.mapIndexed { index, reading ->
                val text = rawTexts[index]
                if (reading?.needsFlip == true) IndraRtl.flip(text, if (pageRtl) true else null) else text
            }
            val kept = lines.indices.filter { i ->
                texts[i].isNotBlank() && !IndraLayout.isLeaderLine(texts[i], lines[i].box)
            }
            if (kept.isEmpty()) return RecognizedPage("", emptyList(), originalWidth, originalHeight, OcrEngine.INDRA)

            // 6-8. Reading order and blocks.
            var orderBoxes = kept.map { lines[it].box }
            if (pageRtl) orderBoxes = IndraLayout.mirror(orderBoxes, page.cols().toFloat())
            val ordered = IndraLayout.xyCut(orderBoxes).map { kept[it] }
            val blocks = IndraLayout.groupBlocks(ordered.map { lines[it].box }).map { members ->
                val blockLines = members.map { m ->
                    val index = ordered[m]
                    PageTextLine(texts[index], transform.boxToOriginal(lines[index].box).normalized(originalWidth, originalHeight))
                }
                PageTextBlock(
                    text = TextReflow.paragraph(blockLines.map { it.text }),
                    box = TextBox(
                        left = blockLines.minOf { it.box.left },
                        top = blockLines.minOf { it.box.top },
                        right = blockLines.maxOf { it.box.right },
                        bottom = blockLines.maxOf { it.box.bottom },
                    ),
                    lines = blockLines,
                )
            }
            return RecognizedPage(
                text = TextReflow.page(blocks.map { it.text }),
                blocks = blocks,
                imageWidth = originalWidth,
                imageHeight = originalHeight,
                engine = OcrEngine.INDRA,
            )
        } finally {
            tracked.forEach { runCatching { it.release() } }
        }
    }

    /** Quarter turns (clockwise) that make the page upright. Class 4 (vertical CJK) is treated as upright. */
    private fun detectTurns(small: Mat): Int {
        val result = models().orienter().run(
            listOf(floatFeed("x", intArrayOf(1, 768, 768, 1), IndraImageOps.orientInput(small))),
            listOf("p"),
        ).getValue("p").floats()
        var best = 0
        for (i in 1 until result.size) if (result[i] > result[best]) best = i
        return if (best in 1..3 && result[best] > ORIENT_MIN) best else 0
    }

    private fun detectLines(page: Mat): List<IndraBox> {
        var pass = detectPass(page, IndraDetDecoder.DET_LONG_SIDE)
        IndraDetDecoder.adaptiveLongSide(pass.first)?.let { pass = detectPass(page, it) }
        val (boxes, scale) = pass
        val width = page.cols().toFloat()
        val height = page.rows().toFloat()
        return boxes.mapNotNull { box ->
            val clipped = IndraBox(
                x0 = (box.x0 / scale).toFloat().coerceIn(0f, width),
                y0 = (box.y0 / scale).toFloat().coerceIn(0f, height),
                x1 = (box.x1 / scale).toFloat().coerceIn(0f, width),
                y1 = (box.y1 / scale).toFloat().coerceIn(0f, height),
                score = box.score,
            )
            clipped.takeIf { it.width >= 2f && it.height >= 2f }
        }
    }

    private fun detectPass(page: Mat, longSide: Int): Pair<List<IndraBox>, Double> {
        val input = IndraImageOps.detInput(page, longSide)
        val result = models().detector().run(
            listOf(floatFeed("x", intArrayOf(1, input.paddedHeight, input.paddedWidth, 1), input.ink)),
            listOf("out"),
        ).getValue("out")
        check(result.shape.size == 4 && result.shape[3] == 3) {
            "Indra detector output must be [1, H/2, W/2, 3], got ${result.shape.toList()}."
        }
        val boxes = IndraDetDecoder.decode(result.floats(), result.shape[1], result.shape[2])
        return boxes to input.scale
    }

    private suspend fun readLines(page: Mat, lines: List<Line>): List<Reading?> {
        val groups = routeLines(page, lines)
        val readings = arrayOfNulls<Reading>(lines.size)

        // The base reader takes base-routed and unsure lines; it runs first so ties keep its reading.
        val baseLines = lines.indices.filter { groups[it].group == GROUP_BASE || !groups[it].sure }
        readSet(page, lines, baseLines, window = 3, modelName = "rec", vocabName = "base", base = true, maxLines = 16)
            .forEach { (index, reading) -> readings[index] = reading }

        for ((group, names) in GROUP_MODELS) {
            val members = lines.indices.filter { groups[it].group == group }
            if (members.isEmpty()) continue
            val maxLines = if (group == GROUP_CJK) 1 else 16
            readSet(page, lines, members, window = 1, modelName = names.first, vocabName = names.second, base = false, maxLines = maxLines)
                .forEach { (index, reading) ->
                    val existing = readings[index]
                    if (existing == null || reading.conf > existing.conf) readings[index] = reading
                }
        }
        return readings.toList()
    }

    private class Route(val group: Int, val sure: Boolean)

    private suspend fun routeLines(page: Mat, lines: List<Line>): List<Route> {
        val routes = arrayOfNulls<Route>(lines.size)
        val script = models().script()
        val widths = IntArray(lines.size) { lines[it].crop1.outWidth }
        for (batch in IndraGeometry.widthBatches(widths, maxLines = 64)) {
            currentCoroutineContext().ensureActive()
            val input = buildBatch(page, lines, batch, window = 1)
            val probabilities = script.run(batchFeeds(input), listOf("p")).getValue("p").floats()
            batch.forEachIndexed { b, line ->
                var best = 0
                for (c in 1 until SCRIPT_GROUPS) {
                    if (probabilities[b * SCRIPT_GROUPS + c] > probabilities[b * SCRIPT_GROUPS + best]) best = c
                }
                routes[line] = Route(best, probabilities[b * SCRIPT_GROUPS + best] > ROUTE_SURE)
            }
        }
        return routes.map { it ?: Route(GROUP_BASE, false) }
    }

    private suspend fun readSet(
        page: Mat,
        lines: List<Line>,
        members: List<Int>,
        window: Int,
        modelName: String,
        vocabName: String,
        base: Boolean,
        maxLines: Int,
    ): Map<Int, Reading> {
        if (members.isEmpty()) return emptyMap()
        val model = models().recognizer(modelName)
        val vocab = models().vocab(vocabName)
        val widths = IntArray(members.size) { lines[members[it]].crop(window).outWidth }
        val readings = HashMap<Int, Reading>()
        for (batch in IndraGeometry.widthBatches(widths, maxLines)) {
            currentCoroutineContext().ensureActive()
            val lineIndices = IntArray(batch.size) { members[batch[it]] }
            val input = buildBatch(page, lines, lineIndices, window)
            val result = model.run(batchFeeds(input), listOf("logits", "lengths"))
            val logitsTensor = result.getValue("logits")
            check(logitsTensor.shape.size == 3) { "Indra $modelName logits must be [B, T, C]." }
            val steps = logitsTensor.shape[1]
            val classes = logitsTensor.shape[2]
            val logits = logitsTensor.floats()
            val lengths = result.getValue("lengths").ints()
            lineIndices.forEachIndexed { b, line ->
                val ctc = IndraCtc.decode(logits, b * steps * classes, lengths[b].coerceIn(0, steps), classes)
                val text = when {
                    ctc.conf < IndraCtc.MIN_CONF -> ""
                    base -> vocab.decodeBase(ctc.ids)
                    else -> vocab.decodeGroup(ctc.ids)
                }
                readings[line] = Reading(text, ctc.conf, needsFlip = !base && IndraRtl.hasRtlLetters(text))
            }
        }
        return readings
    }

    private fun buildBatch(page: Mat, lines: List<Line>, members: IntArray, window: Int): BatchInput {
        val height = IndraGeometry.LINE_HEIGHT * window
        val widths = IntArray(members.size) { lines[members[it]].crop(window).outWidth }
        val paddedWidth = IndraGeometry.padTo8(widths.max())
        val ink = FloatArray(members.size * height * paddedWidth)
        members.forEachIndexed { b, index ->
            val line = lines[index]
            val crop = IndraImageOps.renderCrop(page, line.crop(window), line.dark, height)
            for (y in 0 until height) {
                System.arraycopy(crop, y * widths[b], ink, (b * height + y) * paddedWidth, widths[b])
            }
        }
        return BatchInput(ink, widths, paddedWidth, height)
    }

    private fun batchFeeds(input: BatchInput): List<TensorFeed> = listOf(
        floatFeed("x", intArrayOf(input.widths.size, input.height, input.paddedWidth, 1), input.ink),
        intFeed("widths", intArrayOf(input.widths.size), input.widths),
    )

    private fun IndraBox.normalized(width: Int, height: Int): TextBox = TextBox(
        left = (x0 / width).coerceIn(0f, 1f),
        top = (y0 / height).coerceIn(0f, 1f),
        right = (x1 / width).coerceIn(0f, 1f),
        bottom = (y1 / height).coerceIn(0f, 1f),
    )

    private fun decodeForRecognition(path: String): Bitmap? {
        if (!File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longEdge / sampleSize > MAX_RECOGNITION_EDGE_PX) sampleSize *= 2
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun exifRotation(path: String): Int = runCatching {
        ExifInterface(path).rotationDegrees
    }.getOrDefault(0)

    private companion object {
        const val TAG = "IndraOcr"
        const val MAX_RECOGNITION_EDGE_PX = 3_200
        const val ORIENT_MIN = 0.95f
        const val ROUTE_SURE = 0.95f
        const val SCRIPT_GROUPS = 4
        const val GROUP_BASE = 0
        const val GROUP_CJK = 1
        val GROUP_MODELS = mapOf(
            GROUP_CJK to ("rec_cjk" to "cjk"),
            2 to ("rec_rtl" to "rtl"),
            3 to ("rec_world" to "world"),
        )
    }
}
