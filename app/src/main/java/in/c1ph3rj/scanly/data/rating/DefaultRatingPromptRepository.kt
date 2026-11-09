package `in`.c1ph3rj.scanly.data.rating

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.domain.model.RatingPromptState
import `in`.c1ph3rj.scanly.domain.repository.RatingPromptRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map

private val Context.ratingPromptDataStore by preferencesDataStore(
    name = "rating_prompt",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class DefaultRatingPromptRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RatingPromptRepository {

    /** Reads fall back to defaults when the preferences file cannot be read, instead of crashing. */
    private val safeData: Flow<Preferences>
        get() = context.ratingPromptDataStore.data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    override fun observeState(): Flow<RatingPromptState> =
        safeData.map { preferences -> preferences.toState() }

    override suspend fun recordFirstSeenIfAbsent(nowMillis: Long) {
        context.ratingPromptDataStore.edit { preferences ->
            if (preferences[FIRST_SEEN_AT_KEY] == null) {
                preferences[FIRST_SEEN_AT_KEY] = nowMillis
            }
        }
    }

    private val sessionPositiveMoments = MutableStateFlow(0)

    override fun observeSessionPositiveMoments(): Flow<Int> = sessionPositiveMoments.asStateFlow()

    override suspend fun recordPositiveMoment() {
        sessionPositiveMoments.update { it + 1 }
        context.ratingPromptDataStore.edit { preferences ->
            preferences[POSITIVE_MOMENTS_KEY] = (preferences[POSITIVE_MOMENTS_KEY] ?: 0) + 1
        }
    }

    override suspend fun recordPromptShown(nowMillis: Long) {
        context.ratingPromptDataStore.edit { preferences ->
            preferences[LAST_PROMPT_AT_KEY] = nowMillis
        }
    }

    override suspend fun recordDismissed() {
        context.ratingPromptDataStore.edit { preferences ->
            preferences[DISMISS_COUNT_KEY] = (preferences[DISMISS_COUNT_KEY] ?: 0) + 1
        }
    }

    override suspend fun recordRated() {
        context.ratingPromptDataStore.edit { preferences ->
            preferences[HAS_RATED_KEY] = true
        }
    }

    override suspend fun recordSettingsCardDismissed() {
        context.ratingPromptDataStore.edit { preferences ->
            preferences[SETTINGS_CARD_DISMISSED_KEY] = true
        }
    }

    private fun Preferences.toState(): RatingPromptState = RatingPromptState(
        firstSeenAtMillis = this[FIRST_SEEN_AT_KEY] ?: 0L,
        positiveMoments = this[POSITIVE_MOMENTS_KEY] ?: 0,
        lastPromptAtMillis = this[LAST_PROMPT_AT_KEY],
        dismissCount = this[DISMISS_COUNT_KEY] ?: 0,
        hasRated = this[HAS_RATED_KEY] ?: false,
        settingsCardDismissed = this[SETTINGS_CARD_DISMISSED_KEY] ?: false,
    )

    private companion object {
        val FIRST_SEEN_AT_KEY = longPreferencesKey("first_seen_at_millis")
        val POSITIVE_MOMENTS_KEY = intPreferencesKey("positive_moments")
        val LAST_PROMPT_AT_KEY = longPreferencesKey("last_prompt_at_millis")
        val DISMISS_COUNT_KEY = intPreferencesKey("dismiss_count")
        val HAS_RATED_KEY = booleanPreferencesKey("has_rated")
        val SETTINGS_CARD_DISMISSED_KEY = booleanPreferencesKey("settings_card_dismissed")
    }
}
