package `in`.c1ph3rj.scanly

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.data.storage.PersistentDocumentLibrarySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ScanlyApplication : Application() {
    @Inject lateinit var documentLibrarySync: PersistentDocumentLibrarySync
    @Inject lateinit var dispatchers: ScanlyDispatchers

    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(dispatchers.io) {
            runCatching {
                documentLibrarySync.sync()
            }
        }
    }
}
