package `in`.c1ph3rj.scanly.data.archive

internal object LibraryArchivePolicy {
    private const val MIN_BACKUP_HEADROOM = 16L * 1024L * 1024L
    private const val MIN_RESTORE_HEADROOM = 32L * 1024L * 1024L

    fun backupRequiredBytes(sourceBytes: Long): Long {
        require(sourceBytes >= 0L)
        return Math.addExact(sourceBytes, maxOf(MIN_BACKUP_HEADROOM, sourceBytes / 20L))
    }

    fun hasSufficientBackupCapacity(requiredBytes: Long, availableBytes: Long?): Boolean {
        require(requiredBytes >= 0L)
        return availableBytes == null || availableBytes >= requiredBytes
    }

    fun restoreRequiredBytes(sourceBytes: Long): Long {
        require(sourceBytes >= 0L)
        return Math.addExact(
            Math.multiplyExact(sourceBytes, 2L),
            maxOf(MIN_RESTORE_HEADROOM, sourceBytes / 10L),
        )
    }

    fun isSafeArchivePath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith('/') &&
            !path.startsWith('\\') &&
            ':' !in path &&
            path.split('/', '\\').none { it.isBlank() || it == ".." || it == "." }

    fun uniqueRestoredTitle(base: String, used: MutableSet<String>): String {
        if (used.add(base.lowercase())) return base
        var index = 1
        while (true) {
            val suffix = if (index == 1) " (Restored)" else " (Restored $index)"
            val candidate = "$base$suffix"
            if (used.add(candidate.lowercase())) return candidate
            index++
        }
    }
}
