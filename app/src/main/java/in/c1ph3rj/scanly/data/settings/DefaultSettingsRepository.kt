package `in`.c1ph3rj.scanly.data.settings

import android.content.Context
import android.os.Build
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
import `in`.c1ph3rj.scanly.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "scanly_settings")

@Singleton
class DefaultSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) : SettingsRepository {

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

    private companion object {
        val themeModeKey = stringPreferencesKey("theme_mode")
        const val faqsAssetPath = "settings/faqs.json"
        const val licensesAssetPath = "settings/licenses.json"
        const val developerWebsite = ""
    }
}
