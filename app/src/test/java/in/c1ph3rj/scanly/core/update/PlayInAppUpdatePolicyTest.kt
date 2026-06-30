package `in`.c1ph3rj.scanly.core.update

import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayInAppUpdatePolicyTest {
    @Test
    fun resolveUpdateType_prefersImmediateForHighPriority() {
        val updateType = PlayInAppUpdatePolicy.resolveUpdateType(
            updatePriority = 5,
            stalenessDays = 0,
            flexibleAllowed = true,
            immediateAllowed = true,
        )

        assertEquals(PlayInAppUpdateType.IMMEDIATE, updateType)
    }

    @Test
    fun resolveUpdateType_usesFlexibleWhenStalenessThresholdReached() {
        val updateType = PlayInAppUpdatePolicy.resolveUpdateType(
            updatePriority = 2,
            stalenessDays = PlayInAppUpdatePolicy.FLEXIBLE_STALENESS_DAYS,
            flexibleAllowed = true,
            immediateAllowed = true,
        )

        assertEquals(PlayInAppUpdateType.FLEXIBLE, updateType)
    }

    @Test
    fun resolveUpdateType_returnsNullWhenNoUpdateTypeAllowed() {
        val updateType = PlayInAppUpdatePolicy.resolveUpdateType(
            updatePriority = 1,
            stalenessDays = 0,
            flexibleAllowed = false,
            immediateAllowed = false,
        )

        assertNull(updateType)
    }

    @Test
    fun shouldAutoStartImmediate_onlyForAutomaticImmediateUpdates() {
        assertTrue(
            PlayInAppUpdatePolicy.shouldAutoStartImmediate(
                updateType = PlayInAppUpdateType.IMMEDIATE,
                triggerAutomatic = true,
            ),
        )
        assertFalse(
            PlayInAppUpdatePolicy.shouldAutoStartImmediate(
                updateType = PlayInAppUpdateType.IMMEDIATE,
                triggerAutomatic = false,
            ),
        )
        assertFalse(
            PlayInAppUpdatePolicy.shouldAutoStartImmediate(
                updateType = PlayInAppUpdateType.FLEXIBLE,
                triggerAutomatic = true,
            ),
        )
    }
}