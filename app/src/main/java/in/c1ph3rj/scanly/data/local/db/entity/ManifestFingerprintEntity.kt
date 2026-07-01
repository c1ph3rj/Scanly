package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity

@Entity(tableName = "manifest_fingerprints", primaryKeys = ["recordType", "recordId"])
data class ManifestFingerprintEntity(
    val recordType: String,
    val recordId: String,
    val revision: Long,
    val checksum: String,
)

