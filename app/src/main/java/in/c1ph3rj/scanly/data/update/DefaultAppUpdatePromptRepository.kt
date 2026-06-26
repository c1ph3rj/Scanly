package `in`.c1ph3rj.scanly.data.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.AppUpdatePromptRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.updatePromptDataStore by preferencesDataStore(name = "scanly_update_prompt")

@Singleton
class DefaultAppUpdatePromptRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) : AppUpdatePromptRepository {

    override suspend fun getLastUpdateDialogShownAtMillis(): Long? =
        withContext(dispatchers.io) {
            runCatching {
                context.updatePromptDataStore.data.first()[lastShownAtKey]
            }.getOrNull()
        }

    override suspend fun setLastUpdateDialogShownAtMillis(timestampMillis: Long): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                context.updatePromptDataStore.edit { preferences ->
                    preferences[lastShownAtKey] = timestampMillis
                }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not store update prompt state.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    private companion object {
        val lastShownAtKey = longPreferencesKey("last_update_dialog_shown_at_millis")
    }
}
