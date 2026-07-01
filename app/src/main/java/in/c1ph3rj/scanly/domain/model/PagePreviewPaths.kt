package `in`.c1ph3rj.scanly.domain.model

import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize

fun ScanPage.previewImageAsset(displaySize: PreviewDisplaySize): LibraryAssetRef? = when (displaySize) {
    PreviewDisplaySize.DETAIL -> processedAsset ?: thumbnailAsset ?: rawAsset
    else -> thumbnailAsset ?: processedAsset ?: rawAsset
}

@Deprecated("Use previewImageAsset")
fun ScanPage.previewImagePath(displaySize: PreviewDisplaySize): String? =
    previewImageAsset(displaySize)?.relativePath
