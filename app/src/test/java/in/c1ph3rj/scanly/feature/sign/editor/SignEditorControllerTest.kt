package `in`.c1ph3rj.scanly.feature.sign.editor

import `in`.c1ph3rj.scanly.domain.model.NormRect
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.domain.model.SignatureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignEditorControllerTest {

    private val pageAspect = 210f / 297f
    private var counter = 0

    private fun newController() = SignEditorController(
        idFactory = { "id-${++counter}" },
        today = { "1 Oct 2026" },
    )

    private fun SignEditorController.withPages(count: Int = 2): SignEditorController {
        setPages(List(count) { SignEditorPage(index = it, aspectRatio = pageAspect) })
        return this
    }

    private val signature = SavedSignature(
        id = "sig-1",
        kind = SignatureKind.SIGNATURE,
        source = SignatureSource.DRAWN,
        imagePath = "sig.png",
        widthPx = 300,
        heightPx = 100,
        createdAtMillis = 0L,
    )

    @Test
    fun placedSignatureKeepsImageAspectOnPortraitPage() {
        val c = newController().withPages()
        c.onAction(SignEditorAction.PlaceSignature(signature, pageIndex = 0))

        val mark = c.marks().single() as SignMark.Image
        val box = mark.box
        assertEquals(0.35f, box.width, 1e-6f)
        assertEquals(3f, box.width * pageAspect / box.height, 0.01f)
        assertEquals(3f, mark.aspectRatio, 0f)
        assertEquals(0, mark.pageIndex)
    }

    @Test
    fun tallSignatureShrinksWidthSoItFitsHalfThePage() {
        val c = newController().withPages()
        val tall = signature.copy(widthPx = 100, heightPx = 500)
        c.onAction(SignEditorAction.PlaceSignature(tall, pageIndex = 0))

        val box = (c.marks().single() as SignMark.Image).box
        assertEquals(0.5f, box.height, 1e-6f)
        assertEquals(0.2f, box.width * pageAspect / box.height, 0.01f)
    }

    @Test
    fun repeatedPlacementsNeverOverlap() {
        val c = newController().withPages()
        repeat(3) { c.onAction(SignEditorAction.PlaceTool(SignTool.Date, pageIndex = 0)) }

        val boxes = c.marks().map { it.box }
        assertEquals(boxes[0].left, boxes[1].left, 1e-4f)
        assertTrue("second goes below the first", boxes[1].top >= boxes[0].bottom)
        assertTrue("third goes above the first", boxes[2].bottom <= boxes[0].top)
        for (i in boxes.indices) for (j in boxes.indices) if (i < j) {
            val a = boxes[i]
            val b = boxes[j]
            val overlaps = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
            assertFalse("marks $i and $j overlap", overlaps)
        }
    }

    @Test
    fun duplicateOffsetsCopyAndSelectsIt() {
        val c = newController().withPages()
        c.onAction(SignEditorAction.PlaceTool(SignTool.Checkbox, pageIndex = 0))
        val original = c.marks().single()

        c.onAction(SignEditorAction.Duplicate(original.id))

        val copy = c.marks()[1]
        assertEquals("id-2", copy.id)
        assertEquals(original.pageIndex, copy.pageIndex)
        assertEquals(original.box.left + 0.03f, copy.box.left, 1e-6f)
        assertEquals(original.box.top + 0.03f, copy.box.top, 1e-6f)
        assertEquals("id-2", c.state.value.selectedMarkId)
        assertTrue(c.state.value.isDirty)
    }

    @Test
    fun deleteRemovesMarkAndClearsSelection() {
        val c = newController().withPages()
        c.onAction(SignEditorAction.PlaceTool(SignTool.Checkbox, pageIndex = 0))

        c.onAction(SignEditorAction.Delete("id-1"))

        assertTrue(c.marks().isEmpty())
        assertNull(c.state.value.selectedMarkId)
        assertTrue(c.state.value.isDirty)
    }

    @Test
    fun inkIsRememberedForTheNextMark() {
        val c = newController().withPages()
        c.onAction(SignEditorAction.PlaceTool(SignTool.Checkbox, pageIndex = 0))
        assertEquals(SignInk.Default, c.marks().single().ink)

        c.onAction(SignEditorAction.SetInk("id-1", SignInk.RED))
        assertEquals(SignInk.RED, c.marks().single().ink)

        c.onAction(SignEditorAction.PlaceTool(SignTool.Checkbox, pageIndex = 0))
        assertEquals(SignInk.RED, c.marks()[1].ink)
    }

    @Test
    fun committingBlankTextDeletesTheMark() {
        val c = newController().withPages()
        c.onAction(SignEditorAction.PlaceTool(SignTool.Text, pageIndex = 0))
        assertEquals("id-1", c.state.value.editingTextMarkId)

        c.onAction(SignEditorAction.CommitText("id-1", "   \n  "))

        assertTrue(c.marks().isEmpty())
        assertNull(c.state.value.editingTextMarkId)
    }

    @Test
    fun cancellingFreshTextDeletesTheMark() {
        val c = newController().withPages()
        c.onAction(SignEditorAction.PlaceTool(SignTool.Text, pageIndex = 0))

        c.onAction(SignEditorAction.CancelEditText)

        assertTrue(c.marks().isEmpty())
        assertNull(c.state.value.editingTextMarkId)
    }

    @Test
    fun committingTextResizesButKeepsLeftTopAndHeight() {
        val c = newController().withPages()
        c.onAction(SignEditorAction.PlaceTool(SignTool.Text, pageIndex = 0))
        val before = c.marks().single().box

        c.onAction(SignEditorAction.CommitText("id-1", "  Jane \n  Doe  "))

        val mark = c.marks().single() as SignMark.Text
        assertEquals("Jane Doe", mark.text)
        assertEquals(before.left, mark.box.left, 1e-6f)
        assertEquals(before.top, mark.box.top, 1e-6f)
        assertEquals(before.height, mark.box.height, 1e-6f)
        assertTrue(mark.box.width > 0f)
        assertNull(c.state.value.editingTextMarkId)
        assertTrue(c.state.value.isDirty)
    }

    @Test
    fun updateBoxIsClampedToThePage() {
        val c = newController().withPages()
        c.onAction(SignEditorAction.PlaceTool(SignTool.Checkbox, pageIndex = 0))

        c.onAction(SignEditorAction.UpdateBox("id-1", NormRect(0.95f, -0.2f, 0.2f, 0.05f)))

        val box = c.marks().single().box
        assertEquals(0.8f, box.left, 1e-6f)
        assertEquals(0f, box.top, 1e-6f)
        assertEquals(0.2f, box.width, 1e-6f)
        assertEquals(0.05f, box.height, 1e-6f)
        assertTrue(box.right <= 1f + 1e-6f)
    }

    @Test
    fun changingPageClearsSelectionFromAnotherPage() {
        val c = newController().withPages()
        c.onAction(SignEditorAction.PlaceTool(SignTool.Checkbox, pageIndex = 0))
        assertEquals("id-1", c.state.value.selectedMarkId)

        c.onAction(SignEditorAction.ChangePage(1))

        assertEquals(1, c.state.value.currentPage)
        assertNull(c.state.value.selectedMarkId)
    }

    @Test
    fun changingPageKeepsSelectionOnThatPageAndClampsIndex() {
        val c = newController().withPages()
        c.onAction(SignEditorAction.PlaceTool(SignTool.Checkbox, pageIndex = 1))

        c.onAction(SignEditorAction.ChangePage(9))

        assertEquals(1, c.state.value.currentPage)
        assertEquals("id-1", c.state.value.selectedMarkId)
    }

    @Test
    fun setMarksLoadsWithoutMarkingDirty() {
        val c = newController().withPages()
        val mark = SignMark.Checkbox("loaded", 0, NormRect(0.1f, 0.1f, 0.05f, 0.05f), SignInk.BLUE)

        c.setMarks(listOf(mark))

        assertEquals(listOf(mark), c.marks())
        assertFalse(c.state.value.isDirty)
        assertNull(c.state.value.selectedMarkId)
    }
}
