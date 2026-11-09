package `in`.c1ph3rj.scanly.data.text

import android.util.Log
import `in`.c1ph3rj.scanly.data.text.indra.IndraUnavailableException
import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import `in`.c1ph3rj.scanly.domain.model.OcrFallbackReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Picks the OCR engine from Settings on every call. Reading a page never fails because of Indra:
 * ML Kit is the backup for every way Indra can go wrong, and each result records which engine
 * actually read the page and why a fallback happened.
 *
 * Ladder for a page that asks for Indra: crash guard suspended, session breaker open, page known
 * to crash Indra, then Indra itself under a time limit (Kotlin failures, timeouts and blank
 * results all fall back). A native crash cannot be caught, so the guard marks the page in flight
 * first; see [IndraCrashGuard]. If ML Kit also throws on a fallback path, that exception
 * propagates and the caller stores "no text"; Indra is never retried for the same page.
 */
@Singleton
class SelectablePageTextRecognizer internal constructor(
    private val mlKit: PageTextRecognizer,
    private val indra: PageTextRecognizer,
    private val engineSource: OcrEngineSource,
    private val guard: IndraCrashGuard,
    private val indraTimeoutMillis: Long,
    private val idleReleaseMillis: Long,
    private val scope: CoroutineScope,
) : PageTextRecognizer {

    @Inject
    constructor(
        @Named(ML_KIT_QUALIFIER) mlKit: PageTextRecognizer,
        @Named(INDRA_QUALIFIER) indra: PageTextRecognizer,
        engineSource: OcrEngineSource,
        guard: IndraCrashGuard,
    ) : this(
        mlKit = mlKit,
        indra = indra,
        engineSource = engineSource,
        guard = guard,
        indraTimeoutMillis = INDRA_PAGE_TIMEOUT_MS,
        idleReleaseMillis = INDRA_IDLE_RELEASE_MS,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val consecutiveFailures = AtomicInteger(0)

    @Volatile
    private var breakerOpen = false

    private val idleLock = Any()
    private var idleJob: Job? = null
    private var activeIndraCalls = 0

    override suspend fun recognize(imagePath: String): RecognizedPage {
        if (engineSource.selectedEngine() != OcrEngine.INDRA) return mlKit.recognize(imagePath)

        val skipReason = when {
            guard.isSuspended() -> OcrFallbackReason.INDRA_SUSPENDED
            breakerOpen -> OcrFallbackReason.INDRA_UNAVAILABLE
            guard.isPoisoned(imagePath) -> OcrFallbackReason.INDRA_FAILED
            else -> null
        }
        if (skipReason != null) return readWithMlKit(imagePath, skipReason)

        return when (val attempt = attemptIndra(imagePath)) {
            is IndraAttempt.Failed -> readWithMlKit(imagePath, attempt.reason)
            is IndraAttempt.Read -> {
                val page = attempt.page
                if (page.text.isNotBlank()) {
                    page.copy(
                        engine = OcrEngine.INDRA,
                        requestedEngine = OcrEngine.INDRA,
                        fallbackReason = null,
                    )
                } else {
                    // Indra found nothing; ML Kit may still see text it missed.
                    val backup = readWithMlKit(imagePath, OcrFallbackReason.INDRA_EMPTY)
                    if (backup.text.isNotBlank()) backup else page
                }
            }
        }
    }

    /** Lets Indra be tried again after the user re-selects it; the crash guard resets separately. */
    fun resetBreaker() {
        consecutiveFailures.set(0)
        breakerOpen = false
    }

    override suspend fun release() {
        synchronized(idleLock) {
            idleJob?.cancel()
            idleJob = null
        }
        try {
            indra.release()
        } finally {
            mlKit.release()
        }
    }

    private suspend fun readWithMlKit(imagePath: String, reason: OcrFallbackReason): RecognizedPage =
        mlKit.recognize(imagePath).copy(
            engine = OcrEngine.ML_KIT,
            requestedEngine = OcrEngine.INDRA,
            fallbackReason = reason,
        )

    private suspend fun attemptIndra(imagePath: String): IndraAttempt {
        beginIndraUse()
        // Durable before Indra starts: only a normal return or a Kotlin exception reaches the
        // finally below, so a native crash leaves the entry behind for the next launch.
        guard.markInFlight(imagePath)
        try {
            val start = System.nanoTime()
            val page = withTimeout(indraTimeoutMillis) { indra.recognize(imagePath) }
            consecutiveFailures.set(0)
            log("Indra read ${page.blocks.size} blocks in ${(System.nanoTime() - start) / 1_000_000} ms")
            return IndraAttempt.Read(page)
        } catch (timeout: TimeoutCancellationException) {
            log("Indra OCR timed out after $indraTimeoutMillis ms; falling back to ML Kit.", timeout)
            recordFailure()
            releaseIndraQuietly()
            return IndraAttempt.Failed(OcrFallbackReason.INDRA_TIMEOUT)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unavailable: IndraUnavailableException) {
            log("Indra OCR is unavailable; using ML Kit for this session.", unavailable)
            breakerOpen = true
            releaseIndraQuietly()
            return IndraAttempt.Failed(OcrFallbackReason.INDRA_UNAVAILABLE)
        } catch (error: Throwable) {
            log("Indra OCR failed; falling back to ML Kit.", error)
            recordFailure()
            releaseIndraQuietly()
            return IndraAttempt.Failed(OcrFallbackReason.INDRA_FAILED)
        } finally {
            guard.clearInFlight()
            endIndraUse()
        }
    }

    private fun recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= BREAKER_FAILURES) breakerOpen = true
    }

    /** The interpreter may be in a bad state after a failure, so drop it; the next page reloads it. */
    private suspend fun releaseIndraQuietly() {
        try {
            indra.release()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            log("Indra OCR could not be released.", error)
        }
    }

    private fun beginIndraUse() {
        synchronized(idleLock) {
            activeIndraCalls++
            idleJob?.cancel()
            idleJob = null
        }
    }

    /** After the last Indra call, frees its native memory once nothing has used it for a while. */
    private fun endIndraUse() {
        synchronized(idleLock) {
            activeIndraCalls--
            if (activeIndraCalls > 0) return
            idleJob = scope.launch {
                delay(idleReleaseMillis)
                if (synchronized(idleLock) { activeIndraCalls } == 0) releaseIndraQuietly()
            }
        }
    }

    private fun log(message: String, error: Throwable? = null) {
        // android.util.Log is unavailable in plain JVM tests; logging must never fail a page.
        try {
            if (error == null) Log.d(TAG, message) else Log.w(TAG, message, error)
        } catch (_: Throwable) {
            // Ignored on purpose.
        }
    }

    private sealed interface IndraAttempt {
        class Read(val page: RecognizedPage) : IndraAttempt

        class Failed(val reason: OcrFallbackReason) : IndraAttempt
    }

    companion object {
        const val ML_KIT_QUALIFIER = "ml_kit_recognizer"
        const val INDRA_QUALIFIER = "indra_recognizer"
        const val INDRA_PAGE_TIMEOUT_MS = 60_000L
        const val INDRA_IDLE_RELEASE_MS = 30_000L
        const val BREAKER_FAILURES = 3
        private const val TAG = "PageTextRecognizer"
    }
}
