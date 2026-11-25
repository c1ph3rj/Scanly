package `in`.c1ph3rj.scanly.domain.model

/**
 * On-device engine that reads page text. [INDRA] is the default when the build ships its models
 * (`IndraAvailability`); [ML_KIT] is the fallback for any page Indra can't read, and the default
 * otherwise.
 */
enum class OcrEngine {
    ML_KIT,
    INDRA,
}
