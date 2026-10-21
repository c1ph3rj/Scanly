package `in`.c1ph3rj.scanly

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import `in`.c1ph3rj.scanly.data.text.TextIndexScheduler
import `in`.c1ph3rj.scanly.domain.usecase.PurgeExpiredTrashUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ScanlyApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var textIndexScheduler: TextIndexScheduler
    @Inject lateinit var purgeExpiredTrash: PurgeExpiredTrashUseCase

    override fun onCreate() {
        super.onCreate()
        // Keeps page text (and therefore search) caught up with every capture and edit.
        textIndexScheduler.start()
        // Recently deleted keeps pages 2 days and documents/folders 7; anything older goes for good.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { purgeExpiredTrash() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
