package `in`.c1ph3rj.scanly.domain.model

data class SettingsContent(
    val faqs: List<SettingsFaq>,
    val licenses: List<LicenseInfo>,
    val developerWebsite: String,
    val appVersionLabel: String,
)
