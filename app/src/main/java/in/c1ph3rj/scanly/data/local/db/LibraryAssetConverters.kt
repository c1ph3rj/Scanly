package `in`.c1ph3rj.scanly.data.local.db

import androidx.room.TypeConverter
import `in`.c1ph3rj.scanly.domain.model.LibraryAssetRef
import kotlinx.serialization.json.Json

class LibraryAssetConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun assetToJson(asset: LibraryAssetRef?): String? = asset?.let { json.encodeToString(it) }

    @TypeConverter
    fun jsonToAsset(value: String?): LibraryAssetRef? = value?.let { json.decodeFromString(it) }
}

