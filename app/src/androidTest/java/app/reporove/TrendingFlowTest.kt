package app.reporove

import android.app.Application
import android.app.NotificationManager
import android.app.job.JobScheduler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.reporove.core.model.*
import app.reporove.core.network.createApi
import app.reporove.core.trending.*
import app.reporove.data.TrendingRepository
import app.reporove.ui.RepoRoveApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.runners.MethodSorters

// The host revokes POST_NOTIFICATIONS before this process starts; revocation mid-run kills the target.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TrendingFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: MockWebServer
    private lateinit var container: AppContainer
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val requests = CopyOnWriteArrayList<String>()
    private val directory get() = File(context.filesDir, "trending")

    private fun html(period: String, language: String = "") = "<h1>Trending</h1><a href='/trending/python?since=$period'>Python</a><a href='/trending/kotlin?since=$period'>Kotlin</a>" + (1..8).joinToString("") { i ->
        val range = when (period) { "weekly" -> "this week"; "monthly" -> "this month"; else -> "today" }
        val name = "${language.ifEmpty { period }}-$i"
        """<article class='Box-row'><h2><a href='/acme/$name' data-hydro-click='{"payload":{"record_id":${100 + i}}}'>$name</a></h2><p>Useful open source project $i</p><span itemprop='programmingLanguage'>Python</span><a href='/acme/$name/stargazers'>1,000</a><span class='float-sm-right'>${100 + i} stars $range</span></article>"""
    }

    @Before fun setup() = runBlocking {
        directory.deleteRecursively()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request.path.orEmpty()
                val url = request.requestUrl!!
                val body = when {
                    url.encodedPath.startsWith("/trending") -> html(url.queryParameter("since") ?: "daily", url.pathSegments.getOrNull(1).orEmpty())
                    url.encodedPath == "/search/repositories" -> """{"total_count":0,"items":[]}"""
                    url.encodedPath.startsWith("/repos/acme/") && url.pathSegments.size == 3 -> {
                        val name = url.pathSegments.last()
                        """{"id":101,"name":"$name","full_name":"acme/$name","owner":{"id":1,"login":"acme"},"description":"Repository detail from API","html_url":"https://github.com/acme/$name"}"""
                    }
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setHeader("Content-Type", if (url.encodedPath.startsWith("/trending")) "text/html" else "application/json").setBody(body)
            }
        }
        server.start()
        container = AppContainer(context, trending = TrendingRepository(directory, baseUrl = server.url("/"))) { token -> createApi(token, server.url("/").toString()) }
        container.repository.logout()
        container.local.updatePreferences { Preferences(clipboardLinks = false, tabs = listOf(MainTab.Trending, MainTab.Library), home = MainTab.Trending) }
        container.trendingNotifications.setEnabled(false)
        context.getSystemService(NotificationManager::class.java).cancel(TrendingNotifications.NOTIFICATION_ID)
    }
    @After fun cleanup() = runBlocking {
        container.trendingNotifications.setEnabled(false)
        container.local.updatePreferences { Preferences(clipboardLinks = false) }
        server.shutdown()
    }
    private fun waitFor(text: String) { compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }

    @Test fun completeBoardRetainsMutedAndSavedProjectsAndOpensRepositoryInsideApp() = runBlocking {
        container.local.updatePreferences { it.copy(interests = listOf("kotlin"), mutedRepositories = listOf(101L)) }
        compose.setContent { RepoRoveApp(container) }
        waitFor("daily-1")
        compose.onNodeWithTag("trending-list").performScrollToNode(hasText("daily-8"))
        compose.onNodeWithText("daily-8").assertExists()
        compose.onNodeWithTag("trending-list").performScrollToNode(hasText("daily-1"))
        compose.onNodeWithContentDescription("稍后看 acme/daily-1").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("移出稍后看 acme/daily-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("daily-1").assertExists().performClick()
        waitFor("Repository detail from API")
        compose.waitUntil(5000) { runBlocking { container.trending.history().opened.any { it.fullName == "acme/daily-1" } } }
        compose.onNodeWithContentDescription("返回").performClick()
        waitFor("完整热榜")
        assertEquals(8, container.trending.board(TrendingPeriod.Daily).data.entries.size)
    }

    @Test fun rangesAndLanguageFiltersPersistWithoutChangingRecommendationInterests() = runBlocking {
        container.local.updatePreferences { it.copy(interests = listOf("android")) }
        compose.setContent { RepoRoveApp(container) }
        waitFor("daily-1")
        compose.onNodeWithText("周榜").performClick()
        waitFor("weekly-1")
        compose.onNodeWithText("全部语言").performClick()
        compose.onNodeWithTag("trending-language:python").performClick()
        waitFor("python-1")
        val prefs = container.local.preferences.first()
        assertEquals(TrendingPeriod.Weekly, prefs.trendingPeriod)
        assertEquals("python", prefs.trendingLanguage)
        assertEquals(listOf("android"), prefs.interests)
        assertTrue(requests.any { it.startsWith("/trending/python?") && it.contains("since=weekly") })
    }

    @Test fun toolbarEntryWorksWithoutInterestsAndHeatBoardCanBeTheStartupPage() = runBlocking {
        container.local.updatePreferences { Preferences(clipboardLinks = false) }
        compose.setContent { RepoRoveApp(container) }
        compose.onNodeWithContentDescription("热榜").performClick()
        waitFor("daily-1")
        compose.onNodeWithText("今日精选").performClick()
        waitFor("为你精选")
        compose.onNodeWithText("查看完整热榜").performClick()
        waitFor("完整热榜")
        container.local.updatePreferences { it.copy(tabs = listOf(MainTab.Trending, MainTab.Library), home = MainTab.Trending) }
        assertEquals(MainTab.Trending, container.local.preferences.first().home)
    }

    @Test fun aNotificationsDefaultOffAndPermissionDenialNeverStartsSourceRequests() = runBlocking {
        assertFalse(container.local.preferences.first().trendingNotifications)
        assertNull(context.getSystemService(JobScheduler::class.java).getPendingJob(TrendingNotifications.JOB_ID))
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS))
        container.trendingNotifications.setEnabled(true)
        val before = server.requestCount
        assertEquals(DigestDelivery.NoPermission, container.trendingNotifications.check())
        assertEquals(before, server.requestCount)
        container.trendingNotifications.setEnabled(false)
        compose.setContent { RepoRoveApp(container) }
        waitFor("daily-1")
    }

    @Test fun realNotificationIsDeduplicatedAndTapOpensTheSavedSelection() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        container.trendingNotifications.setEnabled(true)
        assertNotNull(context.getSystemService(JobScheduler::class.java).getPendingJob(TrendingNotifications.JOB_ID))
        assertEquals(DigestDelivery.Posted, container.trendingNotifications.check())
        val requestCount = server.requestCount
        assertEquals(DigestDelivery.AlreadySent, container.trendingNotifications.check())
        assertEquals(requestCount, server.requestCount)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.single { it.id == TrendingNotifications.NOTIFICATION_ID }.notification
        val day = LocalDate.now().toString()
        assertEquals(day, container.trending.savedDigest(day)!!.day)
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            notification.contentIntent.send()
            val activity = monitor.waitForActivityWithTimeout(10000)
            assertNotNull(activity)
            try { waitFor("$day 的精选") } finally { instrumentation.runOnMainSync { activity.finish() } }
        } finally { instrumentation.removeMonitor(monitor) }
        container.trendingNotifications.setEnabled(false)
        assertNull(context.getSystemService(JobScheduler::class.java).getPendingJob(TrendingNotifications.JOB_ID))
        assertTrue(manager.activeNotifications.none { it.id == TrendingNotifications.NOTIFICATION_ID })
    }
}
