package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.AppLanguage
import `in`.c1ph3rj.scanly.core.common.AppLocales
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionSupport
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import java.util.Locale

/** Settings › Language: pick the app language or follow the phone's language. */
@Composable
fun SettingsLanguageRoute(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppLocales.selectedLanguage(context)) }
    val deviceLocale = remember { AppLocales.deviceLocale(context) }
    SettingsLanguageScreen(
        selected = selected,
        deviceLocale = deviceLocale,
        onNavigateUp = onNavigateUp,
        onLanguageSelected = { language ->
            if (language != selected) {
                selected = language
                CameraPermissionSupport.findActivity(context)?.let { AppLocales.setLanguage(it, language) }
            }
        },
    )
}

@Composable
fun SettingsLanguageScreen(
    selected: AppLanguage?,
    deviceLocale: Locale,
    onNavigateUp: () -> Unit,
    onLanguageSelected: (AppLanguage?) -> Unit,
) {
    val window = rememberWindowSizeInfo()
    val uiLocale = LocalConfiguration.current.locales[0]
    val deviceLanguageName = AppLanguage.from(deviceLocale)?.nativeName
        ?: deviceLocale.getDisplayName(deviceLocale)
    ScanlyDetailScaffold(
        title = stringResource(R.string.settings_language),
        onNavigateUp = onNavigateUp,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = window.horizontalPadding,
                end = window.horizontalPadding,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("system") {
                SettingsGroup(title = stringResource(R.string.settings_language_follow_phone)) {
                    LanguageOption(
                        title = stringResource(R.string.settings_language_system_default),
                        subtitle = stringResource(R.string.settings_language_uses_phone_language, deviceLanguageName),
                        selected = selected == null,
                        onClick = { onLanguageSelected(null) },
                    )
                }
            }
            item("languages") {
                SettingsGroup(title = stringResource(R.string.settings_language_app_language)) {
                    Column(modifier = Modifier.selectableGroup()) {
                        AppLanguage.entries.forEachIndexed { index, language ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val localName = language.displayName(uiLocale)
                            LanguageOption(
                                title = language.nativeName,
                                subtitle = localName.takeUnless { it.equals(language.nativeName, ignoreCase = true) },
                                selected = selected == language,
                                onClick = { onLanguageSelected(language) },
                            )
                        }
                    }
                }
            }
            item("note") {
                Text(
                    text = stringResource(R.string.settings_language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
