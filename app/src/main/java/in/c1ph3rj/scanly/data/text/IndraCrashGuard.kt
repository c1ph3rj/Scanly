package `in`.c1ph3rj.scanly.data.text

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Protects the app from a native crash inside Indra OCR, which no `try` can catch. Before each
 * Indra page the page is written down durably; if the process dies, the next launch finds that
 * entry still there and asks Android why the process ended: a crash poisons the page and counts,
 * a low-memory kill only poisons the page, and a swipe-away, update or force stop is ignored. Two
 * crashes suspend Indra until the user selects it again.
 */
interface IndraCrashGuard {
    /** True once Indra crashed the app often enough that pages are read with ML Kit instead. */
    fun isSuspended(): Boolean

    /** True when this exact page image crashed the process while Indra was reading it. */
    fun isPoisoned(imagePath: String): Boolean

    /** Durably records that Indra is about to read [imagePath]; returns after the write is on disk. */
    fun markInFlight(imagePath: String)

    /** Records that Indra came back, normally or with a Kotlin exception. */
    fun clearInFlight()

    /**
     * Forgets the crash count so a suspended Indra is tried again. Poisoned pages stay poisoned:
     * a page that crashed Indra would crash it again, so it keeps going to ML Kit until its image
     * changes (which gives it a new key).
     */
    fun reset()

    fun observeSuspended(): Flow<Boolean>
}

/** What the guard persists. [inFlight] and [poisoned] hold page keys, never raw paths. */
data class IndraGuardState(
    val inFlight: String? = null,
    /** Wall-clock time [inFlight] was written, to match it against the process exit record. */
    val inFlightAtMillis: Long = 0L,
    val crashCount: Int = 0,
    val poisoned: List<String> = emptyList(),
)

/** Durable storage for [IndraGuardState]; [save] must not return before the write is on disk. */
interface IndraGuardStore {
    fun load(): IndraGuardState

    fun save(state: IndraGuardState)
}

/** Why the previous process ended, as far as Android recorded it. */
enum class PreviousExit {
    /** Java or native crash, or an ANR. */
    CRASH,

    /** Killed for memory; Indra may have caused it on this page, but it is not a crash. */
    LOW_MEMORY,

    /** Swiped away, force-stopped, updated, or ended normally. */
    NOT_A_CRASH,

    /** No record (Android 10, or the record was not found); treated as a crash to stay safe. */
    UNKNOWN,
}

/** The guard's rules, kept free of Android types so they run in plain JVM tests. */
class IndraCrashGuardCore(
    private val store: IndraGuardStore,
    private val keyOf: (String) -> String = ::pageKey,
    private val previousExit: (sinceMillis: Long) -> PreviousExit = { PreviousExit.UNKNOWN },
    private val now: () -> Long = System::currentTimeMillis,
) : IndraCrashGuard {
    private val lock = Any()
    private var state = IndraGuardState()
    private var loaded = false
    private val suspended = MutableStateFlow(false)

    override fun isSuspended(): Boolean = synchronized(lock) { loadedState().crashCount >= SUSPEND_AFTER_CRASHES }

    override fun isPoisoned(imagePath: String): Boolean =
        synchronized(lock) { keyOf(imagePath) in loadedState().poisoned }

    override fun markInFlight(imagePath: String) {
        synchronized(lock) { write(loadedState().copy(inFlight = keyOf(imagePath), inFlightAtMillis = now())) }
    }

    override fun clearInFlight() {
        synchronized(lock) {
            val current = loadedState()
            if (current.inFlight != null) write(current.copy(inFlight = null, inFlightAtMillis = 0L))
        }
    }

    override fun reset() {
        synchronized(lock) {
            write(IndraGuardState(poisoned = loadedState().poisoned))
        }
    }

    override fun observeSuspended(): Flow<Boolean> =
        suspended
            .onStart { synchronized(lock) { loadedState() } }
            .flowOn(Dispatchers.IO)

    /** The first access reads storage; a leftover in-flight entry means the last process died in Indra. */
    private fun loadedState(): IndraGuardState {
        if (!loaded) {
            loaded = true
            val stored = store.load()
            val inFlight = stored.inFlight
            state = if (inFlight == null) {
                stored
            } else {
                val exit = runCatching { previousExit(stored.inFlightAtMillis) }.getOrDefault(PreviousExit.UNKNOWN)
                val poisoned = (stored.poisoned.filterNot { it == inFlight } + inFlight).takeLast(MAX_POISONED)
                when (exit) {
                    PreviousExit.CRASH, PreviousExit.UNKNOWN ->
                        IndraGuardState(crashCount = stored.crashCount + 1, poisoned = poisoned)
                    PreviousExit.LOW_MEMORY -> IndraGuardState(crashCount = stored.crashCount, poisoned = poisoned)
                    PreviousExit.NOT_A_CRASH -> stored.copy(inFlight = null, inFlightAtMillis = 0L)
                }.also(store::save)
            }
            suspended.value = state.crashCount >= SUSPEND_AFTER_CRASHES
        }
        return state
    }

    private fun write(next: IndraGuardState) {
        state = next
        store.save(next)
        suspended.value = next.crashCount >= SUSPEND_AFTER_CRASHES
    }

    companion object {
        const val SUSPEND_AFTER_CRASHES = 2
        const val MAX_POISONED = 50

        /** Path hash plus file length: a retaken or re-filtered page is a different page. */
        fun pageKey(imagePath: String): String = "${imagePath.hashCode()}-${File(imagePath).length()}"
    }
}

/** [IndraGuardStore] over SharedPreferences, using `commit()` so a crash right after cannot lose the entry. */
class SharedPreferencesIndraGuardStore(private val preferences: SharedPreferences) : IndraGuardStore {
    override fun load(): IndraGuardState = IndraGuardState(
        inFlight = preferences.getString(KEY_IN_FLIGHT, null),
        inFlightAtMillis = preferences.getLong(KEY_IN_FLIGHT_AT, 0L),
        crashCount = preferences.getInt(KEY_CRASH_COUNT, 0),
        poisoned = preferences.getString(KEY_POISONED, null)
            ?.split(SEPARATOR)
            ?.filter(String::isNotEmpty)
            .orEmpty(),
    )

    override fun save(state: IndraGuardState) {
        preferences.edit()
            .putString(KEY_IN_FLIGHT, state.inFlight)
            .putLong(KEY_IN_FLIGHT_AT, state.inFlightAtMillis)
            .putInt(KEY_CRASH_COUNT, state.crashCount)
            .putString(KEY_POISONED, state.poisoned.joinToString(SEPARATOR))
            .commit()
    }

    private companion object {
        const val KEY_IN_FLIGHT = "in_flight"
        const val KEY_IN_FLIGHT_AT = "in_flight_at"
        const val KEY_CRASH_COUNT = "crash_count"
        const val KEY_POISONED = "poisoned"
        const val SEPARATOR = ","
    }
}

@Singleton
class SharedPreferencesIndraCrashGuard private constructor(
    core: IndraCrashGuardCore,
) : IndraCrashGuard by core {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        IndraCrashGuardCore(
            SharedPreferencesIndraGuardStore(
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            ),
            previousExit = { sinceMillis -> previousExit(context, sinceMillis) },
        ),
    )

    private companion object {
        const val PREFERENCES_NAME = "indra_crash_guard"

        /** Reads Android's record of how this app's main process last ended (Android 11+). */
        fun previousExit(context: Context, sinceMillis: Long): PreviousExit {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return PreviousExit.UNKNOWN
            val activityManager = context.getSystemService(ActivityManager::class.java) ?: return PreviousExit.UNKNOWN
            val record = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXIT_RECORDS)
                .firstOrNull { it.processName == context.packageName && it.timestamp >= sinceMillis }
                ?: return PreviousExit.UNKNOWN
            return when (record.reason) {
                ApplicationExitInfo.REASON_CRASH,
                ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_ANR,
                -> PreviousExit.CRASH
                ApplicationExitInfo.REASON_LOW_MEMORY,
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
                -> PreviousExit.LOW_MEMORY
                else -> PreviousExit.NOT_A_CRASH
            }
        }

        const val MAX_EXIT_RECORDS = 5
    }
}
