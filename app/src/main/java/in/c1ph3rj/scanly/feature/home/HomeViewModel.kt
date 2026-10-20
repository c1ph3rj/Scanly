package `in`.c1ph3rj.scanly.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.CreateGroupUseCase
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.usecase.SuggestDocumentTitleUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SuggestGroupTitleUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ImportImagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveRecentDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveRecentGroupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveShouldShowRatingPromptUseCase
import `in`.c1ph3rj.scanly.domain.usecase.MarkRatingPromptShownUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DismissRatingPromptUseCase
import `in`.c1ph3rj.scanly.domain.usecase.MarkAppRatedUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentGroups: List<DocumentGroup> = emptyList(),
    val hasMoreGroups: Boolean = false,
    val recentDocuments: List<ScanDocument> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importCurrent: Int = 0,
    val importTotal: Int = 0,
    val importStageLabel: String = "",
)

sealed interface HomeEvent {
    data class OpenDocument(val documentId: String) : HomeEvent
    data class OpenGroup(val groupId: String) : HomeEvent
    data class ShowMessage(val message: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeRecentGroupsUseCase: ObserveRecentGroupsUseCase,
    observeRecentDocumentsUseCase: ObserveRecentDocumentsUseCase,
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val createGroupUseCase: CreateGroupUseCase,
    private val importImagesUseCase: ImportImagesUseCase,
    private val suggestDocumentTitleUseCase: SuggestDocumentTitleUseCase,
    private val suggestGroupTitleUseCase: SuggestGroupTitleUseCase,
    private val observeShouldShowRatingPromptUseCase: ObserveShouldShowRatingPromptUseCase,
    private val markRatingPromptShownUseCase: MarkRatingPromptShownUseCase,
    private val dismissRatingPromptUseCase: DismissRatingPromptUseCase,
    private val markAppRatedUseCase: MarkAppRatedUseCase,
) : ViewModel() {

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()
    private val importProgress = MutableStateFlow(ImportProgressSnapshot())

    private val ratingPromptPresentedInThisSession = MutableStateFlow(ratingPromptPresentedThisProcess)

    /**
     * True when the rating nudge is allowed and has not yet been presented in this process. The
     * screen waits a short moment on Home before presenting it.
     */
    val showRatingPrompt: StateFlow<Boolean> = combine(
        observeShouldShowRatingPromptUseCase(),
        ratingPromptPresentedInThisSession,
    ) { shouldShow, presented -> shouldShow && !presented }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /** Called once the rating sheet is actually on screen. */
    fun onRatingPromptPresented() {
        ratingPromptPresentedThisProcess = true
        ratingPromptPresentedInThisSession.value = true
        viewModelScope.launch { markRatingPromptShownUseCase() }
    }

    /** "Not now" or swipe-away on the rating sheet. */
    fun onRatingPromptDismissed() {
        viewModelScope.launch { dismissRatingPromptUseCase() }
    }

    /** Any rating action on the sheet (the stars and the primary button share one path). */
    fun onRatingPromptRated() {
        viewModelScope.launch { markAppRatedUseCase() }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        observeRecentGroupsUseCase(limit = 4),
        observeRecentDocumentsUseCase(limit = 4),
        importProgress,
    ) { groups, docs, progress ->
        HomeUiState(
            recentGroups = groups.take(3),
            hasMoreGroups = groups.size > 3,
            recentDocuments = docs,
            isLoading = false,
            isImporting = progress.active,
            importCurrent = progress.current,
            importTotal = progress.total,
            importStageLabel = progress.stageLabel,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // Loading until Room's first emission, so Home never flashes its empty state.
        initialValue = HomeUiState(isLoading = true),
    )

    fun createDocument(title: String) {
        viewModelScope.launch {
            when (val result = createDocumentUseCase(title)) {
                is ScanlyResult.Success -> _events.emit(HomeEvent.OpenDocument(result.value))
                is ScanlyResult.Failure -> _events.emit(HomeEvent.ShowMessage(result.error.message))
            }
        }
    }

    suspend fun suggestDocumentTitle(format: DocumentTitleFormat): String =
        suggestDocumentTitleUseCase(format)

    suspend fun suggestGroupTitle(format: GroupTitleFormat): String =
        suggestGroupTitleUseCase(format)

    fun createGroup(title: String) {
        viewModelScope.launch {
            when (val result = createGroupUseCase(title)) {
                is ScanlyResult.Success -> _events.emit(HomeEvent.OpenGroup(result.value))
                is ScanlyResult.Failure -> _events.emit(HomeEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun importImagesAsDocument(imageUris: List<Uri>) {
        if (imageUris.isEmpty() || importProgress.value.active) {
            return
        }

        val cappedSelection = ImageImportSupport.capSelection(imageUris)
        val total = cappedSelection.items.size

        viewModelScope.launch {
            importProgress.value = ImportProgressSnapshot(
                active = true,
                current = 0,
                total = total,
                stageLabel = "Starting import",
            )
            try {
                when (val createResult = createDocumentUseCase.createImported()) {
                    is ScanlyResult.Success -> {
                        when (
                            val importResult = importImagesUseCase(
                                documentId = createResult.value,
                                imageUris = cappedSelection.items,
                                onProgress = { progress ->
                                    importProgress.value = ImportProgressSnapshot(
                                        active = true,
                                        current = progress.currentIndex,
                                        total = progress.totalCount,
                                        stageLabel = progress.stageLabel,
                                    )
                                },
                            )
                        ) {
                            is ScanlyResult.Success -> {
                                _events.emit(HomeEvent.OpenDocument(createResult.value))
                                _events.emit(
                                    HomeEvent.ShowMessage(
                                        ImageImportSupport.importResultMessage(
                                            importedCount = importResult.value.importedCount,
                                            truncated = cappedSelection.truncated,
                                            skippedCount = importResult.value.skippedCount,
                                        ),
                                    ),
                                )
                            }

                            is ScanlyResult.Failure -> {
                                _events.emit(HomeEvent.ShowMessage(importResult.error.message))
                            }
                        }
                    }

                    is ScanlyResult.Failure -> {
                        _events.emit(HomeEvent.ShowMessage(createResult.error.message))
                    }
                }
            } finally {
                importProgress.value = ImportProgressSnapshot()
            }
        }
    }
}

/** Process-wide: the rating nudge is shown at most once per app process. */
private var ratingPromptPresentedThisProcess = false

private data class ImportProgressSnapshot(
    val active: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val stageLabel: String = "",
)
