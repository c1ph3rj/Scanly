package `in`.c1ph3rj.scanly

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import `in`.c1ph3rj.scanly.core.common.AppLocales
import `in`.c1ph3rj.scanly.core.common.AppStrings
import `in`.c1ph3rj.scanly.data.text.IcuRomanizer
import `in`.c1ph3rj.scanly.data.text.TextIndexScheduler
import `in`.c1ph3rj.scanly.domain.search.SearchTextDeriver
import `in`.c1ph3rj.scanly.domain.usecase.PurgeExpiredTrashUseCase
import `in`.c1ph3rj.scanly.feature.tools.ToolArtCache
import `in`.c1ph3rj.scanly.feature.widget.ScanlyWidgetSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ScanlyApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var textIndexScheduler: Lazy<TextIndexScheduler>
    @Inject lateinit var purgeExpiredTrash: Lazy<PurgeExpiredTrashUseCase>

    override fun onCreate() {
        super.onCreate()
        // First, so repositories, workers and widgets can resolve localized text.
        AppLocales.applyDefaultLocale(this)
        AppStrings.init(this)
        // Before the indexer runs, so derived text gets English-spelling keys from the start.
        SearchTextDeriver.romanizer = IcuRomanizer()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Give the initial screen first access to Room before scheduled maintenance scans it.
            delay(StartupMaintenanceDelayMillis)
            textIndexScheduler.get().start()
            purgeExpiredTrash.get().invoke()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The UI is no longer visible: let go of decoded tool pictures; they reload on demand.
        if (level >= TRIM_MEMORY_UI_HIDDEN) ToolArtCache.clear()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLocales.onConfigurationChanged(this)
        // Locale changes no longer recreate MainActivity, which used to perform this rebind.
        ScanlyWidgetSupport.refreshAll(this)
    }

    private companion object {
        const val StartupMaintenanceDelayMillis = 1_500L
    }
}
