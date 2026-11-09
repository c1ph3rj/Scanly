package `in`.c1ph3rj.scanly.feature.settings

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A successful handoff is not evidence that the user sent the email. */
data class FeedbackEmailHandoffState(
    val showConfirmation: Boolean = false,
    val draftEpoch: Int = 0,
    val closeRequested: Boolean = false,
)

/** Persists an explicit launch -> actual departure -> return cycle independently of composition. */
internal class FeedbackEmailHandoff(private val savedState: SavedStateHandle) {
    private enum class Phase { IDLE, LAUNCHED, LEFT_APP, ASKING }

    private var phase = runCatching {
        Phase.valueOf(savedState.get<String>(PHASE_KEY) ?: Phase.IDLE.name)
    }.getOrDefault(Phase.IDLE)
    private val current = MutableStateFlow(snapshot())
    val state = current.asStateFlow()

    fun onEmailLaunched() {
        if (!current.value.closeRequested) {
            savedState[RESULT_PENDING_KEY] = true
            updatePhase(Phase.LAUNCHED)
        }
    }

    fun onEmailReturned() {
        if (savedState.get<Boolean>(RESULT_PENDING_KEY) != true) return
        savedState[RESULT_PENDING_KEY] = false
        if (!current.value.closeRequested && phase != Phase.ASKING) updatePhase(Phase.ASKING)
    }

    fun onHostStopped(changingConfigurations: Boolean) {
        if (!changingConfigurations && phase == Phase.LAUNCHED) updatePhase(Phase.LEFT_APP)
    }

    fun onHostResumed() {
        when (phase) {
            Phase.LEFT_APP -> updatePhase(Phase.ASKING)
            // Do not treat resume alone as a departure. A floating/multiwindow email can return a
            // result without ever stopping Scanly; its separately saved pending result still asks.
            Phase.LAUNCHED -> updatePhase(Phase.IDLE)
            Phase.IDLE, Phase.ASKING -> Unit
        }
    }

    fun keepDraft() {
        if (phase == Phase.ASKING) {
            savedState[RESULT_PENDING_KEY] = false
            updatePhase(Phase.IDLE)
        }
    }

    fun confirmSent(): Boolean {
        if (phase != Phase.ASKING) return false
        savedState[RESULT_PENDING_KEY] = false
        savedState[EPOCH_KEY] = current.value.draftEpoch + 1
        savedState[CLOSE_KEY] = true
        updatePhase(Phase.IDLE)
        return true
    }

    fun consumeCloseRequest() {
        savedState[CLOSE_KEY] = false
        publish()
    }

    private fun updatePhase(value: Phase) {
        phase = value
        savedState[PHASE_KEY] = value.name
        publish()
    }

    private fun publish() { current.value = snapshot() }

    private fun snapshot() = FeedbackEmailHandoffState(
        showConfirmation = phase == Phase.ASKING,
        draftEpoch = savedState.get<Int>(EPOCH_KEY) ?: 0,
        closeRequested = savedState.get<Boolean>(CLOSE_KEY) ?: false,
    )

    private companion object {
        const val PHASE_KEY = "feedback_email_handoff_phase"
        const val RESULT_PENDING_KEY = "feedback_email_result_pending"
        const val EPOCH_KEY = "feedback_draft_epoch"
        const val CLOSE_KEY = "feedback_close_requested"
    }
}
