package `in`.c1ph3rj.scanly.data.library

object LibraryPathValidator {
    const val MAX_PATH_LENGTH = 512

    fun requireValid(relativePath: String) {
        require(relativePath.isNotBlank()) { "Library path cannot be blank." }
        require(!relativePath.startsWith('/') && !relativePath.startsWith('\\')) { "Absolute paths are not allowed." }
        require(!relativePath.contains("..") && !relativePath.contains('\\') && !relativePath.contains(':')) {
            "Unsafe library path."
        }
        require(relativePath.length <= MAX_PATH_LENGTH) { "Library path is too long." }
    }
}

