package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.editing.CropQuadEditor
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionDestination
import `in`.c1ph3rj.scanly.feature.camera.replacementCompletionEvent
import `in`.c1ph3rj.scanly.feature.document.DocumentDestination
import `in`.c1ph3rj.scanly.feature.editor.PageCropDestination
import `in`.c1ph3rj.scanly.feature.editor.PageEditorDestination
import `in`.c1ph3rj.scanly.feature.preview.PageImagePreviewDestination
import `in`.c1ph3rj.scanly.testing.InMemoryDocumentRepository
import `in`.c1ph3rj.scanly.testing.InMemoryPageRepository
import `in`.c1ph3rj.scanly.testing.InMemoryScanlyLibrary
import `in`.c1ph3rj.scanly.testing.requireSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionAndPageEditFlowTest {
    private val library = InMemoryScanlyLibrary()
    private val documents = InMemoryDocumentRepository(library)
    private val pages = InMemoryPageRepository(library)

    private val createDocument = CreateDocumentUseCase(documents)
    private val prepareCapture = PreparePageCaptureUseCase(pages)
    private val prepareReplacement = PrepareReplacementCaptureUseCase(pages)
    private val finalizeCapture = FinalizeCapturedPageUseCase(pages)
    private val updatePageEdits = UpdatePageEditsUseCase(pages)
    private val observePages = ObserveDocumentPagesUseCase(pages)
    private val observePage = ObservePageUseCase(pages)

    @Test
    fun addCaptureRoute_keepsScanSessionOpenAndAppendsPage() = runBlocking {
        val documentId = createDocument("Session").requireSuccess()
        assertEquals(
            "camera/session/$documentId",
            ScanSessionDestination.route(documentId),
        )

        val firstDraft = prepareCapture(documentId).requireSuccess()
        assertNull(firstDraft.replacementPageId)
        assertFalse(firstDraft.isReplacement)
        assertEquals(0, firstDraft.pageIndex)
        val firstPageId = finalizeCapture(firstDraft).requireSuccess()
        assertNull(replacementCompletionEvent(firstDraft, firstPageId))

        val secondDraft = prepareCapture(documentId).requireSuccess()
        assertEquals(1, secondDraft.pageIndex)
        assertNotEquals(firstPageId, secondDraft.pageId)
        finalizeCapture(secondDraft).requireSuccess()

        assertEquals(2, observePages(documentId).first().size)
        assertEquals(
            "document/$documentId",
            DocumentDestination.route(documentId),
        )
    }

    @Test
    fun retakeCapture_replacesExistingPageAndReturnsToEditor() = runBlocking {
        val documentId = createDocument("Retake").requireSuccess()
        val originalDraft = prepareCapture(documentId).requireSuccess()
        val pageId = finalizeCapture(originalDraft).requireSuccess()

        val retakeRoute = ScanSessionDestination.route(documentId, replacePageId = pageId)
        assertEquals("camera/session/$documentId?replacePageId=$pageId", retakeRoute)

        val replacementDraft = prepareReplacement(pageId).requireSuccess()
        assertEquals(pageId, replacementDraft.pageId)
        assertEquals(pageId, replacementDraft.replacementPageId)
        assertTrue(replacementDraft.isReplacement)
        assertEquals(0, replacementDraft.pageIndex)

        val capturedPageId = finalizeCapture(replacementDraft).requireSuccess()
        assertEquals(pageId, capturedPageId)
        assertEquals(1, observePages(documentId).first().size)

        val completion = replacementCompletionEvent(replacementDraft, capturedPageId)
        assertNotNull(completion)
        assertEquals(pageId, completion!!.pageId)
        assertEquals(
            "editor/page/$pageId",
            PageEditorDestination.route(completion.pageId),
        )
    }

    @Test
    fun previewEditorAndCropRoutes_matchTypedDestinations() {
        val pageId = "page-22"
        assertEquals("preview/page/$pageId", PageImagePreviewDestination.route(pageId))
        assertEquals("editor/page/$pageId", PageEditorDestination.route(pageId))
        assertEquals("crop/page/$pageId", PageCropDestination.route(pageId))
    }

    @Test
    fun galleryImportCap_limitsSelectionToTenImages() {
        val underCap = ImageImportSupport.capSelection((1..10).toList())
        assertEquals(10, underCap.items.size)
        assertFalse(underCap.truncated)
        assertEquals("Imported 10 images.", ImageImportSupport.importResultMessage(10, false))

        val overCap = ImageImportSupport.capSelection((1..14).map { "image-$it" })
        assertEquals(ImageImportSupport.MAX_IMAGES_PER_IMPORT, overCap.items.size)
        assertTrue(overCap.truncated)
        assertEquals((1..10).map { "image-$it" }, overCap.items)
        assertEquals(
            "Imported 10 images. Only ${ImageImportSupport.MAX_IMAGES_PER_IMPORT} images can be added at a time.",
            ImageImportSupport.importResultMessage(overCap.items.size, overCap.truncated),
        )
    }

    @Test
    fun cropFilterAndAdjustApply_persistOnTheEditedPage() = runBlocking {
        val documentId = createDocument("Edits").requireSuccess()
        val firstPageId = finalizeCapture(prepareCapture(documentId).requireSuccess()).requireSuccess()
        val secondPageId = finalizeCapture(prepareCapture(documentId).requireSuccess()).requireSuccess()

        val cropQuad = CropQuadEditor.rotateClockwise(CropQuadEditor.defaultQuad())
        val adjustments = PageFilterAdjustments(
            brightness = 0.25f,
            contrast = -0.10f,
            saturation = 0.40f,
            sharpness = 0.50f,
        )

        updatePageEdits(
            pageId = firstPageId,
            cropQuad = cropQuad,
            rotationDegrees = 90,
            filterPreset = PageFilterPreset.CLEAN,
            filterAdjustments = adjustments,
            applyFilterToAllPages = false,
        ).requireSuccess()

        val edited = observePage(firstPageId).first()!!
        assertEquals(cropQuad, edited.cropQuad)
        assertEquals(90, edited.rotationDegrees)
        assertEquals(PageFilterPreset.CLEAN, edited.filterPreset)
        assertEquals(adjustments.sanitized(), edited.filterAdjustments)

        val untouched = observePage(secondPageId).first()!!
        assertEquals(PageFilterPreset.AUTO, untouched.filterPreset)
        assertEquals(PageFilterAdjustments.Default, untouched.filterAdjustments)
        assertEquals(0, untouched.rotationDegrees)
    }

    @Test
    fun applyFilterToAllPages_updatesPresetsWithoutCopyingCropOrAdjustments() = runBlocking {
        val documentId = createDocument("Bulk filter").requireSuccess()
        val firstPageId = finalizeCapture(prepareCapture(documentId).requireSuccess()).requireSuccess()
        val secondPageId = finalizeCapture(prepareCapture(documentId).requireSuccess()).requireSuccess()

        val cropQuad = CropQuadEditor.defaultQuad(inset = 0.12f)
        val adjustments = PageFilterAdjustments(brightness = 0.30f)
        updatePageEdits(
            pageId = firstPageId,
            cropQuad = cropQuad,
            rotationDegrees = 180,
            filterPreset = PageFilterPreset.RECEIPT,
            filterAdjustments = adjustments,
            applyFilterToAllPages = true,
        ).requireSuccess()

        val edited = observePage(firstPageId).first()!!
        val other = observePage(secondPageId).first()!!
        assertEquals(PageFilterPreset.RECEIPT, edited.filterPreset)
        assertEquals(PageFilterPreset.RECEIPT, other.filterPreset)
        assertEquals(cropQuad, edited.cropQuad)
        assertNull(other.cropQuad)
        assertEquals(180, edited.rotationDegrees)
        assertEquals(0, other.rotationDegrees)
        assertEquals(adjustments.sanitized(), edited.filterAdjustments)
        assertEquals(PageFilterAdjustments.Default, other.filterAdjustments)
    }
}
