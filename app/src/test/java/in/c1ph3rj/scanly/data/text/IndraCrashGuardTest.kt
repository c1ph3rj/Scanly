package `in`.c1ph3rj.scanly.data.text

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IndraCrashGuardTest {
    private val store = InMemoryGuardStore()

    /** A new guard over the same store is a new process reading what the last one left behind. */
    private fun newProcess() = IndraCrashGuardCore(store, keyOf = { it })

    @Test
    fun freshGuard_isNotSuspendedAndTrustsEveryPage() {
        val guard = newProcess()

        assertFalse(guard.isSuspended())
        assertFalse(guard.isPoisoned("a.jpg"))
    }

    @Test
    fun markInFlight_isWrittenThroughImmediately() {
        val guard = newProcess()

        guard.markInFlight("a.jpg")

        assertEquals("a.jpg", store.state.inFlight)
        assertEquals(1, store.saves)
    }

    @Test
    fun normalCompletion_leavesNothingBehind() {
        val first = newProcess()
        first.markInFlight("a.jpg")
        first.clearInFlight()

        val second = newProcess()

        assertFalse(second.isPoisoned("a.jpg"))
        assertFalse(second.isSuspended())
        assertNull(store.state.inFlight)
    }

    @Test
    fun leftoverInFlight_poisonsThePageAndCountsACrash() {
        newProcess().markInFlight("a.jpg")

        val next = newProcess()

        assertTrue(next.isPoisoned("a.jpg"))
        assertFalse(next.isPoisoned("b.jpg"))
        assertFalse(next.isSuspended())
        assertEquals(1, store.state.crashCount)
        assertNull(store.state.inFlight)
    }

    @Test
    fun lowMemoryKill_poisonsThePageWithoutCountingACrash() {
        IndraCrashGuardCore(store, keyOf = { it }, now = { 100L }).markInFlight("a.jpg")
        var askedSince = -1L

        val next = IndraCrashGuardCore(store, keyOf = { it }, previousExit = { askedSince = it; PreviousExit.LOW_MEMORY })

        assertTrue(next.isPoisoned("a.jpg"))
        assertEquals(0, store.state.crashCount)
        assertEquals(100L, askedSince)
        assertNull(store.state.inFlight)
    }

    @Test
    fun swipeAwayOrUpdate_isIgnored() {
        newProcess().markInFlight("a.jpg")

        val next = IndraCrashGuardCore(store, keyOf = { it }, previousExit = { PreviousExit.NOT_A_CRASH })

        assertFalse(next.isPoisoned("a.jpg"))
        assertEquals(0, store.state.crashCount)
        assertNull(store.state.inFlight)
    }

    @Test
    fun exitLookupFailure_isTreatedAsACrash() {
        newProcess().markInFlight("a.jpg")

        val next = IndraCrashGuardCore(store, keyOf = { it }, previousExit = { error("no record") })

        assertTrue(next.isPoisoned("a.jpg"))
        assertEquals(1, store.state.crashCount)
    }

    @Test
    fun twoLeftovers_suspendIndra() = runBlocking {
        newProcess().markInFlight("a.jpg")
        newProcess().markInFlight("b.jpg")

        val guard = newProcess()

        assertTrue(guard.isSuspended())
        assertTrue(guard.isPoisoned("a.jpg"))
        assertTrue(guard.isPoisoned("b.jpg"))
        assertTrue(guard.observeSuspended().first())
    }

    @Test
    fun recoveryHappensOnceNotOnEveryCall() {
        newProcess().markInFlight("a.jpg")
        val guard = newProcess()

        guard.isSuspended()
        guard.isPoisoned("a.jpg")
        guard.isSuspended()

        assertEquals(1, store.state.crashCount)
    }

    @Test
    fun reset_liftsTheSuspensionButKeepsPoisonedPages() = runBlocking {
        newProcess().markInFlight("a.jpg")
        newProcess().markInFlight("b.jpg")
        val guard = newProcess()
        assertTrue(guard.isSuspended())

        guard.reset()

        assertFalse(guard.isSuspended())
        assertTrue(guard.isPoisoned("a.jpg"))
        assertTrue(guard.isPoisoned("b.jpg"))
        assertEquals(IndraGuardState(poisoned = listOf("a.jpg", "b.jpg")), store.state)
        assertFalse(guard.observeSuspended().first())
        assertFalse(newProcess().isSuspended())
    }

    @Test
    fun poisonedSet_isCappedAtTheNewestEntries() {
        repeat(IndraCrashGuardCore.MAX_POISONED + 5) { index ->
            newProcess().markInFlight("page$index.jpg")
        }

        val guard = newProcess()

        assertEquals(IndraCrashGuardCore.MAX_POISONED, store.state.poisoned.size)
        assertFalse(guard.isPoisoned("page0.jpg"))
        assertTrue(guard.isPoisoned("page${IndraCrashGuardCore.MAX_POISONED + 4}.jpg"))
    }

    @Test
    fun pageKey_changesWhenTheImageIsRewritten() {
        val file = File.createTempFile("indra-guard", ".jpg")
        try {
            file.writeText("one")
            val before = IndraCrashGuardCore.pageKey(file.absolutePath)
            file.writeText("a longer rewrite")

            assertNotEquals(before, IndraCrashGuardCore.pageKey(file.absolutePath))
            assertFalse(before.contains(file.name))
        } finally {
            file.delete()
        }
    }
}

private class InMemoryGuardStore : IndraGuardStore {
    var state = IndraGuardState()
    var saves = 0

    override fun load(): IndraGuardState = state

    override fun save(state: IndraGuardState) {
        this.state = state
        saves++
    }
}
