package `in`.c1ph3rj.scanly.feature.sign.editor

import `in`.c1ph3rj.scanly.domain.model.NormRect
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** "13 Oct 2026". */
fun formatSignDate(millis: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(java.util.Date(millis))

/**
 * Owns the shared sign editor state. Hosts supply pages, signatures and marks, and forward
 * [SignEditorAction]s; all rules live in [SignEditorReducer].
 */
class SignEditorController(
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val today: () -> String = { formatSignDate() },
) {
    private val _state = MutableStateFlow(SignEditorUiState())
    val state: StateFlow<SignEditorUiState> = _state.asStateFlow()

    /** Ink used for new marks; updated only by [SignEditorAction.SetInk]. */
    private var lastInk: SignInk = SignInk.Default

    fun setPages(pages: List<SignEditorPage>) {
        _state.value = SignEditorReducer.setPages(_state.value, pages)
    }

    fun setSignatures(signatures: List<SavedSignature>) {
        _state.value = SignEditorReducer.setSignatures(_state.value, signatures)
    }

    /** Loads existing marks (e.g. reopening a signed page); does not mark dirty, clears selection. */
    fun setMarks(marks: List<SignMark>) {
        _state.value = SignEditorReducer.setMarks(_state.value, marks)
    }

    fun marks(): List<SignMark> = _state.value.marks

    fun onAction(action: SignEditorAction) {
        val current = _state.value
        val env = SignEditorEnv(defaultInk = lastInk, newId = idFactory, today = today)
        val next = SignEditorReducer.reduce(current, action, env)
        if (action is SignEditorAction.SetInk && next !== current) {
            lastInk = action.ink
        }
        _state.value = next
    }
}

/** Dependencies the reducer needs for placing marks, so it stays deterministic in tests. */
internal class SignEditorEnv(
    val defaultInk: SignInk,
    val newId: () -> String,
    val today: () -> String,
)

private const val SignatureWidth = 0.35f
private const val InitialsWidth = 0.16f
private const val DateWidth = 0.24f
private const val DateAspect = 5.5f
private const val TextWidth = 0.30f
private const val TextAspect = 6.5f
private const val CheckboxWidth = 0.05f
private const val CheckboxAspect = 1f
private const val MaxSignatureHeight = 0.5f
private const val CascadeStep = 0.03f
private const val CascadeTolerance = 0.01f
private const val MaxCascadeTries = 10
private const val MaxTextLength = 80
private const val MaxTextWidth = 0.9f
private const val TextCharWidth = 0.74f * 0.56f
private const val TextPadding = 0.3f
private const val MinAspect = 0.01f
private const val DefaultPageAspect = 1f

/** Pure state transitions for the sign editor. Returns the same instance when nothing changes. */
internal object SignEditorReducer {

    fun setPages(state: SignEditorUiState, pages: List<SignEditorPage>): SignEditorUiState =
        state.copy(pages = pages, currentPage = clampPage(state.currentPage, pages))

    fun setSignatures(state: SignEditorUiState, signatures: List<SavedSignature>): SignEditorUiState =
        state.copy(signatures = signatures)

    fun setMarks(state: SignEditorUiState, marks: List<SignMark>): SignEditorUiState =
        state.copy(marks = marks, selectedMarkId = null, editingTextMarkId = null)

    fun reduce(state: SignEditorUiState, action: SignEditorAction, env: SignEditorEnv): SignEditorUiState =
        when (action) {
            is SignEditorAction.SelectMark -> selectMark(state, action.id)
            is SignEditorAction.SelectTool -> state.copy(activeTool = action.tool)
            is SignEditorAction.ChangePage -> changePage(state, action.index)
            is SignEditorAction.PlaceSignature -> placeSignature(state, action, env)
            is SignEditorAction.PlaceTool -> placeTool(state, action, env)
            is SignEditorAction.UpdateBox -> updateMark(state, action.id) { it.withBox(action.box.clampedToPage()) }
            is SignEditorAction.SetInk -> updateMark(state, action.id) { it.withInk(action.ink) }
            is SignEditorAction.Duplicate -> duplicate(state, action.id, env)
            is SignEditorAction.Delete -> delete(state, action.id)
            is SignEditorAction.StartEditText -> startEditText(state, action.id)
            is SignEditorAction.CommitText -> commitText(state, action.id, action.text)
            SignEditorAction.CancelEditText -> cancelEditText(state)
        }

    private fun selectMark(state: SignEditorUiState, id: String?): SignEditorUiState {
        val exists = id != null && findMark(state, id) != null
        return state.copy(selectedMarkId = if (exists) id else null)
    }

    private fun changePage(state: SignEditorUiState, index: Int): SignEditorUiState {
        val page = clampPage(index, state.pages)
        val selected = state.selectedMark
        val clearSelection = selected != null && selected.pageIndex != page
        return state.copy(
            currentPage = page,
            selectedMarkId = if (clearSelection) null else state.selectedMarkId,
        )
    }

    private fun placeSignature(
        state: SignEditorUiState,
        action: SignEditorAction.PlaceSignature,
        env: SignEditorEnv,
    ): SignEditorUiState {
        val pageAspect = pageAspect(state, action.pageIndex) ?: return state
        val signature = action.signature
        val width = if (signature.kind == SignatureKind.INITIALS) InitialsWidth else SignatureWidth
        val boxAspect = signature.aspectRatio.coerceAtLeast(MinAspect)

        var boxWidth = width
        var boxHeight = width * pageAspect / boxAspect
        if (boxHeight > MaxSignatureHeight) {
            boxHeight = MaxSignatureHeight
            boxWidth = MaxSignatureHeight * boxAspect / pageAspect
        }

        val box = placeBox(state, action.pageIndex, boxWidth, boxHeight)
        val mark = SignMark.Image(
            id = env.newId(),
            pageIndex = action.pageIndex,
            box = box,
            ink = env.defaultInk,
            imagePath = signature.imagePath,
            aspectRatio = signature.aspectRatio,
            kind = signature.kind,
        )
        return addMark(state, mark)
    }

    private fun placeTool(
        state: SignEditorUiState,
        action: SignEditorAction.PlaceTool,
        env: SignEditorEnv,
    ): SignEditorUiState {
        if (action.tool == SignTool.Signature || action.tool == SignTool.Initials) return state
        val pageIndex = action.pageIndex
        val pageAspect = pageAspect(state, pageIndex) ?: return state

        val (width, boxAspect) = when (action.tool) {
            SignTool.Date -> DateWidth to DateAspect
            SignTool.Text -> TextWidth to TextAspect
            else -> CheckboxWidth to CheckboxAspect
        }
        val box = placeBox(state, pageIndex, width, width * pageAspect / boxAspect)
        val id = env.newId()
        val mark: SignMark = when (action.tool) {
            SignTool.Date -> SignMark.Date(id, pageIndex, box, env.defaultInk, env.today())
            SignTool.Text -> SignMark.Text(id, pageIndex, box, env.defaultInk, "")
            else -> SignMark.Checkbox(id, pageIndex, box, env.defaultInk)
        }
        val next = addMark(state, mark)
        return if (mark is SignMark.Text) next.copy(editingTextMarkId = id) else next
    }

    private fun duplicate(state: SignEditorUiState, id: String, env: SignEditorEnv): SignEditorUiState {
        val source = findMark(state, id) ?: return state
        val copy = source
            .withId(env.newId())
            .withBox(source.box.offsetBy(CascadeStep, CascadeStep).clampedToPage())
        return addMark(state, copy)
    }

    private fun delete(state: SignEditorUiState, id: String): SignEditorUiState {
        if (findMark(state, id) == null) return state
        return state.copy(
            marks = state.marks.filterNot { it.id == id },
            selectedMarkId = if (state.selectedMarkId == id) null else state.selectedMarkId,
            editingTextMarkId = if (state.editingTextMarkId == id) null else state.editingTextMarkId,
            isDirty = true,
        )
    }

    private fun startEditText(state: SignEditorUiState, id: String): SignEditorUiState {
        val mark = findMark(state, id)
        if (mark !is SignMark.Text && mark !is SignMark.Date) return state
        return state.copy(editingTextMarkId = id)
    }

    private fun commitText(state: SignEditorUiState, id: String, raw: String): SignEditorUiState {
        val mark = findMark(state, id) ?: return state
        if (mark !is SignMark.Text && mark !is SignMark.Date) return state

        val text = normaliseText(raw)
        if (text.isEmpty()) return delete(state, id)

        // Keep the box height and left edge; the width follows the new text length.
        val pageAspect = pageAspect(state, mark.pageIndex) ?: DefaultPageAspect
        val height = mark.box.height
        val textAspect = TextCharWidth * text.length + TextPadding
        val width = (height * textAspect / pageAspect).coerceAtMost(MaxTextWidth)
        val box = NormRect(mark.box.left, mark.box.top, width, height).clampedToPage()

        val resized: SignMark = when (mark) {
            is SignMark.Text -> mark.copy(text = text, box = box)
            is SignMark.Date -> mark.copy(text = text, box = box)
            else -> return state
        }
        return state.copy(
            marks = state.marks.map { if (it.id == id) resized else it },
            editingTextMarkId = null,
            isDirty = true,
        )
    }

    private fun cancelEditText(state: SignEditorUiState): SignEditorUiState {
        val editingId = state.editingTextMarkId ?: return state
        val cleared = state.copy(editingTextMarkId = null)
        val mark = findMark(cleared, editingId)
        if (mark is SignMark.Text && mark.text.isBlank()) return delete(cleared, editingId)
        return cleared
    }

    private inline fun updateMark(
        state: SignEditorUiState,
        id: String,
        transform: (SignMark) -> SignMark,
    ): SignEditorUiState {
        if (findMark(state, id) == null) return state
        return state.copy(
            marks = state.marks.map { if (it.id == id) transform(it) else it },
            isDirty = true,
        )
    }

    private fun addMark(state: SignEditorUiState, mark: SignMark): SignEditorUiState =
        state.copy(
            marks = state.marks + mark,
            selectedMarkId = mark.id,
            currentPage = mark.pageIndex,
            isDirty = true,
        )

    /** Centred on the page, cascaded diagonally while another mark sits at the same top-left. */
    private fun placeBox(state: SignEditorUiState, pageIndex: Int, width: Float, height: Float): NormRect {
        var box = NormRect.centeredAt(0.5f, 0.5f, width, height).clampedToPage()
        var tries = 0
        while (tries < MaxCascadeTries && isOccupied(state, pageIndex, box)) {
            box = box.offsetBy(CascadeStep, CascadeStep).clampedToPage()
            tries++
        }
        return box
    }

    private fun isOccupied(state: SignEditorUiState, pageIndex: Int, box: NormRect): Boolean =
        state.marks.any { mark ->
            mark.pageIndex == pageIndex &&
                abs(mark.box.left - box.left) <= CascadeTolerance &&
                abs(mark.box.top - box.top) <= CascadeTolerance
        }

    private fun normaliseText(raw: String): String =
        raw.replace(Regex("\\s+"), " ").trim().take(MaxTextLength).trim()

    private fun findMark(state: SignEditorUiState, id: String): SignMark? =
        state.marks.firstOrNull { it.id == id }

    private fun pageAspect(state: SignEditorUiState, pageIndex: Int): Float? =
        state.pages.firstOrNull { it.index == pageIndex }?.aspectRatio?.coerceAtLeast(MinAspect)

    private fun clampPage(index: Int, pages: List<SignEditorPage>): Int {
        if (pages.isEmpty()) return 0
        return index.coerceIn(pages.minOf { it.index }, pages.maxOf { it.index })
    }
}
