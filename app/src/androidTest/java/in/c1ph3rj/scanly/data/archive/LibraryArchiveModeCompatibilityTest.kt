package `in`.c1ph3rj.scanly.data.archive

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryArchiveModeCompatibilityTest {
    @Test
    fun legacyArchiveFieldsDefaultToDocumentMode() {
        val document = LibraryArchiveEngine.ArchiveDocument.fromJson(
            JSONObject()
                .put("id", "document-1")
                .put("title", "Legacy")
                .put("pageCount", 1)
                .put("createdAtMillis", 1L)
                .put("updatedAtMillis", 2L),
        )
        val page = LibraryArchiveEngine.ArchivePage.fromJson(legacyPageJson())

        assertEquals("document", document.preferredScanMode)
        assertNull(document.preferredIdFilterPreset)
        assertNull(document.preferredBookFilterPreset)
        assertEquals("document", page.scanMode)
        assertNull(page.idCardPairId)
        assertNull(page.idCardSide)
    }

    @Test
    fun idModeMetadataRoundTripsWithoutChangingAssetFields() {
        val restored = LibraryArchiveEngine.ArchivePage.fromJson(
            LibraryArchiveEngine.ArchivePage.fromJson(
                legacyPageJson()
                    .put("scanMode", "id_card")
                    .put("idCardPairId", "pair-1")
                    .put("idCardSide", "front"),
            ).toJson(),
        )

        assertEquals("id_card", restored.scanMode)
        assertEquals("pair-1", restored.idCardPairId)
        assertEquals("front", restored.idCardSide)
        assertEquals("raw/page.jpg", restored.rawPath)
    }

    private fun legacyPageJson() = JSONObject()
        .put("id", "page-1")
        .put("documentId", "document-1")
        .put("pageIndex", 0)
        .put("rawPath", "raw/page.jpg")
        .put("rotationDegrees", 0)
        .put("filterPreset", "auto")
        .put("processingState", "ready")
        .put("createdAtMillis", 1L)
        .put("updatedAtMillis", 2L)
}
