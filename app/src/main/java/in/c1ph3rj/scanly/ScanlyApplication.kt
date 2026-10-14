package `in`.c1ph3rj.scanly

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import `in`.c1ph3rj.scanly.data.text.TextIndexScheduler
import javax.inject.Inject

@HiltAndroidApp
class ScanlyApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var textIndexScheduler: TextIndexScheduler

    override fun onCreate() {
        super.onCreate()
        // Keeps page text (and therefore search) caught up with every capture and edit.
        textIndexScheduler.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
