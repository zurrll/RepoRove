package app.reporove

import android.graphics.Bitmap
import android.app.DownloadManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.reporove.core.network.createApi
import app.reporove.data.GitHubRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.BeforeClass
import java.io.File
import kotlinx.coroutines.flow.first

/** Opt-in network smoke, separate from the deterministic regression gate. */
class PublicApiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    companion object {
        @JvmStatic @BeforeClass fun requireLiveNetworkOptIn() {
            assumeTrue(InstrumentationRegistry.getArguments().getString("liveApi") == "true")
        }
    }
    @Test fun realGitHubSearchReadmeAndProductionHome() {
        runBlocking {
            val api = createApi(null)
            val repo = api.repository("octocat", "Hello-World")
            assertEquals("octocat/Hello-World", repo.fullName)
            assertTrue(GitHubRepository.text(api.readme("octocat", "Hello-World")).isNotBlank())
            val search = api.searchRepositories("repo:octocat/Hello-World")
            assertTrue(search.items.any { it.id == repo.id })
        }
        compose.waitUntil(60000) { compose.onAllNodesWithContentDescription("稍后看").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir("screenshots"), "live-discover.png")
        output.parentFile?.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun publicReleaseAttachmentDownloadsThroughAndroidService() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RepoRoveApplication
        val asset = createApi(null).releases("cli", "cli").first().assets.first { it.name.endsWith("checksums.txt") }
        val downloads = context.container.downloads
        val id = downloads.enqueue("cli/cli", asset)
        val manager = context.getSystemService(DownloadManager::class.java)
        var file: File? = null
        var lastStatus = ""
        try {
            withTimeout(90000) {
                while (true) {
                    val progress = downloads.progress(id)
                    val status = "${progress?.status}:${progress?.reason}:${progress?.bytes}/${progress?.total}"
                    if (status != lastStatus) { println("ATTACHMENT_DOWNLOAD $status"); lastStatus = status }
                    if (progress?.status == DownloadManager.STATUS_SUCCESSFUL) {
                        assertEquals(asset.size, progress.bytes)
                        file = localFile(progress.localUri)
                        assertTrue(file!!.isFile)
                        // A 0.3 record lacks the filename; deletion must learn it before the system row disappears.
                        val record = context.container.local.downloads.first().first { it.id == id }
                        context.container.local.addDownload(record.copy(fileName = null))
                        break
                    }
                    if (progress?.status == DownloadManager.STATUS_FAILED) fail("DownloadManager reason: ${progress.reason}")
                    delay(500)
                }
            }
        } finally { downloads.remove(id) }
        assertFalse(file!!.exists())
        assertNull(manager.getUriForDownloadedFile(id))
        assertTrue(context.container.local.downloads.first().none { it.id == id })
    }

    @Test fun coldStartAndPythonRecommendationsUseRealGuardedCandidates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RepoRoveApplication
        val repository = context.container.repository
        repository.logout()
        val cold = repository.recommendations(app.reporove.core.model.Preferences(), null, 1, true).data.items
        assertTrue(cold.isNotEmpty())
        assertTrue(cold.all { it.repository.stars >= 50 && !it.repository.archived && !it.repository.fork && !it.repository.isPrivate })
        val python = repository.recommendations(app.reporove.core.model.Preferences(interests = listOf("python")), "python", 1, true).data.items
        assertTrue(python.isNotEmpty())
        assertTrue(python.all { it.repository.stars >= 10 && "python" in it.repository.topics })
        println("REAL_RECOMMENDATIONS cold=${cold.size}, cold_min_stars=${cold.minOf { it.repository.stars }}, python=${python.size}, python_min_stars=${python.minOf { it.repository.stars }}")
    }

    @Test fun releaseSourceZipAndTarDownloadValidArchives() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RepoRoveApplication
        val release = createApi(null).releaseTag("ad-m", "github-push-action", "v1.3.0")
        assertNotNull(release.zipballUrl); assertNotNull(release.tarballUrl)
        val downloads = context.container.downloads
        val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
        for (tar in listOf(false, true)) {
            val id = downloads.enqueueArchive("ad-m/github-push-action", release, tar)
            try {
                withTimeout(90000) {
                    while (true) {
                        val progress = downloads.progress(id)
                        if (progress?.status == DownloadManager.STATUS_SUCCESSFUL) { assertTrue(progress.bytes > 0); break }
                        if (progress?.status == DownloadManager.STATUS_FAILED) fail("Archive DownloadManager reason: ${progress.reason}")
                        delay(500)
                    }
                }
                val uri = manager.getUriForDownloadedFile(id)
                context.contentResolver.openInputStream(uri).use { stream ->
                    assertNotNull(stream)
                    assertEquals(if (tar) 0x1f else 0x50, stream!!.read())
                    assertEquals(if (tar) 0x8b else 0x4b, stream.read())
                }
            } finally { downloads.remove(id) }
        }
    }

    private fun localFile(uri: String?): File = File(android.net.Uri.parse(uri!!).path!!)
}
