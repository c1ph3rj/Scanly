package `in`.c1ph3rj.scanly.data.settings

import android.content.Context
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.LicenseInfo
import `in`.c1ph3rj.scanly.domain.model.SettingsContent
import `in`.c1ph3rj.scanly.domain.model.SettingsFaq
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.domain.model.ExportDestination
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "scanly_settings")

@Singleton
class DefaultSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) : SettingsRepository {

    override fun observeLiveDetectionModel(): Flow<DocumentCornerModel> =
        context.settingsDataStore.data.map { DocumentCornerModel.fromStorage(it[liveDetectionModelKey]) }

    override suspend fun setLiveDetectionModel(model: DocumentCornerModel): ScanlyResult<Unit> =
        updateModelPreference(liveDetectionModelKey, model)

    override fun observePostProcessingModel(): Flow<DocumentCornerModel> =
        context.settingsDataStore.data.map { DocumentCornerModel.fromStorage(it[postProcessingModelKey]) }

    override suspend fun setPostProcessingModel(model: DocumentCornerModel): ScanlyResult<Unit> =
        updateModelPreference(postProcessingModelKey, model)

    override suspend fun getPostProcessingModel(): DocumentCornerModel =
        DocumentCornerModel.fromStorage(context.settingsDataStore.data.first()[postProcessingModelKey])

    override fun observeAutomaticModelSelection(): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[automaticModelSelectionKey] ?: false
        }

    override suspend fun setAutomaticModelSelection(enabled: Boolean): ScanlyResult<Unit> =
        updateBooleanPreference(automaticModelSelectionKey, enabled)

    override suspend fun getAutomaticModelSelection(): Boolean =
        context.settingsDataStore.data.first()[automaticModelSelectionKey] ?: false

    override fun observeDocumentGateEnabled(): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[documentGateEnabledKey] ?: true
        }

    override suspend fun setDocumentGateEnabled(enabled: Boolean): ScanlyResult<Unit> =
        updateBooleanPreference(documentGateEnabledKey, enabled)

    override suspend fun getDocumentGateEnabled(): Boolean =
        context.settingsDataStore.data.first()[documentGateEnabledKey] ?: true

    override fun observeExportDestination(): Flow<ExportDestination> =
        context.settingsDataStore.data.map { preferences ->
            val uri = preferences[exportTreeUriKey]
            val label = preferences[exportTreeLabelKey]
            if (uri.isNullOrBlank() || label.isNullOrBlank()) {
                ExportDestination.DefaultDownloadsScanly
            } else {
                ExportDestination.CustomTree(uriString = uri, displayName = label)
            }
        }

    override suspend fun setExportDestination(
        destination: ExportDestination.CustomTree,
    ): ScanlyResult<Unit> = updateExportDestination(destination)

    override suspend fun resetExportDestination(): ScanlyResult<Unit> =
        updateExportDestination(null)

    override fun observeThemeMode(): Flow<ThemeMode> =
        context.settingsDataStore.data.map { preferences ->
            ThemeMode.fromStorage(preferences[themeModeKey] ?: ThemeMode.SYSTEM.storageValue)
        }

    override suspend fun setThemeMode(themeMode: ThemeMode): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                context.settingsDataStore.edit { preferences ->
                    preferences[themeModeKey] = themeMode.storageValue
                }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not update theme mode.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    override fun observePureBlackEnabled(): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[pureBlackEnabledKey] ?: false
        }

    override suspend fun setPureBlackEnabled(enabled: Boolean): ScanlyResult<Unit> =
        updateBooleanPreference(pureBlackEnabledKey, enabled)

    override fun observeOnboardingCompleted(): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[onboardingCompletedKey] ?: false
        }

    override suspend fun completeOnboarding(): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                context.settingsDataStore.edit { preferences ->
                    preferences[onboardingCompletedKey] = true
                }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not finish onboarding.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    override suspend fun loadSettingsContent(): ScanlyResult<SettingsContent> =
        withContext(dispatchers.io) {
            runCatching {
                SettingsContent(
                    faqs = readFaqs(),
                    licenses = readLicenses(),
                    developerWebsite = developerWebsite,
                    appVersionLabel = packageVersionLabel(),
                )
            }.fold(
                onSuccess = { content -> ScanlyResult.Success(content) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not load settings content.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    private fun readFaqs(): List<SettingsFaq> {
        val jsonArray = JSONArray(readAsset(faqsAssetPath))
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    SettingsFaq(
                        id = item.getString("id"),
                        question = item.getString("question"),
                        answer = item.getString("answer"),
                    ),
                )
            }
        }
    }

    private fun readLicenses(): List<LicenseInfo> {
        val jsonArray = JSONArray(readAsset(licensesAssetPath))
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    LicenseInfo(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        summary = item.getString("summary"),
                        license = item.getString("license"),
                        websiteUrl = item.optString("websiteUrl").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun packageVersionLabel(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return "v${packageInfo.versionName ?: "1.0"}"
    }

    private suspend fun updateExportDestination(
        destination: ExportDestination.CustomTree?,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            val previousUri = context.settingsDataStore.data.first()[exportTreeUriKey]
            context.settingsDataStore.edit { preferences ->
                if (destination == null) {
                    preferences.remove(exportTreeUriKey)
                    preferences.remove(exportTreeLabelKey)
                } else {
                    preferences[exportTreeUriKey] = destination.uriString
                    preferences[exportTreeLabelKey] = destination.displayName
                }
            }
            if (!previousUri.isNullOrBlank() && previousUri != destination?.uriString) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(previousUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
        }.fold(
            onSuccess = { ScanlyResult.Success(Unit) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Could not update the save location.",
                        cause = throwable,
                    ),
                )
            },
        )
    }

    private suspend fun updateModelPreference(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        model: DocumentCornerModel,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            context.settingsDataStore.edit { it[key] = model.storageValue }
        }.fold(
            onSuccess = { ScanlyResult.Success(Unit) },
            onFailure = { throwable ->
                ScanlyResult.Failure(ScanlyError(throwable.message ?: "Could not update model selection.", throwable))
            },
        )
    }

    private suspend fun updateBooleanPreference(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        enabled: Boolean,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            context.settingsDataStore.edit { it[key] = enabled }
        }.fold(
            onSuccess = { ScanlyResult.Success(Unit) },
            onFailure = { throwable ->
                ScanlyResult.Failure(ScanlyError(throwable.message ?: "Could not update document detection settings.", throwable))
            },
        )
    }

    private companion object {
        val themeModeKey = stringPreferencesKey("theme_mode")
        val pureBlackEnabledKey = booleanPreferencesKey("pure_black_enabled")
        val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
        val exportTreeUriKey = stringPreferencesKey("export_tree_uri")
        val exportTreeLabelKey = stringPreferencesKey("export_tree_label")
        val liveDetectionModelKey = stringPreferencesKey("live_detection_model")
        val postProcessingModelKey = stringPreferencesKey("post_processing_model")
        val automaticModelSelectionKey = booleanPreferencesKey("automatic_document_model_selection")
        val documentGateEnabledKey = booleanPreferencesKey("document_gate_enabled")
        const val faqsAssetPath = "settings/faqs.json"
        const val licensesAssetPath = "settings/licenses.json"
        const val developerWebsite = ""
    }
}
