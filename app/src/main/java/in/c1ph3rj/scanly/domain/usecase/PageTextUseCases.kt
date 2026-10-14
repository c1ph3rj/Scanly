package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.PageTextRepository
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
import javax.inject.Inject

class ObserveDocumentTextsUseCase @Inject constructor(private val repository: PageTextRepository) {
    operator fun invoke(documentId: String) = repository.observeDocumentTexts(documentId)
}

class ObserveTextIndexProgressUseCase @Inject constructor(private val repository: PageTextRepository) {
    operator fun invoke() = repository.observeIndexProgress()
}

class ObserveDocumentKindsUseCase @Inject constructor(private val repository: PageTextRepository) {
    operator fun invoke() = repository.observeDocumentKinds()
}

class RecognizeDocumentTextUseCase @Inject constructor(private val repository: PageTextRepository) {
    suspend operator fun invoke(
        documentId: String,
        force: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) = repository.recognizeDocument(documentId, force, onProgress)
}

class RecognizePageTextUseCase @Inject constructor(private val repository: PageTextRepository) {
    suspend operator fun invoke(pageId: String, force: Boolean = false) =
        repository.recognizePage(pageId, force)
}

class UpdatePageTextUseCase @Inject constructor(private val repository: PageTextRepository) {
    /** Pass null to discard the user's edits and go back to the recognized text. */
    suspend operator fun invoke(pageId: String, text: String?) = repository.updateUserText(pageId, text)
}

class SuggestSearchCorrectionUseCase @Inject constructor(private val repository: PageTextRepository) {
    suspend operator fun invoke(query: SearchQuery) = repository.suggestCorrection(query)
}

class SearchPageTextUseCase @Inject constructor(private val repository: PageTextRepository) {
    suspend operator fun invoke(query: SearchQuery) = repository.searchPages(query, MAX_PAGE_HITS)

    private companion object {
        /** Bounds snippet work for very common words; ranking only needs the best few hundred. */
        const val MAX_PAGE_HITS = 400
    }
}
