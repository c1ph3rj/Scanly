package `in`.c1ph3rj.scanly.feature.library

import `in`.c1ph3rj.scanly.domain.model.SearchScope
import `in`.c1ph3rj.scanly.domain.search.LibrarySearchRanker
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Isolated screen fixtures: does not create, delete, or rename persisted library data. */
@RunWith(AndroidJUnit4::class)
class LibraryRedesignTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private var openedDocument: String? = null
    private var openedGroup: String? = null

    @Test
    fun darkGridAndListKeepDocumentActionsAndFolderFiltering() {
        showLibrary(dark = true)
        composeRule.onNodeWithText("Lease agreement").assertIsDisplayed()
        assertControlsStayInsideCards()
        capture("library-dark-grid")
        val firstCard = composeRule.onNodeWithText("Lease agreement")
        firstCard.performTouchInput { down(center) }
        Thread.sleep(220) // Allow Android's delayed press indication to render before releasing.
        capture("library-dark-grid-pressed", settle = false)
        firstCard.performTouchInput { up() }
        composeRule.onAllNodesWithContentDescription("More options")[0].performClick()
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Move to folder").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.onNodeWithContentDescription("List view").performClick()
        composeRule.onNodeWithContentDescription("List view").assertIsSelected()
        assertControlsStayInsideCards()
        capture("library-dark-list")
        assertTargetsInsideScreen(ScanlyTestTags.LIBRARY_SCREEN,
            listOf("Search library", "Sort library", "Grid view", "List view"))
        composeRule.onNodeWithText("Previous 7 days").assertExists()
        composeRule.onNodeWithText("Work").performClick()
        assertEquals("work", openedGroup)
        composeRule.onNodeWithText("See all").performClick()
        composeRule.onNodeWithTag(ScanlyTestTags.LIBRARY_SEARCH).assertIsDisplayed()
        composeRule.onNodeWithText("Documents").assertDoesNotExist()
        composeRule.onNodeWithText("Organise scans").assertIsDisplayed()
        composeRule.onNodeWithText("Receipts").performTouchInput { longClick() }
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        capture("library-dark-folders")
        composeRule.onNodeWithContentDescription("Back to library").performClick()
        composeRule.onNodeWithText("Documents").assertIsDisplayed()
        composeRule.onNodeWithText("Invoice INV-2041").performClick()
        assertEquals("invoice", openedDocument)
    }

    @Test
    fun lightGridCanOpenDedicatedSearchAndReturnWithoutFilteringLibrary() {
        showLibrary(dark = false)
        capture("library-light-grid")
        composeRule.onNodeWithContentDescription("List view").performClick()
        capture("library-light-list")
        composeRule.onNodeWithContentDescription("Grid view").performClick()
        composeRule.onNodeWithTag(ScanlyTestTags.LIBRARY_SEARCH).performClick()
        composeRule.onNodeWithTag(ScanlyTestTags.SEARCH_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ScanlyTestTags.SEARCH_INPUT).performTextReplacement("invoice")
        composeRule.onNodeWithTag(ScanlyTestTags.SEARCH_INPUT).performImeAction()
        composeRule.onNodeWithText("Invoice INV-2041").assertIsDisplayed()
        composeRule.onNodeWithText("Lease agreement").assertDoesNotExist()
        composeRule.onNodeWithText("All").assertDoesNotExist()
        // Results are grouped: only the Documents heading appears for a document-only match.
        composeRule.onNodeWithText("Folders").assertDoesNotExist()
        composeRule.onNodeWithText("Documents").assertIsDisplayed()
        assertTargetsInsideScreen(ScanlyTestTags.SEARCH_SCREEN, listOf("Back", "Clear search"))
        assertContained(composeRule.onNodeWithContentDescription("Clear search").fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithTag(ScanlyTestTags.SEARCH_FIELD).fetchSemanticsNode().boundsInRoot)
        capture("search-light-results")
        composeRule.onNodeWithText("Invoice INV-2041").performClick()
        assertEquals("invoice", openedDocument)
        composeRule.onNodeWithContentDescription("Clear search").performClick()
        composeRule.onNodeWithTag(ScanlyTestTags.SEARCH_INPUT).performTextReplacement("no-match")
        composeRule.onNodeWithText("No results found").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Lease agreement").assertIsDisplayed()
        composeRule.onNodeWithText("Thermodynamics notes").assertIsDisplayed()
    }

    @Test
    fun pureBlackGridPreservesTheme() {
        showLibrary(dark = true, pureBlack = true)
        composeRule.onNodeWithText("Lease agreement").assertIsDisplayed()
        capture("library-pure-black-grid")
    }

    @Test
    fun compactListKeepsLongTitlesAndActionsWithinTheRow() {
        showLibrary(dark = true, longTitles = true)
        composeRule.onNodeWithContentDescription("List view").performClick()
        val firstRow = composeRule.onNodeWithText("Scan 13-Oct-2026 10:39 pm").fetchSemanticsNode().boundsInRoot
        val action = composeRule.onAllNodesWithContentDescription("More options")[0].fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= firstRow.left && action.right <= firstRow.right)
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("List row should remain compact with a two-line title", firstRow.height / density <= 108f)
        val secondRow = composeRule.onNodeWithText("Imported 13-Oct-2026 9:31 pm").fetchSemanticsNode().boundsInRoot
        val gap = (secondRow.top - firstRow.bottom) / density
        assertTrue("Adjacent rows are separated by the 3 dp segment gap", gap in 2.5f..3.5f)
        composeRule.onNodeWithText("Document").assertDoesNotExist()
        composeRule.onNodeWithText("Empty").assertDoesNotExist()
        capture("library-dark-list-long-titles")
    }

    private fun assertControlsStayInsideCards() {
        val firstCard = composeRule.onNodeWithText("Lease agreement").fetchSemanticsNode().boundsInRoot
        val action = composeRule.onAllNodesWithContentDescription("More options")[0].fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= firstCard.left && action.right <= firstCard.right)
        assertTrue(action.top >= firstCard.top && action.bottom <= firstCard.bottom)
        val switch = composeRule.onNodeWithTag(ScanlyTestTags.LIBRARY_LAYOUT_SWITCH).fetchSemanticsNode().boundsInRoot
        listOf("Grid view", "List view").forEach { label ->
            val button = composeRule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(button.left >= switch.left && button.right <= switch.right)
            assertTrue(button.top >= switch.top && button.bottom <= switch.bottom)
        }
    }

    private fun assertContained(inner: androidx.compose.ui.geometry.Rect, outer: androidx.compose.ui.geometry.Rect) {
        assertTrue("Touch target must stay within its container", inner.left >= outer.left && inner.right <= outer.right &&
            inner.top >= outer.top && inner.bottom <= outer.bottom)
    }

    private fun assertTargetsInsideScreen(tag: String, labels: List<String>) {
        val screen = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        labels.forEach { label ->
            val target = composeRule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertContained(target, screen)
            val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
            assertTrue("Touch target should be at least 48 dp", target.width / density >= 47.9f && target.height / density >= 47.9f)
        }
    }

    private fun showLibrary(dark: Boolean, pureBlack: Boolean = false, longTitles: Boolean = false) {
        val fixture = fixture(longTitles)
        composeRule.setContent {
            var state by remember { mutableStateOf(fixture) }
            var search by remember { mutableStateOf(false) }
            var searchQuery by remember { mutableStateOf("") }
            ScanlyTheme(darkTheme = dark, pureBlack = pureBlack) {
                if (search) {
                    LibrarySearchScreen(
                        uiState = LibrarySearchUiState(
                            query = searchQuery,
                            results = LibrarySearchRanker.rank(
                                query = SearchQuery.parse(searchQuery),
                                documents = fixture.allDocuments,
                                groups = fixture.groups,
                                documentKinds = emptyMap(),
                                textHits = emptyList(),
                                scope = SearchScope.EVERYWHERE,
                            ),
                            isLoading = false,
                            hasLibrary = true,
                        ),
                        onBack = { search = false },
                        onOpenDocument = { openedDocument = it },
                        onOpenGroup = {},
                        onOpenPageText = { _, _, _ -> },
                        onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = "" },
                        onScopeChange = {},
                        onKindChange = {},
                    )
                } else {
                    LibraryScreen(
                        uiState = state,
                        snackbarHostState = remember { SnackbarHostState() },
                        onCreateDocument = { _, _ -> }, onRenameDocument = { _, _ -> }, onDeleteDocument = {},
                        onCreateGroup = {}, onRenameGroup = { _, _ -> }, onDeleteGroup = {},
                        onMoveDocumentToGroup = { _, _ -> }, onMoveDocumentToNewGroup = { _, _ -> },
                        onOpenDocument = { openedDocument = it }, onOpenScanSession = {}, onOpenGroup = { openedGroup = it },
                        onOpenSearch = { search = true },
                        onGroupSelected = { state = state.copy(selectedGroupId = it, selectedTab = LibraryTab.Documents) },
                        onTabSelected = { state = state.copy(selectedTab = it, selectedGroupId = null) },
                        onSortSelected = { state = state.copy(sortOption = it) },
                        onSuggestTitle = { "New document" }, onSuggestGroupTitle = { "New folder" },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun fixture(longTitles: Boolean = false): LibraryUiState {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val titles = if (longTitles) listOf("Scan 13-Oct-2026 10:39 pm", "Imported 13-Oct-2026 9:31 pm",
            "Imported 13-Oct-2026 9:29 pm", "Scan 16-Sept-2026 11:28 am")
        else listOf("Lease agreement", "Invoice INV-2041", "Thermodynamics notes", "Green Basket receipt")
        val ids = listOf("lease", "invoice", "notes", "receipt")
        val documents = titles.mapIndexed { index, title ->
            val file = File(context.cacheDir, "library-preview-$index.png")
            val bitmap = Bitmap.createBitmap(600, 840, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 44, 56); textSize = 26f }
            canvas.drawText(title.uppercase(), 36f, 65f, paint)
            paint.textSize = 15f
            canvas.drawText("SCANLY   /   DOCUMENT PREVIEW", 36f, 96f, paint)
            paint.color = Color.LTGRAY
            for (line in 0..22) canvas.drawRect(36f, 135f + line * 25, if (line % 4 == 0) 400f else 556f, 138f + line * 25, paint)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            ScanDocument(ids[index], title, listOf(6, 1, 3, 1)[index], file.absolutePath, context.cacheDir.absolutePath,
                now - listOf(0L, 1_000L, 3 * DAY, 40 * DAY)[index], now - listOf(0L, 1_000L, 3 * DAY, 40 * DAY)[index], if (index < 2) "work" else if (index == 3) "receipts" else "personal")
        }
        return LibraryUiState(
            groups = listOf(
                DocumentGroup("work", "Work", 2, 7, documents[0].coverThumbnailPath, now, now),
                DocumentGroup("receipts", "Receipts", 1, 1, documents[3].coverThumbnailPath, now, now),
                DocumentGroup("personal", "Personal", 1, 3, null, now, now),
            ),
            allDocuments = documents,
        )
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }

    private fun capture(name: String, settle: Boolean = true) {
        if (settle) composeRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "library-v3").apply { mkdirs() }
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(directory, "$name.png")))
    }
}
