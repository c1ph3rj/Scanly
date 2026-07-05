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
    val recentDocuments: List<ScanDocument> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
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
) : ViewModel() {

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()
    private val isImporting = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        observeRecentGroupsUseCase(limit = 6),
        observeRecentDocumentsUseCase(limit = 8),
        isImporting,
    ) { groups, docs, importing ->
        HomeUiState(
            recentGroups = groups,
            recentDocuments = docs,
            isLoading = false,
            isImporting = importing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = false),
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
        if (imageUris.isEmpty() || isImporting.value) {
            return
        }

        val cappedSelection = ImageImportSupport.capSelection(imageUris)

        viewModelScope.launch {
            isImporting.value = true
            when (val createResult = createDocumentUseCase.createImported()) {
                is ScanlyResult.Success -> {
                    when (val importResult = importImagesUseCase(createResult.value, cappedSelection.items)) {
                        is ScanlyResult.Success -> {
                            _events.emit(HomeEvent.OpenDocument(createResult.value))
                            _events.emit(
                                HomeEvent.ShowMessage(
                                    ImageImportSupport.importResultMessage(
                                        importedCount = cappedSelection.items.size,
                                        truncated = cappedSelection.truncated,
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
            isImporting.value = false
        }
    }
}
