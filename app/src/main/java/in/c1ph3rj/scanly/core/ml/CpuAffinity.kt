package `in`.c1ph3rj.scanly.core.ml

import android.util.Log
import java.io.File

/**
 * Runs work on CPU cores of a single microarchitecture.
 *
 * LiteRT's CPU kernels (XNNPACK, ruy) pick a micro-kernel for the core each piece of work runs
 * on, and the big- and LITTLE-core kernels round differently. On a big.LITTLE phone the same
 * input therefore gives outputs that differ in the last bits depending on where the scheduler
 * put the threads, which made repeated imports of the same photo warp to slightly different
 * pages. Pinning inference to one core type makes it bit-reproducible.
 *
 * The LITTLE cluster is used because every app cpuset (foreground and background) includes it.
 * On phones with one core type, or when the native helper is missing, [run] just runs [block].
 */
internal object CpuAffinity {
    private const val TAG = "CpuAffinity"

    private val nativeLoaded: Boolean = try {
        System.loadLibrary("scanly_affinity")
        true
    } catch (error: Throwable) {
        Log.w(TAG, "Native affinity helper unavailable; inference runs unpinned.", error)
        false
    }

    /** CPUs of the slowest core type, or null when pinning is unnecessary or impossible. */
    val uniformCoreMask: Long? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (!nativeLoaded) return@lazy null
        runCatching {
            CpuClusters.littleCoreMask(
                cpuInfo = File("/proc/cpuinfo").readText(),
                maxFrequencyKhz = { cpu -> readMaxFrequencyKhz(cpu) },
            )
        }.onFailure { Log.w(TAG, "Could not read the CPU topology.", it) }.getOrNull()
    }

    /** Runs [block] on the calling thread while it is pinned to [uniformCoreMask]. */
    fun <T> run(block: () -> T): T {
        val mask = uniformCoreMask ?: return block()
        // Without the current mask the thread could not be unpinned afterwards, and it is a
        // shared dispatcher thread, so skip pinning rather than leave it on the LITTLE cores.
        val previous = nativeGetAffinity()
        if (previous == 0L || nativeSetAffinity(mask) != 0) {
            Log.w(TAG, "Could not pin to CPU mask ${mask.toString(16)}; running unpinned.")
            return block()
        }
        try {
            return block()
        } finally {
            nativeSetAffinity(previous)
        }
    }

    /** CPU the calling thread runs on (tests), or -1. */
    fun currentCpu(): Int = if (nativeLoaded) nativeCurrentCpu() else -1

    private fun readMaxFrequencyKhz(cpu: Int): Long? =
        runCatching {
            File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
        }.getOrNull()

    @JvmStatic
    private external fun nativeGetAffinity(): Long

    @JvmStatic
    private external fun nativeSetAffinity(mask: Long): Int

    @JvmStatic
    private external fun nativeCurrentCpu(): Int
}

/** Pure parsing of the CPU topology, kept separate so it can be unit tested. */
internal object CpuClusters {
    private const val MAX_CPUS = 64

    /**
     * Mask of the CPUs that share the core type of the slowest cluster, or null when all cores
     * have the same type (nothing to pin) or the topology cannot be read.
     *
     * Core type is the `CPU implementer / part / variant / revision` of each `processor` block in
     * [cpuInfo]; the slowest type is the one whose fastest core has the lowest max frequency.
     */
    fun littleCoreMask(cpuInfo: String, maxFrequencyKhz: (Int) -> Long?): Long? {
        val coreTypes = parseCoreTypes(cpuInfo)
        if (coreTypes.isEmpty() || coreTypes.values.toSet().size <= 1) return null
        val byType = coreTypes.entries.groupBy({ it.value }, { it.key })
        val slowest = byType.minByOrNull { (_, cpus) ->
            cpus.maxOf { maxFrequencyKhz(it) ?: Long.MAX_VALUE }
        }?.value ?: return null
        return slowest.fold(0L) { mask, cpu -> mask or (1L shl cpu) }
    }

    internal fun parseCoreTypes(cpuInfo: String): Map<Int, String> {
        val types = sortedMapOf<Int, String>()
        var processor: Int? = null
        val fields = HashMap<String, String>()
        fun flush() {
            val cpu = processor ?: return
            val part = fields["CPU part"] ?: return
            if (cpu in 0 until MAX_CPUS) {
                types[cpu] = listOf("CPU implementer", "CPU part", "CPU variant", "CPU revision")
                    .joinToString("/") { fields[it].orEmpty() }
                    .ifEmpty { part }
            }
        }
        for (line in cpuInfo.lineSequence()) {
            val separator = line.indexOf(':')
            if (separator < 0) continue
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            if (key == "processor") {
                flush()
                processor = value.toIntOrNull()
                fields.clear()
            } else {
                fields[key] = value
            }
        }
        flush()
        return types
    }
}
