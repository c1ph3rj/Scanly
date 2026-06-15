package `in`.c1ph3rj.scanly.domain.usecase

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class ImportImagesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pageRepository: PageRepository,
    private val dispatchers: ScanlyDispatchers,
) {
    suspend operator fun invoke(documentId: String, imageUris: List<Uri>): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                for (uri in imageUris) {
                    val draftResult = pageRepository.prepareCapture(documentId)
                    if (draftResult !is ScanlyResult.Success) {
                        error("Failed to prepare page capture for imported image.")
                    }
                    val draft = draftResult.value

                    val rawFile = File(draft.rawImagePath)
                    
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(rawFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } ?: error("Failed to open input stream for URI: $uri")

                    val finalizeResult = pageRepository.finalizeCapture(draft)
                    if (finalizeResult !is ScanlyResult.Success) {
                        error("Failed to finalize captured page for imported image.")
                    }
                }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Failed to import images.",
                            cause = throwable,
                        )
                    )
                }
            )
        }
}
