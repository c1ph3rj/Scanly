package `in`.c1ph3rj.scanly.data.text

/** Reads the text of one page image. Implementations serialize their own calls. */
interface PageTextRecognizer {
    suspend fun recognize(imagePath: String): RecognizedPage

    /** Frees native memory once a batch is done; the next call recreates what it needs. */
    suspend fun release()
}
