package `in`.c1ph3rj.scanly.data.text.indra

/**
 * Indra could not start in this process: OpenCV or a model failed to load. Retrying the next page
 * would fail the same way, so the caller stops using Indra for the rest of the session.
 */
class IndraUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
