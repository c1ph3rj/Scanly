package `in`.c1ph3rj.scanly.feature.naming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.NamingTemplateRenderer
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.domain.model.NamingTemplate
import `in`.c1ph3rj.scanly.domain.usecase.DeleteNamingTemplateUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveNamingTemplatesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveNamingTemplateUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetDefaultNamingTemplateUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add / edit dialog. [templateId] is null when adding. */
data class NamingTemplateEditor(
    val templateId: String?,
    val initialPattern: String,
    val isSaving: Boolean = false,
    /** The last save was rejected by the repository; shown in the dialog until the pattern changes. */
    val saveFailed: Boolean = false,
)

/** True when another template (not [editingId]) already has [pattern], ignoring case and outer spaces. */
internal fun isDuplicatePattern(
    pattern: String,
    templates: List<NamingTemplate>,
    editingId: String?,
): Boolean {
    val trimmed = pattern.trim()
    if (trimmed.isEmpty()) return false
    return templates.any { it.id != editingId && it.pattern.trim().equals(trimmed, ignoreCase = true) }
}

data class NamingTemplatesUiState(
    val templates: List<NamingTemplate> = emptyList(),
    val isLoaded: Boolean = false,
    val editor: NamingTemplateEditor? = null,
    val pendingDelete: NamingTemplate? = null,
) {
    /** Null means the built-in `DocumentTitleFormat.default`. */
    val defaultTemplate: NamingTemplate? get() = templates.firstOrNull { it.isDefault }
}

sealed interface NamingTemplatesEvent {
    data class ShowMessage(val message: String) : NamingTemplatesEvent
}

@HiltViewModel
class NamingTemplatesViewModel @Inject constructor(
    observeNamingTemplatesUseCase: ObserveNamingTemplatesUseCase,
    private val saveNamingTemplateUseCase: SaveNamingTemplateUseCase,
    private val deleteNamingTemplateUseCase: DeleteNamingTemplateUseCase,
    private val setDefaultNamingTemplateUseCase: SetDefaultNamingTemplateUseCase,
) : ViewModel() {

    private val editor = MutableStateFlow<NamingTemplateEditor?>(null)
    private val pendingDelete = MutableStateFlow<NamingTemplate?>(null)

    private val _events = MutableSharedFlow<NamingTemplatesEvent>()
    val events: SharedFlow<NamingTemplatesEvent> = _events.asSharedFlow()

    val uiState: StateFlow<NamingTemplatesUiState> = combine(
        observeNamingTemplatesUseCase(),
        editor,
        pendingDelete,
    ) { templates, openEditor, deleteTarget ->
        NamingTemplatesUiState(
            templates = templates,
            isLoaded = true,
            editor = openEditor,
            pendingDelete = deleteTarget,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NamingTemplatesUiState(),
    )

    fun startAddingTemplate() {
        editor.value = NamingTemplateEditor(templateId = null, initialPattern = "")
    }

    fun startEditingTemplate(template: NamingTemplate) {
        editor.value = NamingTemplateEditor(templateId = template.id, initialPattern = template.pattern)
    }

    fun dismissEditor() {
        if (editor.value?.isSaving == true) return
        editor.value = null
    }

    /** Clears a save failure once the user changes the pattern. */
    fun onPatternEdited() {
        editor.update { current ->
            if (current?.saveFailed == true) current.copy(saveFailed = false) else current
        }
    }

    fun saveTemplate(pattern: String) {
        val current = editor.value ?: return
        if (current.isSaving || NamingTemplateRenderer.validate(pattern).isNotEmpty()) return
        if (isDuplicatePattern(pattern, uiState.value.templates, current.templateId)) return
        editor.value = current.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            when (val result = saveNamingTemplateUseCase(current.templateId, pattern.trim())) {
                is ScanlyResult.Success -> editor.value = null
                is ScanlyResult.Failure -> editor.update { it?.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** [templateId] null makes the built-in format the default again. */
    fun setDefaultTemplate(templateId: String?) {
        if (uiState.value.defaultTemplate?.id == templateId) return
        viewModelScope.launch {
            val result = setDefaultNamingTemplateUseCase(templateId)
            if (result is ScanlyResult.Failure) {
                _events.emit(NamingTemplatesEvent.ShowMessage(appString(R.string.naming_default_failed)))
            }
        }
    }

    fun requestDelete(template: NamingTemplate) {
        pendingDelete.value = template
    }

    fun dismissDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch {
            val result = deleteNamingTemplateUseCase(target.id)
            if (result is ScanlyResult.Failure) {
                _events.emit(NamingTemplatesEvent.ShowMessage(appString(R.string.naming_delete_failed)))
            }
        }
    }
}
