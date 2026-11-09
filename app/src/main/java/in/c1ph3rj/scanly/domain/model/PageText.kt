package `in`.c1ph3rj.scanly.domain.model

/** A rectangle in page-image coordinates normalized to 0..1, so it survives any display size. */
data class TextBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class PageTextLine(
    val text: String,
    val box: TextBox,
)

/** A paragraph-like region. [text] is reflowed for reading; [lines] keep the original breaks. */
data class PageTextBlock(
    val text: String,
    val box: TextBox,
    val lines: List<PageTextLine>,
)

data class PageText(
    val pageId: String,
    val documentId: String,
    val recognizedText: String,
    val userText: String?,
    val blocks: List<PageTextBlock>,
    /** Size of the image the text was read from; 0 when unknown. */
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val sourceUpdatedAtMillis: Long,
    val recognizedAtMillis: Long,
    val kind: DocumentKind?,
    /** Engine that produced [recognizedText]; null when nothing was recognized or the page predates tracking. */
    val engine: OcrEngine? = null,
    /** Engine the user had selected when the page was read. */
    val requestedEngine: OcrEngine? = null,
    /** Set when [requestedEngine] was Indra but ML Kit read the page. */
    val fallbackReason: OcrFallbackReason? = null,
) {
    /** What the user reads, copies, shares and searches. */
    val text: String get() = userText ?: recognizedText

    val isEdited: Boolean get() = userText != null

    val hasText: Boolean get() = text.isNotBlank()

    val wordCount: Int get() = TextStats.wordCount(text)

    /** Width / height of the page image, or null when unknown. */
    val imageAspectRatio: Float?
        get() = if (imageWidth > 0 && imageHeight > 0) imageWidth.toFloat() / imageHeight else null
}

/** Library-wide indexing state, used to tell the user when search may still be incomplete. */
data class TextIndexProgress(
    val totalPages: Int,
    val pendingPages: Int,
) {
    val isComplete: Boolean get() = pendingPages == 0
    val indexedPages: Int get() = (totalPages - pendingPages).coerceAtLeast(0)
}

object TextStats {
    private val wordPattern = Regex("[\\p{L}\\p{N}]+")

    fun wordCount(text: String): Int = wordPattern.findAll(text).count()
}
