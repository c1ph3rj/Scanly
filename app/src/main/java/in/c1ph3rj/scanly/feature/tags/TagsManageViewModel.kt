package `in`.c1ph3rj.scanly.feature.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.domain.model.DocumentTag
import `in`.c1ph3rj.scanly.domain.model.DocumentTagRules
import `in`.c1ph3rj.scanly.domain.usecase.CreateTagUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DeleteTagUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveTagsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RenameTagUseCase
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The name dialog that is open: creating a tag, or renaming [tagId]. */
sealed interface TagNameEditor {
    data object Create : TagNameEditor

    data class Rename(val tagId: String, val currentName: String) : TagNameEditor
}

data class TagsManageUiState(
    /** Every tag with its document count, sorted by name. */
    val tags: List<DocumentTag> = emptyList(),
    val isLoading: Boolean = true,
    val editor: TagNameEditor? = null,
    /** Why the last name was refused (blank, taken, or a save failure); shown under the field. */
    val nameError: String? = null,
    val isSaving: Boolean = false,
    /** Tag awaiting delete confirmation. */
    val pendingDelete: DocumentTag? = null,
)

sealed interface TagsManageEvent {
    data class ShowMessage(val message: String) : TagsManageEvent
}

private data class TagsManageDialogs(
    val editor: TagNameEditor? = null,
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val pendingDeleteId: String? = null,
)

/** Backs the Tags settings screen: lists tags with counts and creates, renames and deletes them. */
@HiltViewModel
class TagsManageViewModel @Inject constructor(
    observeTagsUseCase: ObserveTagsUseCase,
    private val createTagUseCase: CreateTagUseCase,
    private val renameTagUseCase: RenameTagUseCase,
    private val deleteTagUseCase: DeleteTagUseCase,
) : ViewModel() {
    private val dialogs = MutableStateFlow(TagsManageDialogs())

    val uiState: StateFlow<TagsManageUiState> = combine(observeTagsUseCase(), dialogs) { tags, dialogState ->
        TagsManageUiState(
            tags = tags,
            isLoading = false,
            editor = dialogState.editor,
            nameError = dialogState.nameError,
            isSaving = dialogState.isSaving,
            // A tag deleted elsewhere closes its confirmation.
            pendingDelete = dialogState.pendingDeleteId?.let { id -> tags.firstOrNull { it.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsManageUiState())

    private val messages = Channel<TagsManageEvent>(Channel.BUFFERED)
    val events: Flow<TagsManageEvent> = messages.receiveAsFlow()

    fun startCreate() {
        dialogs.update { it.copy(editor = TagNameEditor.Create, nameError = null) }
    }

    fun startRename(tag: DocumentTag) {
        dialogs.update { it.copy(editor = TagNameEditor.Rename(tag.id, tag.name), nameError = null) }
    }

    fun dismissEditor() {
        dialogs.update { it.copy(editor = null, nameError = null, isSaving = false) }
    }

    /** Clears a refusal once the user edits the name again. */
    fun clearNameError() {
        if (dialogs.value.nameError != null) dialogs.update { it.copy(nameError = null) }
    }

    fun submitName(name: String) {
        val editor = dialogs.value.editor ?: return
        if (dialogs.value.isSaving) return
        val refusal = nameRefusal(name, editor)
        if (refusal != null) {
            dialogs.update { it.copy(nameError = refusal) }
            return
        }
        dialogs.update { it.copy(isSaving = true, nameError = null) }
        viewModelScope.launch {
            val failure = when (editor) {
                TagNameEditor.Create -> (createTagUseCase(name) as? ScanlyResult.Failure)
                is TagNameEditor.Rename -> (renameTagUseCase(editor.tagId, name) as? ScanlyResult.Failure)
            }
            if (failure != null) {
                // Keep the dialog open so the user can pick another name.
                dialogs.update { it.copy(isSaving = false, nameError = failure.error.message) }
                return@launch
            }
            dialogs.update { it.copy(editor = null, nameError = null, isSaving = false) }
            if (editor is TagNameEditor.Rename) {
                messages.send(TagsManageEvent.ShowMessage(appString(R.string.tags_manage_renamed)))
            }
        }
    }

    /**
     * Why [name] can't be used, checked here so the message is specific (the repository only
     * reports a generic failure). Renaming a tag to its own name in another case is allowed.
     */
    private fun nameRefusal(name: String, editor: TagNameEditor): String? {
        val normalized = DocumentTagRules.normalizeName(name)
        if (normalized.isEmpty()) return appString(R.string.tags_manage_name_blank)
        val key = DocumentTagRules.key(normalized)
        val ownId = (editor as? TagNameEditor.Rename)?.tagId
        val clash = uiState.value.tags.firstOrNull { it.id != ownId && DocumentTagRules.key(it.name) == key }
        return clash?.let { appString(R.string.tags_manage_name_taken, it.name) }
    }

    fun requestDelete(tag: DocumentTag) {
        dialogs.update { it.copy(pendingDeleteId = tag.id) }
    }

    fun dismissDelete() {
        dialogs.update { it.copy(pendingDeleteId = null) }
    }

    fun confirmDelete() {
        val tagId = dialogs.value.pendingDeleteId ?: return
        dialogs.update { it.copy(pendingDeleteId = null) }
        viewModelScope.launch {
            val message = when (val result = deleteTagUseCase(tagId)) {
                is ScanlyResult.Success -> appString(R.string.tags_manage_deleted)
                is ScanlyResult.Failure -> result.error.message
            }
            messages.send(TagsManageEvent.ShowMessage(message))
        }
    }
}
