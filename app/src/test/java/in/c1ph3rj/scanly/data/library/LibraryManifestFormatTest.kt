package `in`.c1ph3rj.scanly.data.library

import `in`.c1ph3rj.scanly.data.library.manifest.DocumentManifest
import `in`.c1ph3rj.scanly.data.library.manifest.PageManifest
import `in`.c1ph3rj.scanly.domain.model.LibraryAssetRef
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class LibraryManifestFormatTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun documentManifestRoundTripsAllRecoveryMetadata() {
        val documentId = UUID.randomUUID().toString()
        val pageId = UUID.randomUUID().toString()
        val asset = LibraryAssetRef(
            relativePath = "documents/$documentId/raw/$pageId-capture.jpg",
            revision = 3L,
            byteCount = 42L,
            sha256 = "a".repeat(64),
        )
        val original = DocumentManifest(
            id = documentId,
            revision = 3L,
            title = "Recovery fixture",
            createdAtMillis = 1L,
            updatedAtMillis = 2L,
            pages = listOf(
                PageManifest(
                    id = pageId,
                    pageIndex = 0,
                    rawAsset = asset,
                    rotationDegrees = 90,
                    filterPreset = "auto",
                    processingState = "processed",
                    createdAtMillis = 1L,
                    updatedAtMillis = 2L,
                ),
            ),
        )

        assertEquals(original, json.decodeFromString<DocumentManifest>(json.encodeToString(original)))
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        assertRejected("../private/file.jpg")
        assertRejected("/absolute/file.jpg")
        assertRejected("C:/absolute/file.jpg")
    }

    private fun assertRejected(path: String) {
        try {
            LibraryPathValidator.requireValid(path)
            fail("Expected path to be rejected: $path")
        } catch (_: IllegalArgumentException) {
            Unit
        }
    }
}
