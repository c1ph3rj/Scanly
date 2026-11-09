package `in`.c1ph3rj.scanly.data.text

import `in`.c1ph3rj.scanly.data.text.indra.IndraUnavailableException
import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import `in`.c1ph3rj.scanly.domain.model.OcrFallbackReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectablePageTextRecognizerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mlKit = FakeRecognizer(OcrEngine.ML_KIT) { page("ml kit text", OcrEngine.ML_KIT) }
    private val indra = FakeRecognizer(OcrEngine.INDRA) { page("indra text", OcrEngine.INDRA) }
    private val source = FakeEngineSource(OcrEngine.INDRA)
    private val guard = RecordingGuard()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun recognizer(
        timeoutMillis: Long = 5_000L,
        idleMillis: Long = 60_000L,
    ) = SelectablePageTextRecognizer(
        mlKit = mlKit,
        indra = indra,
        engineSource = source,
        guard = guard,
        indraTimeoutMillis = timeoutMillis,
        idleReleaseMillis = idleMillis,
        scope = scope,
    )

    @Test
    fun mlKitSelected_neverTouchesIndraOrGuard() = runBlocking {
        source.engine = OcrEngine.ML_KIT

        val result = recognizer().recognize("a.jpg")

        assertEquals(OcrEngine.ML_KIT, result.engine)
        assertEquals(OcrEngine.ML_KIT, result.requestedEngine)
        assertNull(result.fallbackReason)
        assertEquals(0, indra.calls)
        assertTrue(guard.events.isEmpty())
    }

    @Test
    fun indraSuccess_isReportedAsIndraAndGuardIsBracketed() = runBlocking {
        val result = recognizer().recognize("a.jpg")

        assertEquals("indra text", result.text)
        assertEquals(OcrEngine.INDRA, result.engine)
        assertEquals(OcrEngine.INDRA, result.requestedEngine)
        assertNull(result.fallbackReason)
        assertEquals(0, mlKit.calls)
        assertEquals(listOf("mark:a.jpg", "clear"), guard.events)
    }

    @Test
    fun indraThrows_fallsBackToMlKitWithFailed() = runBlocking {
        indra.behavior = { throw IllegalStateException("boom") }

        val result = recognizer().recognize("a.jpg")

        assertFallback(result, OcrFallbackReason.INDRA_FAILED)
        assertNull(guard.inFlight)
    }

    @Test
    fun indraOutOfMemory_fallsBackToMlKit() = runBlocking {
        indra.behavior = { throw OutOfMemoryError("native heap") }

        val result = recognizer().recognize("a.jpg")

        assertFallback(result, OcrFallbackReason.INDRA_FAILED)
        assertNull(guard.inFlight)
    }

    @Test
    fun indraTimeout_fallsBackWithTimeoutAndReleasesIndra() = runBlocking {
        indra.behavior = {
            delay(10_000)
            page("late", OcrEngine.INDRA)
        }

        val result = recognizer(timeoutMillis = 50).recognize("a.jpg")

        assertFallback(result, OcrFallbackReason.INDRA_TIMEOUT)
        assertTrue(indra.releases >= 1)
        assertNull(guard.inFlight)
    }

    @Test
    fun indraBlank_usesMlKitTextWithEmptyReason() = runBlocking {
        indra.behavior = { page("  ", OcrEngine.INDRA) }

        val result = recognizer().recognize("a.jpg")

        assertFallback(result, OcrFallbackReason.INDRA_EMPTY)
        assertEquals("ml kit text", result.text)
    }

    @Test
    fun indraBlankAndMlKitBlank_returnsTheIndraResult() = runBlocking {
        indra.behavior = { page("", OcrEngine.INDRA) }
        mlKit.behavior = { page(" ", OcrEngine.ML_KIT) }

        val result = recognizer().recognize("a.jpg")

        assertEquals(OcrEngine.INDRA, result.engine)
        assertEquals(OcrEngine.INDRA, result.requestedEngine)
        assertNull(result.fallbackReason)
        assertEquals("", result.text)
    }

    @Test
    fun breakerOpensAfterThreeConsecutiveFailures() = runBlocking {
        indra.behavior = { throw IllegalStateException("boom") }
        val recognizer = recognizer()

        repeat(3) { assertFallback(recognizer.recognize("p$it.jpg"), OcrFallbackReason.INDRA_FAILED) }
        assertEquals(3, indra.calls)

        val fourth = recognizer.recognize("p4.jpg")

        assertFallback(fourth, OcrFallbackReason.INDRA_UNAVAILABLE)
        assertEquals(3, indra.calls)
    }

    @Test
    fun breakerCounterResetsOnSuccess() = runBlocking {
        val recognizer = recognizer()
        indra.behavior = { throw IllegalStateException("boom") }
        repeat(2) { recognizer.recognize("f$it.jpg") }
        indra.behavior = { page("indra text", OcrEngine.INDRA) }
        assertEquals(OcrEngine.INDRA, recognizer.recognize("ok.jpg").engine)

        indra.behavior = { throw IllegalStateException("boom") }
        repeat(2) { recognizer.recognize("g$it.jpg") }
        indra.behavior = { page("indra text", OcrEngine.INDRA) }

        assertEquals(OcrEngine.INDRA, recognizer.recognize("again.jpg").engine)
    }

    @Test
    fun indraUnavailable_opensBreakerImmediately() = runBlocking {
        indra.behavior = { throw IndraUnavailableException("no models") }
        val recognizer = recognizer()

        assertFallback(recognizer.recognize("a.jpg"), OcrFallbackReason.INDRA_UNAVAILABLE)
        assertFallback(recognizer.recognize("b.jpg"), OcrFallbackReason.INDRA_UNAVAILABLE)

        assertEquals(1, indra.calls)
    }

    @Test
    fun resetBreaker_letsIndraRunAgain() = runBlocking {
        indra.behavior = { throw IndraUnavailableException("no models") }
        val recognizer = recognizer()
        recognizer.recognize("a.jpg")
        indra.behavior = { page("indra text", OcrEngine.INDRA) }

        recognizer.resetBreaker()

        assertEquals(OcrEngine.INDRA, recognizer.recognize("b.jpg").engine)
    }

    @Test
    fun suspendedGuard_usesMlKitWithoutCallingIndra() = runBlocking {
        guard.suspended = true

        val result = recognizer().recognize("a.jpg")

        assertFallback(result, OcrFallbackReason.INDRA_SUSPENDED)
        assertEquals(0, indra.calls)
        assertTrue(guard.events.isEmpty())
    }

    @Test
    fun poisonedPage_usesMlKitWithoutCallingIndra() = runBlocking {
        guard.poisoned += "bad.jpg"

        val bad = recognizer().recognize("bad.jpg")
        val good = recognizer().recognize("good.jpg")

        assertFallback(bad, OcrFallbackReason.INDRA_FAILED)
        assertEquals(OcrEngine.INDRA, good.engine)
        assertEquals(1, indra.calls)
    }

    @Test
    fun outerCancellation_propagatesAndClearsInFlight() = runBlocking {
        val started = CompletableDeferred<Unit>()
        indra.behavior = {
            started.complete(Unit)
            delay(60_000)
            page("never", OcrEngine.INDRA)
        }
        val recognizer = recognizer(timeoutMillis = 120_000)

        val job = async(Dispatchers.Default) { recognizer.recognize("a.jpg") }
        started.await()
        job.cancel()

        assertThrows(CancellationException::class.java) { runBlocking { job.await() } }
        assertNull(guard.inFlight)
        assertEquals(0, mlKit.calls)
    }

    @Test
    fun mlKitFailureOnFallbackPath_rethrowsWithoutRetryingIndra() {
        indra.behavior = { throw IllegalStateException("indra down") }
        mlKit.behavior = { throw IllegalArgumentException("ml kit down") }

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { recognizer().recognize("a.jpg") }
        }

        assertEquals("ml kit down", thrown.message)
        assertEquals(1, indra.calls)
    }

    @Test
    fun indraIsReleasedAfterIdleDelay() = runBlocking {
        val recognizer = recognizer(idleMillis = 50)

        recognizer.recognize("a.jpg")

        val deadline = System.currentTimeMillis() + 3_000
        while (indra.releases == 0 && System.currentTimeMillis() < deadline) delay(20)
        assertTrue("Indra should be released when idle", indra.releases >= 1)
    }

    @Test
    fun anotherCallBeforeTheIdleDelay_keepsIndraLoaded() = runBlocking {
        val recognizer = recognizer(idleMillis = 400)

        recognizer.recognize("a.jpg")
        delay(150)
        recognizer.recognize("b.jpg")
        delay(150)

        assertEquals(0, indra.releases)
    }

    @Test
    fun release_releasesBothEngines() = runBlocking {
        recognizer().release()

        assertEquals(1, indra.releases)
        assertEquals(1, mlKit.releases)
    }

    private fun assertFallback(result: RecognizedPage, reason: OcrFallbackReason) {
        assertEquals(OcrEngine.ML_KIT, result.engine)
        assertEquals(OcrEngine.INDRA, result.requestedEngine)
        assertEquals(reason, result.fallbackReason)
    }
}

private fun page(text: String, engine: OcrEngine) = RecognizedPage(
    text = text,
    blocks = emptyList(),
    imageWidth = 10,
    imageHeight = 10,
    engine = engine,
)

private class FakeRecognizer(
    private val engine: OcrEngine,
    var behavior: suspend (String) -> RecognizedPage,
) : PageTextRecognizer {
    @Volatile
    var calls = 0

    @Volatile
    var releases = 0

    override suspend fun recognize(imagePath: String): RecognizedPage {
        calls++
        return behavior(imagePath).also { check(it.engine == engine) { "Fake returned the wrong engine." } }
    }

    override suspend fun release() {
        releases++
    }
}

private class FakeEngineSource(@Volatile var engine: OcrEngine) : OcrEngineSource {
    override suspend fun selectedEngine(): OcrEngine = engine
}

private class RecordingGuard : IndraCrashGuard {
    val events = mutableListOf<String>()
    val poisoned = mutableSetOf<String>()

    @Volatile
    var suspended = false

    @Volatile
    var inFlight: String? = null

    override fun isSuspended() = suspended

    override fun isPoisoned(imagePath: String) = imagePath in poisoned

    override fun markInFlight(imagePath: String) {
        inFlight = imagePath
        synchronized(events) { events += "mark:$imagePath" }
    }

    override fun clearInFlight() {
        inFlight = null
        synchronized(events) { events += "clear" }
    }

    override fun reset() {
        suspended = false
        poisoned.clear()
        inFlight = null
    }

    override fun observeSuspended(): Flow<Boolean> = MutableStateFlow(suspended)
}
