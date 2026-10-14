package `in`.c1ph3rj.scanly.feature.tools.pdf

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PdfPasswordMode
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecordPositiveMomentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveExportArtifactUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.InspectPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RemovePdfPasswordUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderPdfPageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.SetPdfPasswordUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shortest password the backend accepts (see PdfToolkitRepository.setPassword). */
internal const val PDF_PASSWORD_MIN_LENGTH = 4

/** Longest password the backend accepts. */
internal const val PDF_PASSWORD_MAX_LENGTH = 64

/**
 * The file decides the action: an unencrypted PDF is locked (Protect), an encrypted or
 * password-needing PDF is unlocked (Remove). There is no mode toggle in the UI.
 */
@HiltViewModel
class PdfPasswordViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val setPdfPasswordUseCase: SetPdfPasswordUseCase,
    private val removePdfPasswordUseCase: RemovePdfPasswordUseCase,
    private val inspectPdfUseCase: InspectPdfUseCase,
    saveExportArtifactUseCase: SaveExportArtifactUseCase,
    renderPdfPageUseCase: RenderPdfPageUseCase,
    recordPositiveMomentUseCase: RecordPositiveMomentUseCase,
) : BasePdfToolViewModel(observeDocumentsUseCase, saveExportArtifactUseCase, renderPdfPageUseCase, recordPositiveMomentUseCase) {
    private val _restrictEditing = MutableStateFlow(false)

    /** "Restrict editing" switch for the Add password flow. */
    val restrictEditing: StateFlow<Boolean> = _restrictEditing.asStateFlow()

    private val _passwordError = MutableStateFlow<String?>(null)

    /** Wrong-password message for the Remove password field. Shown inline, not as a snackbar. */
    val passwordError: StateFlow<String?> = _passwordError.asStateFlow()

    fun setMode(mode: PdfPasswordMode) {
        _uiState.update { it.copy(passwordMode = mode, result = null) }
    }

    fun setPassword(value: String) {
        _uiState.update { it.copy(password = value, result = null) }
    }

    fun setConfirmPassword(value: String) {
        _uiState.update { it.copy(confirmPassword = value, result = null) }
    }

    fun setCurrentPassword(value: String) {
        _passwordError.value = null
        _uiState.update { it.copy(currentPassword = value, result = null) }
    }

    fun setRestrictEditing(value: Boolean) {
        _restrictEditing.value = value
    }

    fun onSourcesChosen(sources: List<PdfToolSource>) {
        clearResult()
        _restrictEditing.value = false
        _passwordError.value = null
        _uiState.update {
            it.copy(
                sources = sources,
                info = null,
                needsPassword = false,
                password = "",
                confirmPassword = "",
                currentPassword = "",
            )
        }
        sources.firstOrNull()?.let { inspect(it) }
    }

    /** Drops the picked file, its inspection, the result and every password field. */
    fun reset() {
        clearResult()
        clearAll()
        _restrictEditing.value = false
        _passwordError.value = null
        _uiState.update { it.copy(password = "", confirmPassword = "", currentPassword = "") }
    }

    /** Locks an unencrypted file, or unlocks a protected one, depending on [PdfToolUiState.passwordMode]. */
    fun submit() {
        val state = _uiState.value
        val source = state.sources.firstOrNull() ?: return
        when (state.passwordMode) {
            PdfPasswordMode.Protect -> {
                if (state.password.length !in PDF_PASSWORD_MIN_LENGTH..PDF_PASSWORD_MAX_LENGTH) {
                    emitMessage(
                        "Use $PDF_PASSWORD_MIN_LENGTH to $PDF_PASSWORD_MAX_LENGTH characters for the password.",
                    )
                    return
                }
                if (state.password != state.confirmPassword) {
                    emitMessage("Passwords do not match.")
                    return
                }
                val restrict = _restrictEditing.value
                runProcess("Locking PDF…", outputName = suggestPdfOutputName(source, "locked")) {
                    setPdfPasswordUseCase(
                        source = source,
                        newPassword = state.password,
                        restrictEditing = restrict,
                    )
                }
            }
            PdfPasswordMode.Remove -> {
                if (state.currentPassword.isBlank()) {
                    _passwordError.value = "Enter the current password."
                    return
                }
                runProcess("Unlocking PDF…", outputName = suggestPdfOutputName(source, "unlocked")) {
                    removePdfPasswordUseCase(source, state.currentPassword).also { result ->
                        _passwordError.value = (result as? ScanlyResult.Failure)
                            ?.error?.message
                            ?.takeIf { it.contains("password", ignoreCase = true) }
                    }
                }
            }
        }
    }

    /** The protected output opens with the new password, so its first page can be rendered. */
    override fun resultPreviewPassword(): String? {
        val state = _uiState.value
        return state.password.takeIf { state.passwordMode == PdfPasswordMode.Protect }?.ifBlank { null }
    }

    private fun inspect(source: PdfToolSource) {
        viewModelScope.launch {
            val result = inspectPdfUseCase(source)
            // The user may have picked another file while this one was being inspected.
            if (_uiState.value.sources.firstOrNull() != source) return@launch
            when (result) {
                is ScanlyResult.Success -> _uiState.update {
                    it.copy(
                        info = result.value,
                        needsPassword = false,
                        passwordMode = if (result.value.isEncrypted) {
                            PdfPasswordMode.Remove
                        } else {
                            PdfPasswordMode.Protect
                        },
                    )
                }
                is ScanlyResult.Failure -> {
                    val needsPassword = result.error.message.contains("password", ignoreCase = true)
                    if (needsPassword) {
                        _uiState.update {
                            it.copy(needsPassword = true, info = null, passwordMode = PdfPasswordMode.Remove)
                        }
                    } else {
                        clearAll()
                        emitMessage(result.error.message)
                    }
                }
            }
        }
    }
}

/** True when inspect failed for a password reason, or the opened PDF reports encryption. */
internal fun PdfToolUiState.isPdfAlreadyProtected(): Boolean =
    needsPassword || info?.isEncrypted == true
