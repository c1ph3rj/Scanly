package `in`.c1ph3rj.scanly.domain.search

import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.DateFilter
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.FolderFilter
import `in`.c1ph3rj.scanly.domain.model.HighlightedText
import `in`.c1ph3rj.scanly.domain.model.MatchTier
import `in`.c1ph3rj.scanly.domain.model.PageTextHit
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.SearchFilters
import `in`.c1ph3rj.scanly.domain.model.SearchScope
import `in`.c1ph3rj.scanly.domain.model.SearchSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchRankerTest {
    private val lease = doc("lease", "Lease agreement", updated = 10, groupId = "home")
    private val invoice = doc("inv", "Invoice INV-2041", updated = 30)
    private val leaseNotes = doc("notes", "Lease notes", updated = 50)
    private val unrelated = doc("other", "Holiday photos", updated = 60)
    private val documents = listOf(lease, invoice, leaseNotes, unrelated)
    private val groups = listOf(DocumentGroup("home", "Home", 1, 1, null, 0, 0))
    private val hits = listOf(
        hit("lease", page = 1, count = 1),
        hit("lease", page = 0, count = 2),
        hit("inv", page = 0, count = 1),
    )

    @Test
    fun exactWordPagesOutrankPrefixOnlyPages() {
        val results = rank(
            "zebra",
            textHits = listOf(hit("other", page = 0, count = 1), hit("inv", page = 0, count = 1, tier = MatchTier.EXACT)),
        )
        // Holiday photos is newer, but only the invoice has the whole word.
        assertEquals(listOf("inv", "other"), results.textMatches.map { it.document.id })
    }

    @Test
    fun closeOnlyDocumentsComeAfterEverythingTheUserTyped() {
        val results = rank(
            "lease",
            textHits = listOf(
                hit("other", page = 0, count = 5, tier = MatchTier.CLOSE),
                hit("inv", page = 0, count = 1),
            ),
            closeTerms = listOf("least"),
        )
        assertEquals(listOf("inv"), results.textMatches.map { it.document.id })
        assertEquals(setOf("lease", "notes"), results.nameMatches.map { it.document.id }.toSet())
        assertEquals(listOf("other"), results.closeMatches.map { it.document.id })
        assertEquals(listOf("least"), results.closeTerms)
        assertEquals(4, results.documentCount)
    }

    @Test
    fun nameMatchWithOnlyCloseTextStaysWithNameMatches() {
        val results = rank("lease", textHits = listOf(hit("lease", page = 0, count = 1, tier = MatchTier.CLOSE)))
        assertTrue(results.closeMatches.isEmpty())
        assertTrue(results.nameMatches.any { it.document.id == "lease" && it.pageHits.size == 1 })
    }

    @Test
    fun closeTermsAreDroppedWhenNoCloseMatchSurvives() {
        val results = rank("lease", textHits = hits, closeTerms = listOf("least"))
        assertTrue(results.closeTerms.isEmpty())
    }

    @Test
    fun ocrVariantPagesRankBelowTypedMatchesButStayInText() {
        val results = rank(
            "zebra",
            textHits = listOf(
                hit("other", page = 0, count = 1, tier = MatchTier.OCR_VARIANT),
                hit("inv", page = 0, count = 1),
            ),
        )
        assertEquals(listOf("inv", "other"), results.textMatches.map { it.document.id })
        assertTrue(results.closeMatches.isEmpty())
    }

    @Test
    fun excludedWordsDropDocumentsByNameEvenWithTextHits() {
        val results = rank("lease -notes")
        assertTrue(results.documentCount > 0)
        assertTrue((results.textMatches + results.nameMatches).none { it.document.id == "notes" })
    }

    @Test
    fun folderAndDateFiltersNarrowDocumentsAndHideFolders() {
        val inHome = LibrarySearchRanker.rank(
            SearchQuery.parse("lease"), documents, groups, emptyMap(), hits, SearchScope.EVERYWHERE,
            SearchFilters(folder = FolderFilter.In("home")),
        )
        assertEquals(listOf("lease"), (inHome.textMatches + inHome.nameMatches).map { it.document.id })
        assertTrue(inHome.folders.isEmpty())
        val recent = LibrarySearchRanker.rank(
            SearchQuery.parse("lease"), documents, groups, emptyMap(), hits, SearchScope.EVERYWHERE,
            SearchFilters(date = DateFilter.CUSTOM, customStartUtcMillis = 0, customEndUtcMillis = 0),
            nowMillis = 0, zone = java.time.ZoneOffset.UTC,
        )
        // Every fixture document was changed within the first day of 1970.
        assertEquals(3, recent.documentCount)
    }

    @Test
    fun blankQueryWithFiltersBrowsesInTheChosenOrder() {
        val browse = LibrarySearchRanker.rank(
            SearchQuery.parse(""), documents, groups, emptyMap(), null, SearchScope.EVERYWHERE,
            SearchFilters(folder = FolderFilter.None, sort = SearchSort.NAME),
        )
        assertEquals(listOf("Holiday photos", "Invoice INV-2041", "Lease notes"), browse.nameMatches.map { it.document.title })
    }

    @Test
    fun sortOtherThanBestMatchReordersEachSection() {
        val oldest = LibrarySearchRanker.rank(
            SearchQuery.parse("lease"), documents, groups, emptyMap(), hits, SearchScope.EVERYWHERE,
            SearchFilters(sort = SearchSort.OLDEST),
        )
        assertEquals(listOf("lease", "inv"), oldest.textMatches.map { it.document.id })
        val newest = LibrarySearchRanker.rank(
            SearchQuery.parse("lease"), documents, groups, emptyMap(), hits, SearchScope.EVERYWHERE,
            SearchFilters(sort = SearchSort.NEWEST),
        )
        assertEquals(listOf("inv", "lease"), newest.textMatches.map { it.document.id })
    }

    @Test
    fun moreRelevantPageBeatsMoreMatches() {
        val results = rank(
            "zebra",
            textHits = listOf(
                hit("other", page = 0, count = 6, relevance = 1.0),
                hit("inv", page = 0, count = 1, relevance = 4.0),
            ),
        )
        assertEquals(listOf("inv", "other"), results.textMatches.map { it.document.id })
    }

    @Test
    fun wordsCloseTogetherBreakTies() {
        val results = rank(
            "zebra crossing",
            textHits = listOf(
                hit("other", page = 0, count = 2, relevance = 2.0, proximity = 0.1),
                hit("inv", page = 0, count = 2, relevance = 2.0, proximity = 0.9),
            ),
        )
        assertEquals(listOf("inv", "other"), results.textMatches.map { it.document.id })
    }

    private fun rank(
        query: String,
        textHits: List<PageTextHit>? = hits,
        scope: SearchScope = SearchScope.EVERYWHERE,
        kind: DocumentKind? = null,
        closeTerms: List<String> = emptyList(),
    ) = LibrarySearchRanker.rank(
        SearchQuery.parse(query),
        documents,
        groups,
        mapOf("lease" to DocumentKind.CONTRACT, "inv" to DocumentKind.INVOICE),
        textHits,
        scope,
        SearchFilters(kind = kind),
        closeTerms,
    )

    @Test
    fun textMatchesComeFirstAndNameAlsoMatchingLeads() {
        val results = rank("lease")
        assertEquals(listOf("lease", "inv"), results.textMatches.map { it.document.id })
        assertEquals(listOf("notes"), results.nameMatches.map { it.document.id })
        assertTrue(results.textMatches.first().nameMatched)
        assertEquals(3, results.textMatches.first().textMatchCount)
        assertEquals("Home", results.textMatches.first().folderTitle)
    }

    @Test
    fun pageHitsAreInPageOrder() {
        assertEquals(listOf(0, 1), rank("lease").textMatches.first().pageHits.map { it.pageIndex })
    }

    @Test
    fun nameResultsArriveBeforeTextSearchCompletes() {
        val results = rank("lease", textHits = null)
        assertFalse(results.isTextSearchComplete)
        assertTrue(results.textMatches.isEmpty())
        assertEquals(setOf("lease", "notes"), results.nameMatches.map { it.document.id }.toSet())
    }

    @Test
    fun scopesRestrictMatching() {
        assertTrue(rank("lease", scope = SearchScope.NAMES).textMatches.isEmpty())
        val textOnly = rank("lease", scope = SearchScope.TEXT)
        assertTrue(textOnly.nameMatches.isEmpty())
        assertTrue(textOnly.folders.isEmpty())
        assertFalse(textOnly.textMatches.first().nameMatched)
    }

    @Test
    fun kindFilterKeepsOnlyThatKind() {
        assertEquals(listOf("inv"), rank("lease", kind = DocumentKind.INVOICE).textMatches.map { it.document.id })
    }

    @Test
    fun foldersMatchByName() {
        assertEquals(listOf("home"), rank("hom").folders.map { it.group.id })
    }

    @Test
    fun blankQueryWithTypeBrowsesThatType() {
        val results = rank("", kind = DocumentKind.CONTRACT)
        assertEquals(listOf("lease"), results.nameMatches.map { it.document.id })
        assertTrue(results.isTextSearchComplete)
    }

    @Test
    fun blankQueryHasNoResults() {
        assertTrue(rank("   ").isEmpty)
    }

    private fun doc(id: String, title: String, updated: Long, groupId: String? = null) = ScanDocument(
        id = id,
        title = title,
        pageCount = 2,
        coverThumbnailPath = null,
        rootDirectoryPath = "",
        createdAtMillis = 0,
        updatedAtMillis = updated,
        groupId = groupId,
    )

    private fun hit(
        documentId: String,
        page: Int,
        count: Int,
        tier: MatchTier = MatchTier.PREFIX,
        relevance: Double? = null,
        proximity: Double = 0.0,
    ) = PageTextHit("$documentId-$page", documentId, page, HighlightedText("…"), count, tier).let { hit ->
        hit.copy(relevance = relevance ?: hit.relevance, proximity = proximity)
    }
}
