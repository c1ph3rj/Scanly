package `in`.c1ph3rj.scanly.core.common

object AppVersionComparator {
    fun isRemoteNewer(installedVersion: String, remoteVersion: String): Boolean =
        compare(remoteVersion, installedVersion) > 0

    fun compare(left: String, right: String): Int {
        val parsedLeft = parse(left)
        val parsedRight = parse(right)
        val maxCoreParts = maxOf(parsedLeft.coreParts.size, parsedRight.coreParts.size)

        repeat(maxCoreParts) { index ->
            val leftPart = parsedLeft.coreParts.getOrElse(index) { 0 }
            val rightPart = parsedRight.coreParts.getOrElse(index) { 0 }
            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }

        return compareSuffixes(parsedLeft.suffix, parsedRight.suffix)
    }

    private fun compareSuffixes(left: String?, right: String?): Int = when {
        left == null && right == null -> 0
        left == null -> 1
        right == null -> -1
        else -> left.compareTo(right, ignoreCase = true)
    }

    private fun parse(rawVersion: String): ParsedVersion {
        val normalized = rawVersion
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("+")
        val core = normalized.substringBefore("-")
        val suffix = normalized.substringAfter("-", missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
        val coreParts = core
            .split(".", "_")
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            .ifEmpty { listOf(0) }

        return ParsedVersion(
            coreParts = coreParts,
            suffix = suffix,
        )
    }

    private data class ParsedVersion(
        val coreParts: List<Int>,
        val suffix: String?,
    )
}
