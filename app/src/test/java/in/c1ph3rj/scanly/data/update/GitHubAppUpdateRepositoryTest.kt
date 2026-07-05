package `in`.c1ph3rj.scanly.data.update

import `in`.c1ph3rj.scanly.domain.model.AppRelease
import `in`.c1ph3rj.scanly.domain.model.AppUpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubAppUpdateRepositoryTest {
    @Test
    fun createCheckResult_marksNewerGitHubReleaseAsAvailable() {
        val result = createGitHubUpdateCheckResult(
            installedVersionName = "1.0.9",
            latestRelease = release(tagName = "v1.0.10"),
        )

        assertTrue(result.updateAvailable)
        assertEquals(AppUpdateChannel.GITHUB, result.channel)
        assertEquals(null, result.playUpdateType)
    }

    @Test
    fun createCheckResult_doesNotOfferSameGitHubRelease() {
        val result = createGitHubUpdateCheckResult(
            installedVersionName = "1.0.9",
            latestRelease = release(tagName = "v1.0.9"),
        )

        assertFalse(result.updateAvailable)
    }

    private fun release(tagName: String) = AppRelease(
        tagName = tagName,
        title = tagName,
        bodyMarkdown = "",
        htmlUrl = "https://github.com/c1ph3rj/Scanly/releases/tag/$tagName",
        publishedAt = null,
        apkAsset = null,
    )
}
