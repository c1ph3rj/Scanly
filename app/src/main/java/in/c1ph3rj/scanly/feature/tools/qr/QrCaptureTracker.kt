package `in`.c1ph3rj.scanly.feature.tools.qr

/** Require a short, uninterrupted lock so the target is visible before showing a result. */
internal class QrCaptureTracker {
    private var raw: String? = null
    private var startedAt = 0L
    private var lastSeenAt = 0L

    fun reset() {
        raw = null
    }

    fun update(value: String?, now: Long): Boolean {
        if (value == null) {
            reset()
            return false
        }
        if (value != raw || now - lastSeenAt > 500L) {
            raw = value
            startedAt = now
        }
        lastSeenAt = now
        return now - startedAt >= 350L
    }
}
