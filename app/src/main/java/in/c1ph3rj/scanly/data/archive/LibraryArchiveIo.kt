package `in`.c1ph3rj.scanly.data.archive

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal fun restorePath(path: String?, oldDocumentId: String, newRoot: File): String? {
    if (path == null) return null
    val prefix = "documents/$oldDocumentId/"
    check(path.startsWith(prefix)) { "Invalid document asset path." }
    val relative = path.removePrefix(prefix)
    return safeArchiveFile(newRoot, relative).absolutePath
}

internal fun safeArchiveFile(root: File, relativePath: String): File {
    check(LibraryArchivePolicy.isSafeArchivePath(relativePath)) { "Unsafe archive path." }
    val canonicalRoot = root.canonicalFile
    val target = File(canonicalRoot, relativePath).canonicalFile
    check(target.path.startsWith(canonicalRoot.path + File.separator)) { "Unsafe archive path." }
    return target
}

internal fun readCurrentEntry(input: InputStream, maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(LibraryArchiveConstants.BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        check(total <= maxBytes) { "Backup manifest is too large." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance(LibraryArchiveConstants.SHA_256)
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(LibraryArchiveConstants.BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
