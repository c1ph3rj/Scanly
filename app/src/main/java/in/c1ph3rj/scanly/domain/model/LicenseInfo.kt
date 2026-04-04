package `in`.c1ph3rj.scanly.domain.model

data class LicenseInfo(
    val id: String,
    val name: String,
    val summary: String,
    val license: String,
    val websiteUrl: String? = null,
)
