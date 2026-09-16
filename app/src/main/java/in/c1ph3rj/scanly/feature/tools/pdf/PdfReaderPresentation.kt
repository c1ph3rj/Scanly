package `in`.c1ph3rj.scanly.feature.tools.pdf

import `in`.c1ph3rj.scanly.core.ui.ZoomableImageState

fun clampPdfReaderPageIndex(index: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    return index.coerceIn(0, pageCount - 1)
}

fun togglePdfReaderLayout(current: PdfReaderLayout): PdfReaderLayout =
    if (current == PdfReaderLayout.Paged) {
        PdfReaderLayout.Continuous
    } else {
        PdfReaderLayout.Paged
    }

fun togglePdfReaderChrome(visible: Boolean): Boolean = !visible

fun formatPdfReaderPageLabel(currentIndex: Int, pageCount: Int): String {
    if (pageCount <= 0) return "0 of 0"
    val current = clampPdfReaderPageIndex(currentIndex, pageCount) + 1
    return "$current of $pageCount"
}

/**
 * Both paged and continuous surfaces yield one-finger pans to the parent
 * scroller (pager / list) until pinch-zoom is active.
 */
fun pdfReaderAllowParentScrollGestures(): Boolean = true

fun pdfReaderParentScrollerEnabled(zoomActive: Boolean): Boolean = !zoomActive

fun pdfReaderZoomActive(zoomStates: Map<Int, ZoomableImageState>): Boolean =
    zoomStates.values.any { it.isZoomActive }

fun resetPdfReaderZoom(zoomStates: Map<Int, ZoomableImageState>) {
    zoomStates.values.forEach { it.reset() }
}
