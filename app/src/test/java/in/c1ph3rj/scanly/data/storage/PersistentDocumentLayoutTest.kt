package `in`.c1ph3rj.scanly.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentDocumentLayoutTest {
    @Test
    fun parseAsset_returnsRawPageAssetForScanlyPagePath() {
        val asset = PersistentDocumentLayout.parseAsset(
            relativePath = "Pictures/Scanly/doc-123/raw/",
            displayName = "page_003.jpg",
        )

        assertEquals("doc-123", asset?.documentId)
        assertEquals(PersistentDocumentLayout.RAW_DIRECTORY, asset?.directoryName)
        assertEquals(2, asset?.pageIndex)
        assertFalse(asset?.isCover ?: true)
    }

    @Test
    fun parseAsset_returnsCoverAssetForCoverThumbnail() {
        val asset = PersistentDocumentLayout.parseAsset(
            relativePath = "Pictures/Scanly/doc-123/thumbs/",
            displayName = "cover.jpg",
        )

        assertEquals("doc-123", asset?.documentId)
        assertEquals(PersistentDocumentLayout.THUMBNAILS_DIRECTORY, asset?.directoryName)
        assertNull(asset?.pageIndex)
        assertTrue(asset?.isCover ?: false)
    }

    @Test
    fun parseAsset_rejectsUnknownLayout() {
        assertNull(
            PersistentDocumentLayout.parseAsset(
                relativePath = "Pictures/Other/doc-123/raw/",
                displayName = "page_001.jpg",
            ),
        )
        assertNull(
            PersistentDocumentLayout.parseAsset(
                relativePath = "Pictures/Scanly/doc-123/raw/",
                displayName = "scan.jpg",
            ),
        )
    }
}
