package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.PdfWorkingCopy
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.ClosePdfWorkingCopyUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.OpenPdfWorkingCopyUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderPdfPageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

/*
 * "Preview": a read-only, full-screen look at a PDF from inside a tool (Split, Merge, Compress…).
 * It opens its own working copy and renders pages on demand, so the tool's choices, selection and
 * progress are never touched; closing it returns to exactly where the person was.
 */

/** What the preview is showing. */
data class PdfPeekUiState(
    val isOpening: Boolean = true,
    val pageCount: Int = 0,
    val pages: Map<Int, Bitmap> = emptyMap(),
    val error: PdfPeekError? = null,
)

enum class PdfPeekError { Locked, CouldNotOpen }

@HiltViewModel
class PdfPeekViewModel @Inject constructor(
    private val openPdfWorkingCopyUseCase: OpenPdfWorkingCopyUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
    private val closePdfWorkingCopyUseCase: ClosePdfWorkingCopyUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfPeekUiState())
    val uiState: StateFlow<PdfPeekUiState> = _uiState.asStateFlow()

    private var openedFor: Pair<PdfToolSource, String?>? = null
    private var workingCopy: PdfWorkingCopy? = null
    private var openJob: Job? = null
    private var generation = 0
    private val inFlight = mutableSetOf<Int>()
    private val renderPermits = Semaphore(RenderParallelism)

    fun open(source: PdfToolSource, password: String?) {
        val key = source to password?.takeIf(String::isNotBlank)
        if (key == openedFor) return
        close()
        openedFor = key
        val requestGeneration = ++generation
        _uiState.value = PdfPeekUiState(isOpening = true)
        openJob = viewModelScope.launch {
            val result = openPdfWorkingCopyUseCase(source, key.second)
            if (requestGeneration != generation) {
                if (result is ScanlyResult.Success) closeQuietly(result.value)
                return@launch
            }
            when (result) {
                is ScanlyResult.Success -> {
                    workingCopy = result.value
                    _uiState.value = PdfPeekUiState(isOpening = false, pageCount = result.value.pageCount)
                }
                is ScanlyResult.Failure -> {
                    val locked = result.error.message.contains("password", ignoreCase = true)
                    _uiState.value = PdfPeekUiState(
                        isOpening = false,
                        error = if (locked) PdfPeekError.Locked else PdfPeekError.CouldNotOpen,
                    )
                }
            }
        }
    }

    /** Renders page [index] if it isn't cached or on its way; far-away pages are dropped. */
    fun requestPage(index: Int) {
        val copy = workingCopy ?: return
        if (index !in 0 until _uiState.value.pageCount) return
        if (index in _uiState.value.pages || !inFlight.add(index)) return
        val requestGeneration = generation
        viewModelScope.launch {
            val result = renderPermits.withPermit {
                if (requestGeneration != generation) return@withPermit null
                renderPdfPageUseCase(copy.file, index, maxWidth = PageWidthPx)
            }
            inFlight.remove(index)
            if (result !is ScanlyResult.Success) return@launch
            if (requestGeneration != generation) {
                result.value.recycle()
                return@launch
            }
            _uiState.update { state ->
                val next = LinkedHashMap(state.pages)
                next[index] = result.value
                // Keep memory bounded: drop the pages furthest from the one just shown.
                while (next.size > MaxCachedPages) {
                    val far = next.keys.maxBy { kotlin.math.abs(it - index) }
                    next.remove(far)
                }
                state.copy(pages = next)
            }
        }
    }

    /** Forgets the open file and deletes its working copy. */
    fun close() {
        generation++
        openJob?.cancel()
        openJob = null
        openedFor = null
        inFlight.clear()
        _uiState.value = PdfPeekUiState()
        workingCopy?.let(::closeQuietly)
        workingCopy = null
    }

    private fun closeQuietly(copy: PdfWorkingCopy) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(NonCancellable) { closePdfWorkingCopyUseCase(copy) }
        }
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private companion object {
        const val PageWidthPx = 1080
        const val MaxCachedPages = 6
        const val RenderParallelism = 2
    }
}

/**
 * Full-screen, read-only preview of [source]. Pass the password the tool already unlocked the
 * file with, if any. Nothing in the calling tool changes; [onDismiss] just closes the preview.
 */
@Composable
fun PdfPeekDialog(
    source: PdfToolSource,
    password: String?,
    onDismiss: () -> Unit,
) {
    val viewModel: PdfPeekViewModel = hiltViewModel(key = "pdf_peek")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(source, password) { viewModel.open(source, password) }
    val dismiss = {
        viewModel.close()
        onDismiss()
    }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxSize()) {
                PeekHeader(name = source.label(), onClose = dismiss)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        uiState.isOpening -> PeekMessage(loading = true, text = stringResource(R.string.pdf_peek_opening))
                        uiState.error == PdfPeekError.Locked -> PeekMessage(
                            icon = Icons.Outlined.Lock,
                            text = stringResource(R.string.pdf_peek_locked),
                        )
                        uiState.error != null -> PeekMessage(
                            icon = Icons.Outlined.ErrorOutline,
                            text = stringResource(R.string.pdf_peek_could_not_open),
                        )
                        else -> PeekPages(uiState = uiState, onRequestPage = viewModel::requestPage)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeekHeader(name: String, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pdf_peek_close))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(
                        Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.pdf_peek_read_only),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeekPages(uiState: PdfPeekUiState, onRequestPage: (Int) -> Unit) {
    val listState = rememberLazyListState()
    // The page nearest the middle of the screen, not the one barely peeking in at the top.
    val currentPage by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val index = info.visibleItemsInfo
                .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }
                ?.index ?: 0
            (index + 1).coerceAtMost(uiState.pageCount)
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(uiState.pageCount, key = { it }) { index ->
                LaunchedEffect(index) { onRequestPage(index) }
                PeekPage(
                    bitmap = uiState.pages[index],
                    label = stringResource(R.string.pdf_peek_page_of, index + 1, uiState.pageCount),
                )
            }
        }
        // Where am I: a floating "Page 3 of 12" pill.
        if (uiState.pageCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = stringResource(R.string.pdf_peek_page_of, currentPage, uiState.pageCount),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

@Composable
private fun PeekPage(bitmap: Bitmap?, label: String) {
    Column(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = RoundedCornerShape(4.dp)
        // A4 placeholder until the page arrives, then the page's own shape.
        val ratio = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (1f / 1.414f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .shadow(3.dp, shape)
                .clip(shape)
                .background(Color.White)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFF8A96A0))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PeekMessage(
    text: String,
    loading: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Column(
        modifier = Modifier.padding(32.dp).widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
