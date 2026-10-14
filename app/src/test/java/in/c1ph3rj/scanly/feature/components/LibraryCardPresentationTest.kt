package `in`.c1ph3rj.scanly.feature.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCardPresentationTest {

    @Test
    fun modifiedDateUsesLocalCalendarDaysAcrossMidnight() {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val now = java.time.Instant.parse("2026-10-12T19:00:00Z").toEpochMilli()
        val today = java.time.Instant.parse("2026-10-12T18:45:00Z").toEpochMilli()
        val yesterday = java.time.Instant.parse("2026-10-12T18:00:00Z").toEpochMilli()
        assertEquals("Today", formatLibraryModifiedDate(today, now, zone))
        assertEquals("Yesterday", formatLibraryModifiedDate(yesterday, now, zone))
        assertTrue(formatLibraryModifiedDate(java.time.Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(), now, zone).contains("2025"))
    }

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

    @Test
    fun sectionedDateShowsTimeForRecentDaysAndDayMonthOtherwise() {
        val zone = java.time.ZoneId.of("UTC")
        val now = java.time.Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()
        val locale = java.util.Locale.ENGLISH
        val yesterdayEvening = java.time.Instant.parse("2026-10-13T22:39:00Z").toEpochMilli()
        assertTrue(formatLibrarySectionedDate(yesterdayEvening, now, zone, locale).contains("10:39"))
        assertTrue(formatLibrarySectionedDate(java.time.Instant.parse("2026-09-16T11:28:00Z").toEpochMilli(), now, zone, locale).startsWith("16 Sep"))
        assertEquals("5 Dec 2025", formatLibrarySectionedDate(java.time.Instant.parse("2025-12-05T08:00:00Z").toEpochMilli(), now, zone, locale))
    }
}
