package `in`.c1ph3rj.scanly.data.text

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.data.local.db.dao.PageTextDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the text index caught up without any feature having to remember to call it.
 *
 * It watches how many pages lack up-to-date text. New captures, imports, retakes, crops and
 * filter changes all bump a page's `updatedAtMillis`, so they show up here automatically.
 * Work is enqueued only when the pending count *grows* (or on start), never as the worker
 * drains it, so the chain can't feed itself.
 */
@Singleton
class TextIndexScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: PageTextDao,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            var previousPending = 0
            dao.observeIndexProgress()
                .map { it.pendingPages }
                // A scan session saves pages in quick succession; index them as one batch.
                .debounce(SETTLE_MILLIS)
                .collect { pending ->
                    if (pending > previousPending) enqueue()
                    previousPending = pending
                }
        }
    }

    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<TextIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .addTag(TextIndexWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TextIndexWorker.UNIQUE_NAME,
            // Append: if a run is mid-way, a follow-up run picks up anything it missed.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private companion object {
        const val SETTLE_MILLIS = 1_500L
    }
}
