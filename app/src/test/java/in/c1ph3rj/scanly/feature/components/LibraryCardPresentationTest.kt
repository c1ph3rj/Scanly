package `in`.c1ph3rj.scanly.feature.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCardPresentationTest {

    @Test
    fun coverAspectIsPortraitPaperNotSquare() {
        val aspect = libraryCardCoverAspectRatio()
        assertEquals(LIBRARY_PAGE_COVER_ASPECT_RATIO, aspect, 0.0001f)
        assertTrue(aspect < 1f)
        assertFalse(aspect == 1f)
    }

    @Test
    fun secondaryActionsLiveOnOverflowOrLongPressNotInline() {
        assertEquals(
            LibraryCardActionPlacement.OverflowOrLongPress,
            libraryCardActionPlacement(),
        )
        assertEquals(
            listOf(LibraryCardActionPlacement.OverflowOrLongPress),
            LibraryCardActionPlacement.entries.toList(),
        )
    }

    @Test
    fun documentOverflowIncludesRenameMoveAndDeleteWhenOffered() {
        assertEquals(
            listOf(
                LibraryCardSecondaryAction.Rename,
                LibraryCardSecondaryAction.Move,
                LibraryCardSecondaryAction.Delete,
            ),
            libraryCardSecondaryActions(
                showRename = true,
                canMove = true,
                showDelete = true,
            ),
        )
    }

    @Test
    fun folderMemberOverflowUsesRemoveInsteadOfDelete() {
        assertEquals(
            listOf(LibraryCardSecondaryAction.RemoveFromFolder),
            libraryCardSecondaryActions(
                showRename = false,
                canMove = false,
                showDelete = true,
                deleteIsRemoveFromFolder = true,
            ),
        )
        assertEquals(
            "Remove from folder",
            libraryCardSecondaryActionLabel(LibraryCardSecondaryAction.RemoveFromFolder),
        )
    }

    @Test
    fun homeRecentsWithNoCallbacksHaveNoSecondaryActions() {
        assertTrue(
            libraryCardSecondaryActions(
                showRename = false,
                canMove = false,
                showDelete = false,
            ).isEmpty(),
        )
    }

    @Test
    fun documentMetaShowsPageCountAndDate() {
        assertEquals("1 page  ·  16 Sep 2026", formatDocumentCardMeta(1, "16 Sep 2026"))
        assertEquals("3 pages  ·  16 Sep 2026", formatDocumentCardMeta(3, "16 Sep 2026"))
        assertEquals("2 pages", formatDocumentCardMeta(2, "  "))
    }

    @Test
    fun folderMetaShowsDocumentAndPageCounts() {
        assertEquals("1 document", formatGroupCardMeta(1, 0))
        assertEquals("2 documents  ·  1 page", formatGroupCardMeta(2, 1))
        assertEquals("4 documents  ·  12 pages", formatGroupCardMeta(4, 12))
    }
}
