package `in`.c1ph3rj.scanly.feature.sign.editor

import `in`.c1ph3rj.scanly.domain.model.NormRect
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignMark

/** The five tools in the editor's bottom row. */
enum class SignTool(val label: String) {
    Signature("Signature"),
    Initials("Initials"),
    Date("Date"),
    Text("Text"),
    Checkbox("Checkbox"),
}

/** One page in the editor. [aspectRatio] is visual width / height. */
data class SignEditorPage(
    val index: Int,
    val aspectRatio: Float,
)

/**
 * Everything the shared sign editor draws. Hosts (PDF tool, document detail) own one
 * [SignEditorController] and only supply pages, page bitmaps and the save action.
 */
data class SignEditorUiState(
    val pages: List<SignEditorPage> = emptyList(),
    val marks: List<SignMark> = emptyList(),
    val selectedMarkId: String? = null,
    val currentPage: Int = 0,
    val activeTool: SignTool = SignTool.Signature,
    val signatures: List<SavedSignature> = emptyList(),
    /** True once anything changed since the editor opened (Cancel asks before discarding). */
    val isDirty: Boolean = false,
    /** Mark whose text is being edited in the text dialog, if any. */
    val editingTextMarkId: String? = null,
) {
    val selectedMark: SignMark? get() = marks.firstOrNull { it.id == selectedMarkId }
}

/** User intents from the editor UI. */
sealed interface SignEditorAction {
    data class SelectMark(val id: String?) : SignEditorAction
    data class SelectTool(val tool: SignTool) : SignEditorAction
    data class ChangePage(val index: Int) : SignEditorAction

    /** Places a saved signature/initials on [pageIndex], centred. */
    data class PlaceSignature(val signature: SavedSignature, val pageIndex: Int) : SignEditorAction

    /** Places a Date, Text or Checkbox mark on [pageIndex], centred. */
    data class PlaceTool(val tool: SignTool, val pageIndex: Int) : SignEditorAction

    /** Moves or resizes: the UI computes the new box (already aspect-locked where needed). */
    data class UpdateBox(val id: String, val box: NormRect) : SignEditorAction
    data class SetInk(val id: String, val ink: SignInk) : SignEditorAction
    data class Duplicate(val id: String) : SignEditorAction
    data class Delete(val id: String) : SignEditorAction
    data class StartEditText(val id: String) : SignEditorAction
    data class CommitText(val id: String, val text: String) : SignEditorAction
    data object CancelEditText : SignEditorAction
}
