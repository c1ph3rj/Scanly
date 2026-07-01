package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.LibraryAccessState
import kotlinx.coroutines.flow.StateFlow

interface LibraryAccessRepository {
    val state: StateFlow<LibraryAccessState>

    suspend fun initialize()

    suspend fun connect(treeUri: String): ScanlyResult<Unit>

    suspend fun disconnect(): ScanlyResult<Unit>

    suspend fun rebuildIndex(): ScanlyResult<Unit>
}

