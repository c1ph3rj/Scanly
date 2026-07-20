package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ImportImagesProgress
import `in`.c1ph3rj.scanly.domain.model.ImportStage
import `in`.c1ph3rj.scanly.domain.repository.ImageImportRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportImagesUseCaseTest {

    @Test
    fun invoke_delegatesToImageImportRepository() = runBlocking {
        val repository = FakeImageImportRepository()
        val useCase = ImportImagesUseCase(repository)

        val result = useCase(
            documentId = "doc-9",
            imageUriStrings = listOf("content://photo/1"),
            onProgress = {},
        )

        assertTrue(result is ScanlyResult.Success)
        assertEquals("doc-9", repository.lastDocumentId)
        assertEquals(listOf("content://photo/1"), repository.lastUris)
    }

    @Test
    fun invoke_propagatesRepositoryFailure() = runBlocking {
        val repository = FakeImageImportRepository(
            failure = ScanlyError("decode failed"),
        )
        val useCase = ImportImagesUseCase(repository)

        val result = useCase(
            documentId = "doc-9",
            imageUriStrings = listOf("content://bad"),
        )

        assertTrue(result is ScanlyResult.Failure)
        assertEquals("decode failed", (result as ScanlyResult.Failure).error.message)
    }

    @Test
    fun progressModel_stageLabelsAreStable() {
        assertEquals(
            "Preparing page",
            ImportImagesProgress(1, 3, ImportStage.Preparing).stageLabel,
        )
        assertEquals(
            "Detecting document",
            ImportImagesProgress(2, 3, ImportStage.Detecting).stageLabel,
        )
        assertEquals(
            "Saving page",
            ImportImagesProgress(3, 3, ImportStage.Finalizing).stageLabel,
        )
    }

    private class FakeImageImportRepository(
        private val failure: ScanlyError? = null,
    ) : ImageImportRepository {
        var lastDocumentId: String? = null
        var lastUris: List<String>? = null

        override suspend fun importImages(
            documentId: String,
            imageUriStrings: List<String>,
            onProgress: suspend (ImportImagesProgress) -> Unit,
        ): ScanlyResult<Unit> {
            lastDocumentId = documentId
            lastUris = imageUriStrings
            failure?.let { return ScanlyResult.Failure(it) }
            return ScanlyResult.Success(Unit)
        }
    }
}
