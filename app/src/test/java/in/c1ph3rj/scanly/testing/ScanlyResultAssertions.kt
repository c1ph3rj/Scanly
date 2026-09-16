package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.ScanlyResult

fun <T> ScanlyResult<T>.requireSuccess(): T = when (this) {
    is ScanlyResult.Success -> value
    is ScanlyResult.Failure ->
        throw AssertionError("Expected success, got failure: ${error.message}", error.cause)
}
