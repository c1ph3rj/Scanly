package `in`.c1ph3rj.scanly.feature.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.LibraryAccessState
import `in`.c1ph3rj.scanly.domain.usecase.ConnectLibraryUseCase
import `in`.c1ph3rj.scanly.domain.usecase.InitializeLibraryUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveLibraryAccessUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RebuildLibraryIndexUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryStartupViewModel @Inject constructor(
    observeLibraryAccessUseCase: ObserveLibraryAccessUseCase,
    private val initializeLibraryUseCase: InitializeLibraryUseCase,
    private val connectLibraryUseCase: ConnectLibraryUseCase,
    private val rebuildLibraryIndexUseCase: RebuildLibraryIndexUseCase,
) : ViewModel() {
    val state: StateFlow<LibraryAccessState> = observeLibraryAccessUseCase()

    init {
        viewModelScope.launch { initializeLibraryUseCase() }
    }

    fun connect(treeUri: String) {
        viewModelScope.launch {
            when (connectLibraryUseCase(treeUri)) {
                is ScanlyResult.Success -> Unit
                is ScanlyResult.Failure -> Unit
            }
        }
    }

    fun rebuildIndex() {
        viewModelScope.launch { rebuildLibraryIndexUseCase() }
    }
}
