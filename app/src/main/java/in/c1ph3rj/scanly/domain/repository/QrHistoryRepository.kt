package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.domain.model.QrHistoryEntry
import `in`.c1ph3rj.scanly.domain.model.QrOrigin
import kotlinx.coroutines.flow.Flow

/** On-device history of scanned and created QR codes, newest first. */
interface QrHistoryRepository {
    fun observe(): Flow<List<QrHistoryEntry>>

    /** Records [raw]. A repeat of the newest entry (same text and origin) only refreshes its time. */
    suspend fun add(raw: String, origin: QrOrigin): QrHistoryEntry

    suspend fun delete(id: String)

    suspend fun clear()
}
