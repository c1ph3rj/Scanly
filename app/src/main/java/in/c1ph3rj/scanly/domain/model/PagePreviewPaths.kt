package `in`.c1ph3rj.scanly.domain.model

import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize

fun ScanPage.previewImagePath(displaySize: PreviewDisplaySize): String? = when (displaySize) {
    PreviewDisplaySize.DETAIL -> processedImagePath ?: thumbnailPath ?: rawImagePath
    else -> thumbnailPath ?: processedImagePath ?: rawImagePath
}
