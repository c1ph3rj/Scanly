package `in`.c1ph3rj.scanly.domain.model

/** Why a page that asked for [OcrEngine.INDRA] was read with ML Kit instead. */
enum class OcrFallbackReason {
    /** Indra threw while reading this page, or this exact page crashed the process before. */
    INDRA_FAILED,

    /** Indra ran but found no text, and ML Kit did. */
    INDRA_EMPTY,

    /** Indra took longer than the per-page limit. */
    INDRA_TIMEOUT,

    /** Indra could not start this session, or its circuit breaker is open. */
    INDRA_UNAVAILABLE,

    /** The crash guard paused Indra after repeated native crashes. */
    INDRA_SUSPENDED,
}
