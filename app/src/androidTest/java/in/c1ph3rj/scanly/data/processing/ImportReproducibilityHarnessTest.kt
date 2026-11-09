package `in`.c1ph3rj.scanly.data.processing

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.core.common.DefaultScanlyDispatchers
import `in`.c1ph3rj.scanly.core.ml.AutomaticDocumentModelSelector
import `in`.c1ph3rj.scanly.core.ml.BookAwareCornerResolver
import `in`.c1ph3rj.scanly.core.ml.LiteRtDocumentCornerDetector
import `in`.c1ph3rj.scanly.core.ml.LiteRtDocumentGateDetector
import `in`.c1ph3rj.scanly.data.settings.DefaultSettingsRepository
import `in`.c1ph3rj.scanly.data.storage.AppPrivateDocumentStorageManager
import `in`.c1ph3rj.scanly.data.storage.DocumentCoverArtFactory
import `in`.c1ph3rj.scanly.data.text.indra.IndraPageTextRecognizer
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEV HARNESS: re-runs capture finalize ([DefaultPageImageProcessor.processCapture], the import
 * path) on the raw photos of a document already on the phone, several rounds, with Indra OCR
 * running alongside like the background text indexer, and fails if any page's saved JPEG differs
 * between rounds. Uses the app's real detection settings; nothing in the document is modified
 * (outputs go to the cache directory).
 *
 * Skipped unless `importReproDocument` names a document id. The Gradle task clears app data, so
 * install the APKs and run it directly:
 *
 * ```
 * adb shell am instrument -w -e importReproDocument <document-id> [-e rounds 4] \
 *   -e class in.c1ph3rj.scanly.data.processing.ImportReproducibilityHarnessTest \
 *   in.c1ph3rj.scanly.test/androidx.test.runner.AndroidJUnitRunner
 * adb logcat -d -s ImportRepro
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ImportReproducibilityHarnessTest {

    @Test
    fun importingTheSamePhotosGivesIdenticalPages() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val documentId = arguments.getString("importReproDocument")
        assumeTrue("Runs only with -e importReproDocument <document-id>", !documentId.isNullOrBlank())
        val rounds = arguments.getString("rounds")?.toIntOrNull() ?: 4
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rawPages = File(context.filesDir, "documents/$documentId/raw").listFiles()
            ?.filter { it.extension == "jpg" }
            ?.sortedBy { it.name }
            .orEmpty()
        check(rawPages.isNotEmpty()) { "No raw pages for document $documentId." }

        val dispatchers = DefaultScanlyDispatchers()
        val detector = LiteRtDocumentCornerDetector(context, dispatchers)
        val processor = DefaultPageImageProcessor(
            bookAwareCornerResolver = BookAwareCornerResolver(detector),
            documentCornerDetector = detector,
            documentGateDetector = LiteRtDocumentGateDetector(context, dispatchers),
            automaticDocumentModelSelector = AutomaticDocumentModelSelector(detector, dispatchers),
            storageManager = AppPrivateDocumentStorageManager(context, DocumentCoverArtFactory(), dispatchers),
            settingsRepository = DefaultSettingsRepository(context, dispatchers),
            dispatchers = dispatchers,
        )
        val outputDir = File(context.cacheDir, "import_repro").apply { deleteRecursively(); mkdirs() }

        val stopOcr = AtomicBoolean(false)
        val ocr = IndraPageTextRecognizer(context, dispatchers)
        val ocrThread = Thread {
            runBlocking {
                var index = 0
                while (!stopOcr.get()) runCatching { ocr.recognize(rawPages[index++ % rawPages.size].path) }
                ocr.release()
            }
        }.apply { start() }

        val hashes = rawPages.associate { it.name to ArrayList<String>() }
        val millis = ArrayList<Long>()
        try {
            repeat(rounds) { round ->
                for (raw in rawPages) {
                    val processed = File(outputDir, "r${round}_${raw.name}")
                    val started = System.nanoTime()
                    val artifacts = processor.processCapture(
                        rawImagePath = raw.path,
                        processedImagePath = processed.path,
                        thumbnailPath = File(outputDir, "r${round}_thumb_${raw.name}").path,
                    )
                    millis += (System.nanoTime() - started) / 1_000_000
                    val hash = sha256(processed.readBytes())
                    hashes.getValue(raw.name) += "${artifacts.filterPreset} $hash"
                    Log.i(TAG, "round=$round ${raw.name} ${artifacts.filterPreset} ${hash.take(16)} quad=${artifacts.cropQuad}")
                }
            }
        } finally {
            stopOcr.set(true)
            ocrThread.join(60_000)
            outputDir.deleteRecursively()
        }

        val sorted = millis.sorted()
        Log.i(TAG, "processCapture ms: median=${sorted[sorted.size / 2]} min=${sorted.first()} max=${sorted.last()}")
        val differing = hashes.filterValues { it.toSet().size > 1 }
        differing.forEach { (page, results) ->
            Log.i(TAG, "DIFFERS $page: ${results.groupingBy { it }.eachCount().values}")
        }
        Log.i(TAG, "DONE pages=${rawPages.size} rounds=$rounds differing=${differing.keys}")
        check(differing.isEmpty()) { "Pages differed between imports: ${differing.keys}" }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "ImportRepro"
    }
}
