package `in`.c1ph3rj.scanly.data.pip

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.domain.model.PipPreference
import `in`.c1ph3rj.scanly.domain.model.PipPreferences
import `in`.c1ph3rj.scanly.domain.repository.PipRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.pipDataStore by preferencesDataStore(name = "pip")

@Singleton
class DefaultPipRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PipRepository {

    override fun observePreferences(): Flow<PipPreferences> =
        context.pipDataStore.data
            .map { preferences -> preferences.toPipPreferences() }
            // Pip is decoration: a broken preferences file must never take a screen down.
            .catch { emit(PipPreferences()) }

    override suspend fun setPreference(preference: PipPreference, enabled: Boolean) {
        runCatching {
            context.pipDataStore.edit { preferences -> preferences[preference.key] = enabled }
        }
    }

    override suspend fun dismissTip(epochDay: Long) {
        runCatching {
            context.pipDataStore.edit { preferences -> preferences[TIP_DISMISSED_DAY_KEY] = epochDay }
        }
    }

    private fun Preferences.toPipPreferences(): PipPreferences = PipPreferences(
        enabled = this[ENABLED_KEY] ?: true,
        homeTips = this[HOME_TIPS_KEY] ?: true,
        cameraCoaching = this[CAMERA_COACHING_KEY] ?: true,
        celebrations = this[CELEBRATIONS_KEY] ?: true,
        motion = this[MOTION_KEY] ?: true,
        tipDismissedEpochDay = this[TIP_DISMISSED_DAY_KEY] ?: PipPreferences.NO_DAY,
    )

    private val PipPreference.key: Preferences.Key<Boolean>
        get() = when (this) {
            PipPreference.ENABLED -> ENABLED_KEY
            PipPreference.HOME_TIPS -> HOME_TIPS_KEY
            PipPreference.CAMERA_COACHING -> CAMERA_COACHING_KEY
            PipPreference.CELEBRATIONS -> CELEBRATIONS_KEY
            PipPreference.MOTION -> MOTION_KEY
        }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("pip_enabled")
        val HOME_TIPS_KEY = booleanPreferencesKey("pip_home_tips")
        val CAMERA_COACHING_KEY = booleanPreferencesKey("pip_camera_coaching")
        val CELEBRATIONS_KEY = booleanPreferencesKey("pip_celebrations")
        val MOTION_KEY = booleanPreferencesKey("pip_motion")
        val TIP_DISMISSED_DAY_KEY = longPreferencesKey("pip_tip_dismissed_epoch_day")
    }
}
