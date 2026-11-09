package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.core.processing.filter.PageBackgroundModel
import `in`.c1ph3rj.scanly.core.processing.filter.PageColorLooks
import `in`.c1ph3rj.scanly.core.processing.filter.PageFilterOperators
import `in`.c1ph3rj.scanly.core.processing.filter.PageImageAnalyzer
import `in`.c1ph3rj.scanly.data.text.indra.IndraBox
import `in`.c1ph3rj.scanly.data.text.indra.IndraGeometry
import `in`.c1ph3rj.scanly.data.text.indra.IndraImageOps
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.Random
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * DEV HARNESS (not a regression test): records pixel-exact fingerprints and timings of every
 * OpenCV code path in the app so two OpenCV builds (e.g. the official 5.0.0.1 AAR vs. a slim
 * self-built one) can be compared on the same phone. It never asserts on values, it only fails if
 * a case throws (after all cases have run and been logged).
 *
 * It is skipped unless the `opencvParity` instrumentation argument is `true`, so normal
 * `connectedDebugAndroidTest` runs stay fast. How to run (use the same device for both builds):
 *
 * ```
 * ./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=in.c1ph3rj.scanly.core.processing.OpenCvParityHarnessTest -Pandroid.testInstrumentationRunnerArguments.opencvParity=true
 * adb logcat -d -s OcvParity > run_<build>.txt
 * ```
 *
 * The Gradle task clears the app's data (ANDROIDX_TEST_ORCHESTRATOR with `clearPackageData`). To
 * keep it, install the debug and test APKs with `adb install -r` and start the run with
 * `adb shell am instrument -w -e opencvParity true -e class <this class> in.c1ph3rj.scanly.test/androidx.test.runner.AndroidJUnitRunner`.
 *
 * Clear logcat first (`adb logcat -c`) so only one run is captured. No files survive an
 * orchestrated run, which is why results are written to logcat. Log lines are tab separated and
 * each is kept under ~1000 chars:
 *
 * ```
 * OcvParity<TAB>HASH<TAB><case-id><TAB><sha256><TAB><summary>
 * OcvParity<TAB>TIME<TAB><case-id><TAB><medianMs><TAB><minMs>
 * OcvParity<TAB>INFO<TAB>...   (device, OpenCV version/build info; expected to differ between builds)
 * OcvParity<TAB>ERROR<TAB><case-id><TAB><exception>
 * OcvParity<TAB>DONE<TAB><number of HASH lines>
 * ```
 *
 * Comparing two runs: extract the HASH lines of each run, drop the logcat prefix, sort and diff.
 * Identical HASH columns mean bit-identical outputs. A differing HASH with similar `summary`
 * means tiny numeric drift; a differing summary points at real behaviour change. A filter case
 * whose summary starts with `ORIGINAL` for a non-ORIGINAL preset means the render threw inside the
 * processor (it swallows exceptions and falls back to the unfiltered page), which is a regression
 * (for example a missing native function). Compare TIME lines (median / min ms) for performance.
 *
 * Expected size: 344 HASH lines and 18 TIME lines; runtime about 3 minutes on a Galaxy S20 FE.
 * The official OpenCV build gives identical HASH lines run after run on the same phone.
 *
 * Covered: OpenCvPageFilterProcessor.analyze / apply / applyWithResolvedPreset / applyAll (every
 * PageFilterPreset, intensities 0.0, 1.0, 1.5, shared and screen-likely profiles),
 * PageFilterAdjustmentsApplier.apply, SignatureInkExtractor.extract / maskFromTransparent /
 * hasMeaningfulAlpha, PageImageAnalyzer.analyze, PageColorLooks.measure / levelsTables / sCurve,
 * PageBackgroundModel.estimate / normalizeChannel, PageFilterOperators (toneCurve, applyLut,
 * vibrance, sharpen, binarize, despeckle), and IndraImageOps (grayFromBitmap, rotateClockwise,
 * turn, preview, resize, estimateSkew, rotatePage, orientInput, detInput, histogram, renderCrop).
 *
 * Not called directly (reached through the presets above, building their inputs is impractical):
 * PageColorLooks.renderScreen / renderVivid (need PageFilterRenderParams from PageFilterLook),
 * PageFilterEngine.render (internal pipeline behind apply), IndraPageTextRecognizer (needs the
 * on-device OCR models).
 */
@RunWith(AndroidJUnit4::class)
class OpenCvParityHarnessTest {

    private var hashCount = 0
    private var timeCount = 0
    private val errors = ArrayList<String>()

    @Test
    fun recordParityAndPerformance() {
        assumeTrue(
            "OpenCV parity harness runs only with -e opencvParity true",
            InstrumentationRegistry.getArguments().getString("opencvParity") == "true",
        )
        OpenCvPageFilterProcessor.ensureInitialized()
        IndraImageOps.ensureInitialized()
        logInfo()

        val extractor = SignatureInkExtractor()
        val sigPaper = signatureOnPaper(1_400, 700)
        val sigAlpha = signatureOnTransparent(1_000, 500)
        try {
            for (spec in pageSpecs()) {
                runPage(spec)
            }
            runSignatures(extractor, sigPaper, sigAlpha)
            val skewed = skewedTextPage(1_200, 1_600)
            try {
                runIndra("text_skewed", skewed)
            } finally {
                skewed.recycle()
            }
            runPerformance(extractor, sigPaper)
        } finally {
            sigPaper.recycle()
            sigAlpha.recycle()
        }

        log("INFO", "counts", "hash=$hashCount time=$timeCount errors=${errors.size}", "")
        log("DONE", hashCount.toString())
        check(errors.isEmpty()) { "${errors.size} harness case(s) threw: ${errors.joinToString()}" }
    }

    // ---- per page -----------------------------------------------------------------------------

    private fun runPage(spec: PageSpec) {
        val page = spec.build()
        try {
            val id = spec.id
            hashCase("analyze/$id") { it.text(OpenCvPageFilterProcessor.analyze(page).toString()) }
            val profile = try {
                OpenCvPageFilterProcessor.analyze(page)
            } catch (t: Throwable) {
                recordError("analyze-profile/$id", t)
                null
            }

            for (preset in PageFilterPreset.entries) {
                for (intensity in INTENSITIES) {
                    hashCase("filter/${preset.name}/i$intensity/$id") { h ->
                        hashApplied(h, page, preset, null, intensity)
                    }
                }
            }
            if (profile != null) {
                hashCase("filter/MAGIC_COLOR/i1.0/profile/$id") { h ->
                    hashApplied(h, page, PageFilterPreset.MAGIC_COLOR, profile, 1f)
                }
                hashCase("filter/AUTO/i1.0/profile/$id") { h ->
                    hashApplied(h, page, PageFilterPreset.AUTO, profile, 1f)
                }
                hashCase("filter/AUTO/i1.0/profileScreenLikely/$id") { h ->
                    hashApplied(h, page, PageFilterPreset.AUTO, profile.copy(screenLikely = true), 1f)
                }
                hashCase("filter/SCREEN/i1.0/profileScreenLikely/$id") { h ->
                    hashApplied(h, page, PageFilterPreset.SCREEN, profile.copy(screenLikely = true), 1f)
                }
            }

            if (id in APPLY_ALL_PAGES) {
                val all = try {
                    OpenCvPageFilterProcessor.applyAll(page)
                } catch (t: Throwable) {
                    recordError("applyAll/$id", t)
                    null
                }
                all?.forEach { (preset, bitmap) ->
                    try {
                        emitHash("applyAll/${preset.name}/$id", Hasher().bitmap(bitmap))
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            if (id in ADJUSTMENT_PAGES) {
                for ((name, adjustments) in ADJUSTMENT_CASES) {
                    hashCase("adjust/$name/$id") { h ->
                        val out = PageFilterAdjustmentsApplier.apply(page, adjustments)
                        try {
                            h.bitmap(out)
                        } finally {
                            out.recycle()
                        }
                    }
                }
            }

            hashCase("signature/hasMeaningfulAlpha/$id") { h ->
                h.text(SignatureInkExtractor().hasMeaningfulAlpha(page).toString())
            }

            if (id in LOW_LEVEL_PAGES) runLowLevel(id, page)
            if (id in INDRA_PAGES) runIndra(id, page)
        } finally {
            page.recycle()
        }
    }

    private fun hashApplied(
        h: Hasher,
        page: Bitmap,
        preset: PageFilterPreset,
        profile: PageImageProfile?,
        intensity: Float,
    ) {
        val applied = OpenCvPageFilterProcessor.applyWithResolvedPreset(page, preset, profile, intensity)
        try {
            h.text(applied.appliedPreset.name)
            h.bitmap(applied.bitmap)
            h.summary.insert(0, "${applied.appliedPreset.name} ")
        } finally {
            applied.bitmap.recycle()
        }
    }

    /** Direct calls into the internal filter building blocks. */
    private fun runLowLevel(id: String, page: Bitmap) {
        OpenCvPageFilterProcessor.ensureInitialized()
        val rgba = Mat()
        val rgb = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(page, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)

            hashCase("lowlevel/rgbaToRgb/$id") { it.mat(rgb) }
            hashCase("lowlevel/rgbToGray/$id") { it.mat(gray) }
            hashCase("lowlevel/PageImageAnalyzer.analyze/$id") { h ->
                val aspect = maxOf(page.width, page.height).toDouble() / minOf(page.width, page.height)
                h.text(PageImageAnalyzer.analyze(sourceRgba = rgba, sourceAspectRatio = aspect).toString())
            }
            hashCase("lowlevel/PageColorLooks.measure/$id") { h ->
                val stats = PageColorLooks.measure(rgb)
                h.doubles(stats.white)
                h.doubles(doubleArrayOf(stats.darkLuminance, stats.medianLuminance))
                h.summary.append(" dark=${stats.darkLuminance} median=${stats.medianLuminance}")
            }
            hashCase("lowlevel/PageColorLooks.levelsTables/$id") { h ->
                val stats = PageColorLooks.measure(rgb)
                for ((balance, clip) in listOf(0.0 to 0.0, 0.5 to 0.8, 1.0 to 1.0)) {
                    PageColorLooks.levelsTables(stats, balance, clip).forEach { h.bytes(it) }
                }
            }
            hashCase("lowlevel/PageColorLooks.sCurve/$id") { h ->
                for (strength in listOf(0.0, 0.3, 0.8)) h.bytes(PageColorLooks.sCurve(strength))
            }

            for (stroke in listOf(0.05, 0.02)) {
                hashCase("lowlevel/PageBackgroundModel.estimate/s$stroke/$id") { h ->
                    val model = PageBackgroundModel.estimate(rgb, stroke)
                    try {
                        for (channel in 0..2) h.mat(model.channel(channel))
                        h.doubles(model.paperReference)
                        h.doubles(doubleArrayOf(model.paperRatio, model.paperReferenceGray))
                        val bg = model.grayBackground()
                        try {
                            h.mat(bg)
                        } finally {
                            bg.release()
                        }
                    } finally {
                        model.release()
                    }
                }
            }
            for (sigma in listOf(1.0, 0.0)) {
                hashCase("lowlevel/PageBackgroundModel.normalizeChannel/sigma$sigma/$id") { h ->
                    val model = PageBackgroundModel.estimate(rgb, 0.05)
                    val bg = model.grayBackground()
                    val out = Mat()
                    try {
                        PageBackgroundModel.normalizeChannel(
                            source = gray,
                            backgroundSmall = bg,
                            reference = model.paperReferenceGray,
                            target = 250.0,
                            maxGain = 4.0,
                            denoiseSigma = sigma,
                            output = out,
                        )
                        h.mat(out)
                    } finally {
                        out.release()
                        bg.release()
                        model.release()
                    }
                }
            }

            hashCase("lowlevel/PageFilterOperators.toneCurve+applyLut/$id") { h ->
                val table = PageFilterOperators.toneCurve(blackPoint = 20.0, whitePoint = 235.0, gamma = 1.4)
                h.bytes(table)
                val outGray = Mat()
                val outRgb = Mat()
                try {
                    PageFilterOperators.applyLut(gray, outGray, table)
                    PageFilterOperators.applyLut(rgb, outRgb, table)
                    h.mat(outGray)
                    h.mat(outRgb)
                } finally {
                    outGray.release()
                    outRgb.release()
                }
            }
            for ((neutralize, amount) in listOf(true to 0.5, false to 0.5, false to 0.0)) {
                hashCase("lowlevel/PageFilterOperators.vibrance/n$neutralize/a$amount/$id") { h ->
                    val out = Mat()
                    try {
                        PageFilterOperators.vibrance(rgb, out, amount, neutralize)
                        h.mat(out)
                    } finally {
                        out.release()
                    }
                }
            }
            hashCase("lowlevel/PageFilterOperators.sharpen/$id") { h ->
                val outRgb = Mat()
                val outGray = Mat()
                try {
                    PageFilterOperators.sharpen(rgb, outRgb, amount = 0.8, sigma = 1.2)
                    PageFilterOperators.sharpen(gray, outGray, amount = 0.0, sigma = 1.2)
                    h.mat(outRgb)
                    h.mat(outGray)
                } finally {
                    outRgb.release()
                    outGray.release()
                }
            }
            hashCase("lowlevel/PageFilterOperators.binarize+despeckle/$id") { h ->
                val out = Mat()
                try {
                    PageFilterOperators.binarize(gray, out, threshold = 140.0, ramp = 10.0)
                    h.mat(out)
                    PageFilterOperators.despeckle(out, 12)
                    h.mat(out)
                } finally {
                    out.release()
                }
            }
        } finally {
            rgba.release()
            rgb.release()
            gray.release()
        }
    }

    // ---- signatures ---------------------------------------------------------------------------

    private fun runSignatures(extractor: SignatureInkExtractor, sigPaper: Bitmap, sigAlpha: Bitmap) {
        val noisy = noisyLowContrastPage(800, 1_100)
        try {
            val inputs = listOf("sig_paper" to sigPaper, "sig_transparent" to sigAlpha, "page_noisy" to noisy)
            for ((name, bitmap) in inputs) {
                for (sensitivity in listOf(0.2f, 0.5f, 0.8f)) {
                    hashCase("signature/extract/s$sensitivity/$name") { h ->
                        val out = extractor.extract(bitmap, sensitivity)
                        try {
                            h.bitmap(out)
                        } finally {
                            out?.recycle()
                        }
                    }
                }
                hashCase("signature/maskFromTransparent/$name") { h ->
                    val out = extractor.maskFromTransparent(bitmap)
                    try {
                        h.bitmap(out)
                    } finally {
                        out?.recycle()
                    }
                }
                hashCase("signature/hasMeaningfulAlpha/$name") { it.text(extractor.hasMeaningfulAlpha(bitmap).toString()) }
            }
            hashCase("signature/extract/s0.5/edge300/sig_paper") { h ->
                val out = extractor.extract(sigPaper, 0.5f, maxOutputEdge = 300)
                try {
                    h.bitmap(out)
                } finally {
                    out?.recycle()
                }
            }
            hashCase("signature/maskFromTransparent/edge300/sig_transparent") { h ->
                val out = extractor.maskFromTransparent(sigAlpha, maxOutputEdge = 300)
                try {
                    h.bitmap(out)
                } finally {
                    out?.recycle()
                }
            }
        } finally {
            noisy.recycle()
        }
    }

    // ---- Indra --------------------------------------------------------------------------------

    private fun runIndra(id: String, bitmap: Bitmap) {
        val gray = IndraImageOps.grayFromBitmap(bitmap)
        try {
            val w = gray.cols()
            val h = gray.rows()
            hashCase("indra/grayFromBitmap/$id") { it.mat(gray) }

            for (degrees in listOf(0, 90, 180, 270, -90)) {
                hashCase("indra/rotateClockwise/$degrees/$id") { hasher ->
                    val out = IndraImageOps.rotateClockwise(gray.clone(), degrees)
                    try {
                        hasher.mat(out)
                    } finally {
                        out.release()
                    }
                }
            }
            for (turns in 0..3) {
                hashCase("indra/turn/$turns/$id") { hasher ->
                    val out = IndraImageOps.turn(gray, turns)
                    try {
                        hasher.mat(out)
                    } finally {
                        out.release()
                    }
                }
            }
            hashCase("indra/preview/$id") { hasher ->
                val out = IndraImageOps.preview(gray)
                try {
                    hasher.mat(out)
                } finally {
                    out.release()
                }
            }
            val resizes = listOf(
                Triple("area_down", (w * 0.4).toInt() to (h * 0.4).toInt(), Imgproc.INTER_AREA),
                Triple("linear_down", (w * 0.4).toInt() to (h * 0.4).toInt(), Imgproc.INTER_LINEAR),
                Triple("linear_up", (w * 1.25).toInt() to (h * 1.25).toInt(), Imgproc.INTER_LINEAR),
                Triple("area_odd", 333 to 517, Imgproc.INTER_AREA),
            )
            for ((name, size, interpolation) in resizes) {
                hashCase("indra/resize/$name/$id") { hasher ->
                    val out = IndraImageOps.resize(gray, size.first, size.second, interpolation)
                    try {
                        hasher.mat(out)
                    } finally {
                        out.release()
                    }
                }
            }

            val small = IndraImageOps.preview(gray)
            try {
                hashCase("indra/estimateSkew/$id") { hasher ->
                    hasher.number(IndraImageOps.estimateSkew(small))
                }
                hashCase("indra/orientInput/$id") { hasher -> hasher.floats(IndraImageOps.orientInput(small)) }
            } finally {
                small.release()
            }
            for (angle in listOf(-2.5, 7.3)) {
                hashCase("indra/rotatePage/a$angle/$id") { hasher ->
                    val out = IndraImageOps.rotatePage(gray, angle)
                    try {
                        hasher.mat(out)
                    } finally {
                        out.release()
                    }
                }
            }
            for (longSide in listOf(960, 2_400)) {
                hashCase("indra/detInput/l$longSide/$id") { hasher ->
                    val input = IndraImageOps.detInput(gray, longSide)
                    hasher.floats(input.ink)
                    hasher.ints(intArrayOf(input.paddedHeight, input.paddedWidth))
                    hasher.number(input.scale)
                }
            }

            val wf = w.toFloat()
            val hf = h.toFloat()
            val boxes = listOf(
                "box1" to IndraBox(wf * 0.08f, hf * 0.06f, wf * 0.62f, hf * 0.078f),
                "boxEdge" to IndraBox(-wf * 0.02f, hf * 0.5f, wf * 0.3f, hf * 0.52f),
            )
            hashCase("indra/histogram/full/$id") { it.ints(IndraImageOps.histogram(gray, IndraBox(0f, 0f, wf, hf))) }
            hashCase("indra/histogram/outside/$id") {
                it.ints(IndraImageOps.histogram(gray, IndraBox(wf + 10f, hf + 10f, wf + 50f, hf + 40f)))
            }
            for ((name, box) in boxes) {
                hashCase("indra/histogram/$name/$id") { it.ints(IndraImageOps.histogram(gray, box)) }
                for (window in listOf(1, 3)) {
                    val geometry = IndraGeometry.cropGeometry(box, window)
                    for (dark in listOf(false, true)) {
                        hashCase("indra/renderCrop/w$window/${if (dark) "dark" else "light"}/$name/$id") { hasher ->
                            hasher.floats(IndraImageOps.renderCrop(gray, geometry, dark, IndraGeometry.LINE_HEIGHT * window))
                            hasher.text(geometry.toString())
                        }
                    }
                }
            }
        } finally {
            gray.release()
        }
    }

    // ---- performance --------------------------------------------------------------------------

    private fun runPerformance(extractor: SignatureInkExtractor, sigPaper: Bitmap) {
        val page = shadedPaperPage(1_600, 2_200, colorMarks = false)
        val skewed = skewedTextPage(1_200, 1_600)
        val gray = IndraImageOps.grayFromBitmap(page)
        val skewGray = IndraImageOps.grayFromBitmap(skewed)
        val skewSmall = IndraImageOps.preview(skewGray)
        val all = PageFilterAdjustments(0.2f, 0.3f, -0.3f, 0.5f)
        try {
            for (preset in PageFilterPreset.entries) {
                timeCase("filter/${preset.name}/i1.0/page_shaded") {
                    OpenCvPageFilterProcessor.apply(page, preset, null, 1f).recycle()
                }
            }
            timeCase("analyze/page_shaded") { OpenCvPageFilterProcessor.analyze(page) }
            timeCase("adjust/all/page_shaded") { PageFilterAdjustmentsApplier.apply(page, all).recycle() }
            timeCase("signature/extract/s0.5/sig_paper") { extractor.extract(sigPaper, 0.5f)?.recycle() }
            timeCase("indra/grayFromBitmap/page_shaded") { IndraImageOps.grayFromBitmap(page).release() }
            timeCase("indra/estimateSkew/text_skewed") { IndraImageOps.estimateSkew(skewSmall) }
            timeCase("indra/detInput/l960/page_shaded") { IndraImageOps.detInput(gray, 960) }
            timeCase("indra/orientInput/text_skewed") { IndraImageOps.orientInput(skewSmall) }
            timeCase("indra/rotatePage/a-2.5/page_shaded") { IndraImageOps.rotatePage(gray, -2.5).release() }
        } finally {
            skewSmall.release()
            skewGray.release()
            gray.release()
            skewed.recycle()
            page.recycle()
        }
    }

    private fun timeCase(id: String, block: () -> Unit) {
        try {
            repeat(WARMUPS) { block() }
            val samples = DoubleArray(TIMED_RUNS)
            for (i in samples.indices) {
                val start = System.nanoTime()
                block()
                samples[i] = (System.nanoTime() - start) / 1_000_000.0
            }
            samples.sort()
            val median = samples[samples.size / 2]
            log("TIME", id, fmt(median), fmt(samples[0]))
            timeCount++
        } catch (t: Throwable) {
            recordError("time:$id", t)
        }
    }

    // ---- logging ------------------------------------------------------------------------------

    private fun hashCase(id: String, block: (Hasher) -> Unit) {
        try {
            val hasher = Hasher()
            block(hasher)
            emitHash(id, hasher)
        } catch (t: Throwable) {
            recordError(id, t)
        }
    }

    private fun emitHash(id: String, hasher: Hasher) {
        log("HASH", id, hasher.sha(), hasher.summary.toString())
        hashCount++
    }

    private fun recordError(id: String, t: Throwable) {
        errors += id
        Log.e(TAG, "$TAG\tERROR\t$id\t${t.javaClass.simpleName}: ${flat(t.message ?: "")}".take(MAX_LINE), t)
    }

    private fun logInfo() {
        log("INFO", "device", "${Build.MANUFACTURER} ${Build.MODEL}", "sdk=${Build.VERSION.SDK_INT} abis=${Build.SUPPORTED_ABIS.joinToString(",")}")
        val buildInfo = try {
            Core.getBuildInformation()
        } catch (t: Throwable) {
            recordError("info/buildInformation", t)
            ""
        }
        log("INFO", "opencv", Core.VERSION, flat(buildInfo).take(300))
        log("INFO", "opencv.buildInformation.sha256", sha256Hex(buildInfo.toByteArray(Charsets.UTF_8)), "length=${buildInfo.length}")
    }

    private fun log(kind: String, vararg fields: String) {
        val line = buildString {
            append(TAG)
            append('\t')
            append(kind)
            for (field in fields) {
                append('\t')
                append(flat(field))
            }
        }
        Log.i(TAG, line.take(MAX_LINE))
    }

    private fun flat(value: String): String = value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ')

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    // ---- hashing ------------------------------------------------------------------------------

    private class Hasher {
        private val digest = MessageDigest.getInstance("SHA-256")
        val summary = StringBuilder()

        fun sha(): String = hex(digest.digest())

        fun text(value: String): Hasher {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            if (summary.length < SUMMARY_LIMIT) {
                if (summary.isNotEmpty()) summary.append("; ")
                summary.append(value.take(SUMMARY_LIMIT))
            }
            return this
        }

        fun number(value: Double): Hasher {
            digest.update("double:${java.lang.Double.doubleToRawLongBits(value)}".toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            summary.append(if (summary.isEmpty()) "" else "; ").append("value=").append(value)
            return this
        }

        fun bytes(values: ByteArray): Hasher {
            digest.update("bytes:${values.size}".toByteArray(Charsets.UTF_8))
            digest.update(values)
            var sum = 0L
            for (b in values) sum += b.toInt() and 0xFF
            note("bytes n=${values.size} mean=${meanOf(sum.toDouble(), values.size)}")
            return this
        }

        fun ints(values: IntArray): Hasher {
            digest.update("ints:${values.size}".toByteArray(Charsets.UTF_8))
            val buffer = ByteBuffer.allocate(values.size * 4)
            buffer.asIntBuffer().put(values)
            digest.update(buffer.array())
            var sum = 0.0
            for (v in values) sum += v
            note("ints n=${values.size} mean=${meanOf(sum, values.size)}")
            return this
        }

        fun floats(values: FloatArray): Hasher {
            digest.update("floats:${values.size}".toByteArray(Charsets.UTF_8))
            val buffer = ByteBuffer.allocate(values.size * 4)
            buffer.asFloatBuffer().put(values)
            digest.update(buffer.array())
            var sum = 0.0
            for (v in values) sum += v
            note("floats n=${values.size} mean=${meanOf(sum, values.size)}")
            return this
        }

        fun doubles(values: DoubleArray): Hasher {
            digest.update("doubles:${values.size}".toByteArray(Charsets.UTF_8))
            val buffer = ByteBuffer.allocate(values.size * 8)
            buffer.asDoubleBuffer().put(values)
            digest.update(buffer.array())
            var sum = 0.0
            for (v in values) sum += v
            note("doubles n=${values.size} mean=${meanOf(sum, values.size)}")
            return this
        }

        fun bitmap(bitmap: Bitmap?): Hasher {
            if (bitmap == null) {
                digest.update("bitmap:null".toByteArray(Charsets.UTF_8))
                note("bitmap null")
                return this
            }
            val size = bitmap.byteCount
            val buffer = ByteBuffer.allocate(size)
            bitmap.copyPixelsToBuffer(buffer)
            val pixels = buffer.array()
            digest.update("bitmap:${bitmap.width}x${bitmap.height}:${bitmap.config}:$size".toByteArray(Charsets.UTF_8))
            digest.update(pixels)
            var sum = 0L
            for (b in pixels) sum += b.toInt() and 0xFF
            note("bitmap ${bitmap.width}x${bitmap.height} ${bitmap.config} mean=${meanOf(sum.toDouble(), size)}")
            return this
        }

        fun mat(mat: Mat): Hasher {
            val source = if (mat.isContinuous) mat else mat.clone()
            try {
                val type = source.type()
                val count = (source.total() * source.channels()).toInt()
                digest.update("mat:${source.rows()}x${source.cols()}:type$type".toByteArray(Charsets.UTF_8))
                when (val depth = CvType.depth(type)) {
                    CvType.CV_8U, CvType.CV_8S -> bytes(ByteArray(count).also { source.get(0, 0, it) })
                    CvType.CV_32S -> ints(IntArray(count).also { source.get(0, 0, it) })
                    CvType.CV_32F -> floats(FloatArray(count).also { source.get(0, 0, it) })
                    CvType.CV_64F -> doubles(DoubleArray(count).also { source.get(0, 0, it) })
                    else -> error("Unsupported Mat depth $depth")
                }
                summary.insert(0, "mat ${source.rows()}x${source.cols()}x${source.channels()} ")
            } finally {
                if (source !== mat) source.release()
            }
            return this
        }

        private fun note(value: String) {
            if (summary.length >= SUMMARY_LIMIT) return
            if (summary.isNotEmpty()) summary.append("; ")
            summary.append(value)
        }

        private fun meanOf(sum: Double, count: Int): String =
            String.format(Locale.US, "%.5f", if (count == 0) 0.0 else sum / count)

        private companion object {
            const val SUMMARY_LIMIT = 600
        }
    }

    // ---- synthetic inputs ---------------------------------------------------------------------

    private class PageSpec(val id: String, val build: () -> Bitmap)

    private fun pageSpecs(): List<PageSpec> = listOf(
        PageSpec("page_shaded") { shadedPaperPage(1_600, 2_200, colorMarks = false) },
        PageSpec("page_stamps") { shadedPaperPage(1_600, 2_200, colorMarks = true) },
        PageSpec("page_dark") { darkScreenPage(1_200, 1_650) },
        PageSpec("page_noisy") { noisyLowContrastPage(800, 1_100) },
    )

    private fun paperPixels(width: Int, height: Int, shadowAmount: Double): IntArray {
        val pixels = IntArray(width * height)
        val xSpan = (width - 1).coerceAtLeast(1).toDouble()
        val ySpan = (height - 1).coerceAtLeast(1).toDouble()
        for (y in 0 until height) {
            val vignette = 10.0 * (y / ySpan)
            for (x in 0 until width) {
                val shadow = shadowAmount * (x / xSpan)
                val texture = (((x * 17) + (y * 31)) % 5) - 2
                val base = (232.0 - shadow - vignette + texture).toInt()
                pixels[(y * width) + x] = Color.rgb(
                    (base + 10).coerceIn(0, 255),
                    (base + 2).coerceIn(0, 255),
                    (base - 9).coerceIn(0, 255),
                )
            }
        }
        return pixels
    }

    private fun bitmapFromPixels(width: Int, height: Int, pixels: IntArray): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
            textSize = size
            this.color = color
        }

    private fun drawTextBlock(
        canvas: Canvas,
        paint: Paint,
        left: Float,
        top: Float,
        step: Float,
        count: Int,
        colors: IntArray? = null,
    ) {
        for (line in 0 until count) {
            if (colors != null) paint.color = colors[line % colors.size]
            paint.alpha = if (line % 7 == 6) 170 else 255
            canvas.drawText(CORPUS[line % CORPUS.size], left, top + (line * step), paint)
        }
    }

    private fun shadedPaperPage(width: Int, height: Int, colorMarks: Boolean): Bitmap {
        val bitmap = bitmapFromPixels(width, height, paperPixels(width, height, shadowAmount = 38.0))
        val canvas = Canvas(bitmap)
        val size = width / 42f
        val ink = Color.rgb(35, 39, 43)
        canvas.drawText("SCANLY DOCUMENT", width * 0.06f, height * 0.07f, textPaint(ink, size * 1.8f, bold = true))
        val step = size * 1.9f
        val count = ((height * 0.80f) / step).toInt()
        val highlightTop = height * 0.12f + (step * 4) - size
        drawTextBlock(canvas, textPaint(ink, size), width * 0.06f, height * 0.12f, step, count)
        if (colorMarks) {
            val s = width / 1_600f
            canvas.drawRect(120f * s, highlightTop, 1_100f * s, highlightTop + (size * 1.4f), Paint().apply {
                color = Color.argb(110, 255, 235, 59)
            })
            canvas.drawRect(220f * s, 900f * s, 520f * s, 1_020f * s, Paint().apply { color = Color.rgb(190, 45, 48) })
            canvas.drawText("PAID", 262f * s, 985f * s, textPaint(Color.WHITE, 80f * s, bold = true))
            canvas.drawCircle(900f * s, 980f * s, 90f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(34, 98, 184)
                style = Paint.Style.STROKE
                strokeWidth = 10f * s
            })
            canvas.drawRect(1_250f * s, 200f * s, 1_450f * s, 260f * s, Paint().apply { color = Color.rgb(160, 40, 150) })
            val pen = Path().apply {
                moveTo(1_100f * s, 1_500f * s)
                cubicTo(1_180f * s, 1_380f * s, 1_260f * s, 1_620f * s, 1_340f * s, 1_480f * s)
                cubicTo(1_400f * s, 1_380f * s, 1_460f * s, 1_560f * s, 1_500f * s, 1_450f * s)
            }
            canvas.drawPath(pen, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 140, 70)
                style = Paint.Style.STROKE
                strokeWidth = 7f * s
                strokeCap = Paint.Cap.ROUND
            })
        }
        return bitmap
    }

    private fun darkScreenPage(width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val base = 24 + ((6 * y) / height)
            for (x in 0 until width) {
                val phase = x % 3
                pixels[(y * width) + x] = Color.rgb(
                    base + if (phase == 0) 8 else 0,
                    base + if (phase == 1) 8 else 0,
                    base + if (phase == 2) 8 else 0,
                )
            }
        }
        val bitmap = bitmapFromPixels(width, height, pixels)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.06f, Paint().apply { color = Color.rgb(38, 42, 50) })
        val size = width / 42f
        val colors = intArrayOf(
            Color.rgb(214, 220, 228),
            Color.rgb(86, 182, 194),
            Color.rgb(209, 154, 102),
            Color.rgb(152, 195, 121),
        )
        canvas.drawText("SCREEN CAPTURE", width * 0.06f, height * 0.04f, textPaint(Color.rgb(230, 235, 240), size * 1.3f, bold = true))
        val step = size * 1.9f
        drawTextBlock(canvas, textPaint(colors[0], size), width * 0.06f, height * 0.11f, step, ((height * 0.78f) / step).toInt(), colors)
        val bar = Paint().apply { color = Color.rgb(200, 200, 200) }
        canvas.drawRect(width * 0.10f, height * 0.90f, width * 0.55f, height * 0.91f, bar)
        canvas.drawRect(width * 0.10f, height * 0.93f, width * 0.40f, height * 0.94f, bar)
        return bitmap
    }

    private fun noisyLowContrastPage(width: Int, height: Int): Bitmap {
        val random = Random(20_240_607L)
        val pixels = IntArray(width * height)
        val ySpan = (height - 1).coerceAtLeast(1).toDouble()
        for (y in 0 until height) {
            val base = (168.0 - (14.0 * (y / ySpan))).toInt()
            for (x in 0 until width) {
                val r = base + 6 + random.nextInt(29) - 14
                val g = base + random.nextInt(29) - 14
                val b = base - 7 + random.nextInt(29) - 14
                pixels[(y * width) + x] = Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
            }
        }
        val bitmap = bitmapFromPixels(width, height, pixels)
        val canvas = Canvas(bitmap)
        val size = width / 40f
        val step = size * 1.9f
        drawTextBlock(
            canvas,
            textPaint(Color.rgb(118, 122, 128), size),
            width * 0.06f,
            height * 0.08f,
            step,
            ((height * 0.84f) / step).toInt(),
        )
        return bitmap
    }

    /** Dark monospace text lines rotated 2.5 degrees about the page centre. */
    private fun skewedTextPage(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(244, 242, 236))
        canvas.save()
        canvas.rotate(2.5f, width / 2f, height / 2f)
        val size = width / 46f
        val step = size * 1.7f
        drawTextBlock(
            canvas,
            textPaint(Color.rgb(25, 28, 32), size),
            width * 0.08f,
            height * 0.1f,
            step,
            ((height * 0.82f) / step).toInt(),
        )
        canvas.restore()
        return bitmap
    }

    private fun signaturePath(width: Int, height: Int): Path {
        val w = width.toFloat()
        val h = height.toFloat()
        return Path().apply {
            moveTo(0.08f * w, 0.55f * h)
            cubicTo(0.15f * w, 0.10f * h, 0.25f * w, 0.90f * h, 0.32f * w, 0.45f * h)
            cubicTo(0.38f * w, 0.15f * h, 0.45f * w, 0.80f * h, 0.55f * w, 0.50f * h)
            cubicTo(0.60f * w, 0.30f * h, 0.70f * w, 0.75f * h, 0.82f * w, 0.35f * h)
            quadTo(0.90f * w, 0.15f * h, 0.93f * w, 0.50f * h)
            moveTo(0.06f * w, 0.78f * h)
            lineTo(0.90f * w, 0.72f * h)
        }
    }

    private fun signatureOnPaper(width: Int, height: Int): Bitmap {
        val bitmap = bitmapFromPixels(width, height, paperPixels(width, height, shadowAmount = 14.0))
        Canvas(bitmap).drawPath(signaturePath(width, height), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 30, 90)
            style = Paint.Style.STROKE
            strokeWidth = width * 0.006f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
        return bitmap
    }

    private fun signatureOnTransparent(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = signaturePath(width, height)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 10, 20, 80)
            style = Paint.Style.STROKE
            strokeWidth = width * 0.014f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(10, 20, 80)
            style = Paint.Style.STROKE
            strokeWidth = width * 0.006f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
        return bitmap
    }

    private companion object {
        const val TAG = "OcvParity"
        const val MAX_LINE = 950
        const val WARMUPS = 2
        const val TIMED_RUNS = 7

        val INTENSITIES = listOf(0.0f, 1.0f, 1.5f)

        val APPLY_ALL_PAGES = setOf("page_dark", "page_noisy")
        val ADJUSTMENT_PAGES = setOf("page_stamps", "page_noisy")
        val LOW_LEVEL_PAGES = setOf("page_stamps", "page_dark", "page_noisy")
        val INDRA_PAGES = setOf("page_shaded", "page_noisy")

        val ADJUSTMENT_CASES: List<Pair<String, PageFilterAdjustments>> = listOf(
            "default" to PageFilterAdjustments(),
            "brightness+0.3" to PageFilterAdjustments(brightness = 0.3f),
            "contrast-0.4" to PageFilterAdjustments(contrast = -0.4f),
            "saturation+0.5" to PageFilterAdjustments(saturation = 0.5f),
            "saturation-1.0" to PageFilterAdjustments(saturation = -1f),
            "sharpness0.7" to PageFilterAdjustments(sharpness = 0.7f),
            "all" to PageFilterAdjustments(brightness = 0.2f, contrast = 0.3f, saturation = -0.3f, sharpness = 0.5f),
            "extreme" to PageFilterAdjustments(brightness = 1f, contrast = 1f, saturation = 1f, sharpness = 1f),
        )

        val CORPUS = listOf(
            "The quick brown fox jumps over the lazy dog 0123456789",
            "Invoice 2026-0417  Total due: 1,284.50 USD",
            "Scanly keeps every page sharp, flat and readable.",
            "Shadows, creases and warm lamp light removed.",
            "Signature: ______________________  Date: ________",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz",
        )

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}
