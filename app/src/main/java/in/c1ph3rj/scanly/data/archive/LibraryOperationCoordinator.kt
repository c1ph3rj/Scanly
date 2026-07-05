package `in`.c1ph3rj.scanly.data.archive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryOperationCoordinator @Inject constructor() {
    private val mutex = Mutex()
    private val _maintenanceMessage = MutableStateFlow<String?>(null)
    val maintenanceMessage = _maintenanceMessage.asStateFlow()

    suspend fun <T> withMutation(block: suspend () -> T): T = mutex.withLock {
        check(_maintenanceMessage.value == null) {
            _maintenanceMessage.value ?: "Library maintenance is in progress."
        }
        block()
    }

    suspend fun <T> withMaintenance(message: String, block: suspend () -> T): T =
        mutex.withLock {
            _maintenanceMessage.value = message
            try {
                block()
            } finally {
                _maintenanceMessage.value = null
            }
        }
}
