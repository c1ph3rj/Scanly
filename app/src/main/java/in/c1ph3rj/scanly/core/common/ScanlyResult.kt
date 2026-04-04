package `in`.c1ph3rj.scanly.core.common

sealed interface ScanlyResult<out T> {
    data class Success<T>(val value: T) : ScanlyResult<T>
    data class Failure(val error: ScanlyError) : ScanlyResult<Nothing>
}

data class ScanlyError(
    val message: String,
    val cause: Throwable? = null,
)
