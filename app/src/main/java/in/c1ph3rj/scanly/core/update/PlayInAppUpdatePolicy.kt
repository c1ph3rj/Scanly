package `in`.c1ph3rj.scanly.core.update

import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateType

object PlayInAppUpdatePolicy {
    const val IMMEDIATE_PRIORITY_THRESHOLD = 4
    const val FLEXIBLE_STALENESS_DAYS = 3

    fun resolveUpdateType(
        updatePriority: Int,
        stalenessDays: Int?,
        flexibleAllowed: Boolean,
        immediateAllowed: Boolean,
    ): PlayInAppUpdateType? {
        if (immediateAllowed && updatePriority >= IMMEDIATE_PRIORITY_THRESHOLD) {
            return PlayInAppUpdateType.IMMEDIATE
        }
        if (
            flexibleAllowed &&
            (stalenessDays ?: 0) >= FLEXIBLE_STALENESS_DAYS
        ) {
            return PlayInAppUpdateType.FLEXIBLE
        }
        if (flexibleAllowed) {
            return PlayInAppUpdateType.FLEXIBLE
        }
        if (immediateAllowed) {
            return PlayInAppUpdateType.IMMEDIATE
        }
        return null
    }

    fun shouldAutoStartImmediate(
        updateType: PlayInAppUpdateType?,
        triggerAutomatic: Boolean,
    ): Boolean = triggerAutomatic && updateType == PlayInAppUpdateType.IMMEDIATE
}