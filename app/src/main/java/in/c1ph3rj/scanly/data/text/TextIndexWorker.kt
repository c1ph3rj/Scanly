package `in`.c1ph3rj.scanly.data.text

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.PageTextRepository

/**
 * Reads text from pages in the background, a few at a time, newest documents first.
 * Each batch is short, so a Text screen opened mid-run gets its pages read promptly.
 */
@HiltWorker
class TextIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PageTextRepository,
    private val recognizer: PageTextRecognizer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            while (!isStopped) {
                val read = when (val result = repository.recognizePendingPages(BATCH_SIZE)) {
                    is ScanlyResult.Success -> result.value
                    is ScanlyResult.Failure -> return Result.retry()
                }
                if (read == 0) return Result.success()
                if (SystemClock.elapsedRealtime() - startedAt > MAX_RUN_MILLIS) {
                    // Stay well inside WorkManager's execution window; a follow-up run continues.
                    repository.requestBackgroundIndexing()
                    return Result.success()
                }
            }
            return Result.success()
        } finally {
            recognizer.release()
        }
    }

    companion object {
        const val UNIQUE_NAME = "page-text-index"
        const val TAG = "page-text-index"
        private const val BATCH_SIZE = 4
        private const val MAX_RUN_MILLIS = 8 * 60 * 1_000L
    }
}
