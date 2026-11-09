package `in`.c1ph3rj.scanly.domain.usecase.pdftools

import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PdfWorkingCopy
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import `in`.c1ph3rj.scanly.domain.repository.FlatPageSetup
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import `in`.c1ph3rj.scanly.domain.repository.PdfToolkitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString
import androidx.annotation.StringRes

/** How imported PDF pages look in the library. */
enum class PdfImportLook(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    internal val filterPreset: PageFilterPreset,
) {
    ORIGINAL(R.string.pdf_import_look_keep_as_is, R.string.pdf_import_look_pages_look_exactly_like_the, PageFilterPreset.ORIGINAL),
    ENHANCE(R.string.pdf_import_look_enhance, R.string.pdf_import_look_even_out_scanned_pages_and, PageFilterPreset.AUTO),
    ;

    val label: String get() = appString(labelRes)
    val description: String get() = appString(descriptionRes)
}

/** A PDF that now lives in the library as an editable Scanly document. */
data class PdfLibraryImport(
    val documentId: String,
    val pageCount: Int,
)

/**
 * Adds pages of a PDF to the library as a new document. Each page is rendered to an image and
 * saved through the normal page pipeline (so it can be cropped, filtered, signed, searched and
 * exported like a scan), but as a flat page: no edge detection, since PDF pages are already straight.
 *
 * All or nothing: if any page fails, or the import is cancelled, the half-built document is
 * removed so the library never shows a partial copy.
 */
class ImportPdfToLibraryUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val pageRepository: PageRepository,
    private val pdfToolkitRepository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        copy: PdfWorkingCopy,
        pageIndexes: List<Int>,
        title: String,
        look: PdfImportLook,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ScanlyResult<PdfLibraryImport> {
        if (pageIndexes.isEmpty()) return failure(appString(R.string.import_pdf_to_library_use_case_choose_at_least_one_page))
        val documentId = when (val created = documentRepository.createDocument(title)) {
            is ScanlyResult.Success -> created.value
            is ScanlyResult.Failure -> return created
        }
        return try {
            pageIndexes.forEachIndexed { done, pageIndex ->
                val draft = pageRepository.prepareCapture(documentId).orThrow()
                pdfToolkitRepository.renderPageToJpeg(
                    copy = copy,
                    pageIndex = pageIndex,
                    longEdgePx = PageLongEdgePx,
                    targetPath = draft.rawImagePath,
                ).orThrow()
                pageRepository.finalizeCapture(draft, FlatPageSetup(look.filterPreset)).orThrow()
                onProgress(done + 1, pageIndexes.size)
            }
            ScanlyResult.Success(PdfLibraryImport(documentId, pageIndexes.size))
        } catch (cancelled: CancellationException) {
            discard(documentId)
            throw cancelled
        } catch (error: Throwable) {
            discard(documentId)
            failure(error.message ?: appString(R.string.import_pdf_to_library_use_case_could_not_add_this_pdf), error)
        }
    }

    private suspend fun discard(documentId: String) {
        withContext(NonCancellable) { documentRepository.deleteDocument(documentId) }
    }

    private fun <T> ScanlyResult<T>.orThrow(): T = when (this) {
        is ScanlyResult.Success -> value
        is ScanlyResult.Failure -> throw IllegalStateException(error.message, error.cause)
    }

    private fun failure(message: String, cause: Throwable? = null): ScanlyResult.Failure =
        ScanlyResult.Failure(ScanlyError(message = message, cause = cause))

    private companion object {
        /** The processing pipeline caps pages at 2800 px, so rendering larger would only cost memory. */
        const val PageLongEdgePx = 2_800
    }
}
