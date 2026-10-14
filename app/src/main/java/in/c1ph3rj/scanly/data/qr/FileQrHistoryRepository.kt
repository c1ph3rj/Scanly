package `in`.c1ph3rj.scanly.data.qr

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.domain.model.QrHistoryEntry
import `in`.c1ph3rj.scanly.domain.model.QrOrigin
import `in`.c1ph3rj.scanly.domain.repository.QrHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small JSON file in app-private storage (never backed up to shared storage). Loaded lazily
 * on first observe/add and capped at [MaxEntries] so it never grows unbounded.
 */
@Singleton
class FileQrHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) : QrHistoryRepository {

    private val mutex = Mutex()
    private val state = MutableStateFlow<List<QrHistoryEntry>>(emptyList())
    private var loaded = false

    private val file: AtomicFile by lazy {
        AtomicFile(File(File(context.filesDir, "qr").apply { mkdirs() }, "history.json"))
    }

    override fun observe(): Flow<List<QrHistoryEntry>> =
        state.asStateFlow().onStart { ensureLoaded() }

    override suspend fun add(raw: String, origin: QrOrigin): QrHistoryEntry = mutate { current ->
        val now = System.currentTimeMillis()
        val newest = current.firstOrNull()
        if (newest != null && newest.raw == raw && newest.origin == origin) {
            val refreshed = newest.copy(createdAtMillis = now)
            refreshed to (listOf(refreshed) + current.drop(1))
        } else {
            val entry = QrHistoryEntry(UUID.randomUUID().toString(), raw, origin, now)
            entry to (listOf(entry) + current).take(MaxEntries)
        }
    }

    override suspend fun delete(id: String) {
        mutate { current -> Unit to current.filterNot { it.id == id } }
    }

    override suspend fun clear() {
        mutate { _ -> Unit to emptyList() }
    }

    private suspend fun <T> mutate(block: (List<QrHistoryEntry>) -> Pair<T, List<QrHistoryEntry>>): T =
        withContext(dispatchers.io) {
            mutex.withLock {
                loadLocked()
                val (value, next) = block(state.value)
                state.value = next
                write(next)
                value
            }
        }

    private suspend fun ensureLoaded() {
        if (loaded) return
        withContext(dispatchers.io) { mutex.withLock { loadLocked() } }
    }

    private fun loadLocked() {
        if (loaded) return
        loaded = true
        state.value = runCatching {
            val array = JSONArray(String(file.readFully(), Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        QrHistoryEntry(
                            id = item.getString("id"),
                            raw = item.getString("raw"),
                            origin = runCatching { QrOrigin.valueOf(item.getString("origin")) }
                                .getOrDefault(QrOrigin.Scanned),
                            createdAtMillis = item.optLong("at"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(entries: List<QrHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("raw", entry.raw)
                    .put("origin", entry.origin.name)
                    .put("at", entry.createdAtMillis),
            )
        }
        val stream = file.startWrite()
        try {
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private companion object {
        const val MaxEntries = 200
    }
}
