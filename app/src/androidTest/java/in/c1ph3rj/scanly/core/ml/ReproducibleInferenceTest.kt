package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.core.common.DefaultScanlyDispatchers
import `in`.c1ph3rj.scanly.core.processing.OpenCvPageFilterProcessor
import `in`.c1ph3rj.scanly.core.processing.PerspectiveBitmapTransform
import `in`.c1ph3rj.scanly.data.text.indra.IndraPageTextRecognizer
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Importing the same photo twice must save the same page, bit for bit.
 *
 * LiteRT picks a CPU kernel per core type, and big- and LITTLE-core kernels round differently, so
 * unpinned inference returns corners that differ in the last float bit from run to run. Those
 * corners drive the perspective warp, so about one page per 8-page import came out slightly
 * different (text edges, up to ~2% of pixels). The drift is far likelier while background OCR
 * keeps the cores busy and the scheduler moves threads around, so this test runs Indra OCR and
 * busy threads alongside.
 *
 * The still-image path (`reproducible = true`, see [CpuAffinity]) must be bit-identical. To see
 * the old behaviour fail, run with `-e reproducibleInference false`.
 */
@RunWith(AndroidJUnit4::class)
class ReproducibleInferenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dispatchers = DefaultScanlyDispatchers()
    private val reproducible =
        InstrumentationRegistry.getArguments().getString("reproducibleInference") != "false"
    private val stopLoad = AtomicBoolean(false)
    private val loadThreads = ArrayList<Thread>()
    private lateinit var photo: Bitmap

    @Before
    fun startBackgroundLoad() {
        photo = documentPhoto(1_600, 2_000)
        val ocrPage = File(context.cacheDir, "reproducible_inference_ocr.jpg")
        ocrPage.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val ocr = IndraPageTextRecognizer(context, dispatchers)
        loadThreads += Thread {
            runBlocking {
                while (!stopLoad.get()) runCatching { ocr.recognize(ocrPage.path) }
                ocr.release()
            }
        }
        repeat(BUSY_THREADS) {
            loadThreads += Thread {
                var sink = 0.0
                while (!stopLoad.get()) {
                    for (i in 0 until 50_000) sink += Math.sqrt(i.toDouble())
                }
                if (sink < 0) Log.d(TAG, "unreachable")
            }
        }
        loadThreads.forEach(Thread::start)
    }

    @After
    fun stopBackgroundLoad() {
        stopLoad.set(true)
        loadThreads.forEach { it.join(30_000) }
        photo.recycle()
    }

    @Test
    fun cornerModelsReturnIdenticalCornersOnEveryRun() = runBlocking {
        val detector = LiteRtDocumentCornerDetector(context, dispatchers)
        for (model in listOf(DocumentCornerModel.LITE, DocumentCornerModel.STANDARD, DocumentCornerModel.HIGH)) {
            val outputs = List(RUNS) {
                detector.detect(photo, model, reproducible).fingerprint()
            }
            assertAllEqual("$model corners", outputs)
        }
    }

    @Test
    fun documentGateReturnsIdenticalProbabilitiesOnEveryRun() = runBlocking {
        val gate = LiteRtDocumentGateDetector(context, dispatchers)
        val outputs = List(RUNS) {
            val probabilities = gate.classify(photo, reproducible).probabilities
            DocumentGateClass.entries.joinToString(",") { probabilities.getValue(it).toRawBits().toString(16) }
        }
        assertAllEqual("gate probabilities", outputs)
    }

    /** The import path from detection to the filtered page (the bytes the PDF export embeds). */
    @Test
    fun importPipelineRendersIdenticalPagesOnEveryRun() = runBlocking {
        val detector = LiteRtDocumentCornerDetector(context, dispatchers)
        val resolver = BookAwareCornerResolver(detector)
        val pages = List(PIPELINE_RUNS) {
            // Import resolves corners in STILL_PROCESS mode, which asks for reproducible inference;
            // the old-behaviour run calls the detector unpinned instead.
            val corners = if (reproducible) {
                resolver.detect(photo, DocumentCornerModel.STANDARD, QuadReadiness.STILL_PROCESS).result.quad
            } else {
                detector.detect(photo, DocumentCornerModel.STANDARD).quad
            } ?: error("The synthetic photo should yield a document quad.")
            val corrected = PerspectiveBitmapTransform.correct(photo, corners)
            val filtered = OpenCvPageFilterProcessor.applyWithResolvedPreset(corrected, PageFilterPreset.AUTO)
            corrected.recycle()
            "${filtered.appliedPreset} ${sha256(filtered.bitmap)}".also { filtered.bitmap.recycle() }
        }
        assertAllEqual("processed page", pages)
    }

    private fun assertAllEqual(what: String, outputs: List<String>) {
        val counts = outputs.groupingBy { it }.eachCount()
        Log.i(TAG, "$what: ${counts.size} distinct of ${outputs.size} (reproducible=$reproducible, pinned to ${CpuAffinity.uniformCoreMask?.toString(16)})")
        assertEquals(
            "$what differed between runs of the same input: ${counts.values.sortedDescending()}",
            1,
            counts.size,
        )
    }

    private fun CornerDetectionResult.fingerprint(): String =
        buildList {
            add(confidence)
            quad?.orderedPoints()?.forEach { (_, point) ->
                add(point.x)
                add(point.y)
            }
        }.joinToString(",") { it.toRawBits().toString(16) }

    private fun sha256(bitmap: Bitmap): String {
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        return MessageDigest.getInstance("SHA-256").digest(buffer.array())
            .joinToString("") { "%02x".format(it) }
    }

    /** A text page lying skewed on a dark desk, so the corner models find a real quad. */
    private fun documentPhoto(width: Int, height: Int): Bitmap {
        val pageWidth = 1_000
        val pageHeight = 1_400
        val page = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        Canvas(page).apply {
            drawColor(Color.rgb(238, 234, 222))
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(35, 39, 43)
                textSize = 30f
                typeface = Typeface.SERIF
            }
            drawText("QUARTERLY REPORT", 80f, 120f, Paint(ink).apply { textSize = 56f; isFakeBoldText = true })
            var y = 200f
            var line = 0
            while (y < pageHeight - 80f) {
                drawText("Line $line: the quick brown fox jumps over the lazy dog 0123456789", 80f, y, ink)
                y += 48f
                line++
            }
        }
        val photo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(photo).apply {
            drawColor(Color.rgb(54, 58, 63))
            val matrix = Matrix().apply {
                setPolyToPoly(
                    floatArrayOf(0f, 0f, pageWidth.toFloat(), 0f, pageWidth.toFloat(), pageHeight.toFloat(), 0f, pageHeight.toFloat()),
                    0,
                    floatArrayOf(250f, 230f, 1_380f, 300f, 1_440f, 1_800f, 170f, 1_720f),
                    0,
                    4,
                )
            }
            drawBitmap(page, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        page.recycle()
        return photo
    }

    private companion object {
        const val TAG = "ReproducibleInference"
        const val RUNS = 24
        const val PIPELINE_RUNS = 8
        const val BUSY_THREADS = 4
    }
}
