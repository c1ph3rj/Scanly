package `in`.c1ph3rj.scanly.core.common

import kotlin.coroutines.cancellation.CancellationException

/** Like [runCatching], but does not swallow coroutine cancellation or fatal errors. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
