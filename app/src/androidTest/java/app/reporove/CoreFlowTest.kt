package app.reporove

import android.app.Application
import android.app.Activity
import android.webkit.WebView
import android.view.View
import android.view.ViewGroup
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.activity.compose.LocalActivity
import androidx.test.platform.app.InstrumentationRegistry
import app.reporove.core.model.*
import app.reporove.core.network.*
import app.reporove.ui.RepoRoveApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/** Real Compose/navigation/storage/network stack, deterministic HTTP fixtures. */
class CoreFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: MockWebServer
    private lateinit var container: AppContainer
    private var activity: Activity? = null
    private val queries = CopyOnWriteArrayList<String>()
    private val markupRequests = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var starred = false
    @Volatile private var showCi = false
    @Volatile private var showReviews = false
    private val requestedPaths = CopyOnWriteArrayList<String>()
    private val repository = """{"id":71,"name":"paper-reader","full_name":"demo/paper-reader","owner":{"id":9,"login":"demo"},"description":"A quiet place to read code and discover useful projects.","language":"Kotlin","stargazers_count":4200,"forks_count":210,"open_issues_count":12,"default_branch":"main","html_url":"https://github.com/demo/paper-reader","pushed_at":"2026-10-05T12:00:00Z","topics":["android","productivity"]}"""
    private val release = """{"id":5,"tag_name":"v1.2.0","name":"Paper Reader 1.2","body":"## Changes\nFaster browsing and a calmer reading experience.","published_at":"2026-10-04T00:00:00Z","html_url":"https://github.com/demo/paper-reader/releases/tag/v1.2.0","zipball_url":"https://api.github.com/repos/demo/paper-reader/zipball/v1.2.0","tarball_url":"https://api.github.com/repos/demo/paper-reader/tarball/v1.2.0","assets":[{"id":500,"name":"reader-arm64.apk","size":1024,"content_type":"application/vnd.android.package-archive","browser_download_url":"https://github.com/demo/paper-reader/releases/download/v1.2.0/reader-arm64.apk"}]}"""
    private val issue = """{"id":8,"number":3,"title":"Improve reading layout","state":"open","user":{"login":"contributor"},"body":"Please keep the page easy to read.","html_url":"https://github.com/demo/paper-reader/issues/3"}"""
    private val readme = "# Paper Reader\n\nRead comfortably on your phone.\n\n## Features\n\n- Fast project discovery\n- Local reading list\n- Release downloads\n\n| Mode | Purpose |\n| --- | --- |\n| Light | Daytime reading |\n| Dark | Evening reading |\n\n[License](LICENSE)"

    @Before fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val path = url.encodedPath
                requestedPaths += path
                if (path.startsWith("/search/")) queries += url.queryParameter("q").orEmpty()
                val body = when {
                    path == "/markdown" && request.body.clone().readUtf8().contains("```diff") -> return MockResponse().setHeader("Content-Type", "text/html").setBody("<pre><code>@@ -1 +1 @@\n-old\n+new</code></pre>")
                    path == "/markdown" && request.body.clone().readUtf8().contains("Exact line comment 77") -> return MockResponse().setHeader("Content-Type", "text/html").setBody("<p>Exact line comment 77</p>")
                    path == "/markdown" && request.body.clone().readUtf8().contains("Exact review target 99") -> return MockResponse().setHeader("Content-Type", "text/html").setBody("<p>Exact review target 99</p>")
                    path == "/markdown" -> { markupRequests.incrementAndGet(); return MockResponse().setHeader("Content-Type", "text/html").setBody("""<h1>Paper Reader</h1><p>😄 <strong>粗体</strong> <a href="https://github.com/demo">作者主页</a> <a href="#features">章节</a></p><h2 id="user-content-features">Features</h2><div class="markdown-alert markdown-alert-note"><p class="markdown-alert-title">Note</p><p>提示正文</p></div><div class="highlight highlight-source-kotlin"><pre><span class="pl-k">val</span> x = 1</pre></div><table><tr><th>Mode</th><th>Purpose</th></tr><tr><td>Light</td><td>Reading</td></tr></table><details><summary>展开</summary><p>细节</p></details><p>Reading content</p>""" + "<p>Long reading content for scroll preservation.</p>".repeat(30)) }
                    path == "/users/demo" -> """{"id":9,"login":"demo","name":"Demo Author","bio":"Author profile inside RepoRove","public_repos":3,"followers":12,"following":2,"html_url":"https://github.com/demo"}"""
                    path == "/users/reader" -> """{"id":44,"login":"reader","name":"Reader Test","public_repos":1,"followers":5,"following":3,"html_url":"https://github.com/reader"}"""
                    path == "/user/memberships/orgs" -> """[{"state":"active","role":"admin","organization":{"id":92,"login":"demo-org","name":"Demo Organization","avatar_url":"","type":"Organization"}}]"""
                    path == "/users/demo-org" || path == "/orgs/demo-org" -> """{"id":92,"login":"demo-org","name":"Demo Organization","type":"Organization","public_repos":1,"html_url":"https://github.com/demo-org"}"""
                    path == "/orgs/demo-org/repos" -> "[$repository,${repository.replace("71", "72").replace("paper-reader", "private-one").replace("\"topics\"", "\"private\":true,\"topics\"")},${repository.replace("71", "73").replace("paper-reader", "private-two").replace("\"topics\"", "\"private\":true,\"topics\"")}]"
                    path == "/graphql" -> {
                        val graph = AppJson.decodeFromString<GraphRequest>(request.body.readUtf8())
                        if (graph.query.contains("totalCount")) """{"data":{"organization":{"repositories":{"totalCount":3}}}}"""
                        else if (graph.query.contains("pinnedItems")) """{"data":{"${if (graph.query.contains("organization(")) "organization" else "user"}":{"pinnedItems":{"nodes":[{"nameWithOwner":"demo/paper-reader"}]}}}}"""
                        else return MockResponse().setResponseCode(400)
                    }
                    path.endsWith("/languages") -> """{"Kotlin":750,"Java":250}"""
                    path.contains("/compare/") -> """{"total_commits":1,"commits":[{"sha":"${"b".repeat(40)}","commit":{"message":"Push target commit"}}],"files":[]}"""
                    path == "/users/reader/received_events" -> """[{"id":"push-1","type":"PushEvent","actor":{"login":"demo"},"repo":{"name":"demo/paper-reader"},"created_at":"2026-10-06T00:00:00Z","payload":{"head":"${"b".repeat(40)}","before":"${"a".repeat(40)}"}}]"""
                    path.endsWith("/actions/runs/900/jobs") -> """{"jobs":[{"id":901,"name":"Build package","status":"completed","conclusion":"failure","steps":[]}]}"""
                    path.endsWith("/actions/runs/900") -> """{"id":900,"name":"CI","display_title":"CI target run #900","status":"completed","conclusion":"failure","html_url":"https://github.com/demo/paper-reader/actions/runs/900","updated_at":"2026-10-06T00:00:00Z"}"""
                    path.endsWith("/actions/runs") && url.queryParameter("check_suite_id") == "90" -> """{"workflow_runs":[{"id":900,"name":"CI","display_title":"CI target run #900","status":"completed","conclusion":"failure","html_url":"https://github.com/demo/paper-reader/actions/runs/900","updated_at":"2026-10-06T00:00:00Z"}]}"""
                    path == "/user" -> """{"id":44,"login":"reader","name":"Reader Test","html_url":"https://github.com/reader"}"""
                    path.startsWith("/search/repositories") -> """{"total_count":1,"items":[$repository]}"""
                    path.startsWith("/search/issues") -> """{"total_count":1,"items":[$issue]}"""
                    path == "/repos/demo/paper-reader" -> repository
                    path.endsWith("/readme") -> """{"name":"README.md","path":"README.md","type":"file","size":${readme.toByteArray().size},"encoding":"base64","content":"${Base64.getEncoder().encodeToString(readme.toByteArray())}"}"""
                    path.endsWith("/releases/5") -> release
                    path.endsWith("/releases") -> "[$release]"
                    path.endsWith("/branches") -> """[{"name":"main"},{"name":"feature/reading"}]"""
                    path.contains("/contents/") -> """[{"name":"README.md","path":"README.md","type":"file","size":200},{"name":"src","path":"src","type":"dir"}]"""
                    path.endsWith("/issues/3") -> issue
                    path.endsWith("/pulls/3") -> issue
                    path.endsWith("/pulls/3/reviews/99") -> """{"id":99,"state":"CHANGES_REQUESTED","user":{"login":"reviewer"},"body":"Exact review target 99","html_url":"https://github.com/demo/paper-reader/pull/3#pullrequestreview-99"}"""
                    path.endsWith("/pulls/comments/77") -> """{"id":77,"user":{"login":"reviewer"},"body":"Exact line comment 77","path":"src/Reader.kt","diff_hunk":"@@ -1 +1 @@\n-old\n+new","html_url":"https://github.com/demo/paper-reader/pull/3#discussion_r77"}"""
                    path.endsWith("/comments") -> "[]"
                    path.endsWith("/issues") || path.endsWith("/pulls") -> "[$issue]"
                    path.endsWith("/actions/runs") -> """{"workflow_runs":[]}"""
                    path == "/user/starred/demo/paper-reader" -> {
                        if (request.method == "PUT") starred = true
                        if (request.method == "DELETE") starred = false
                        return MockResponse().setResponseCode(if (request.method != "GET" || starred) 204 else 404)
                    }
                    path == "/user/starred" -> if (starred) "[$repository]" else "[]"
                    path == "/user/repos" -> "[$repository]"
                    path == "/notifications" && showReviews -> """[{"id":"comment-1","unread":true,"reason":"mention","repository":$repository,"subject":{"title":"Line comment notification","type":"PullRequest","url":"https://api.github.com/repos/demo/paper-reader/pulls/3","latest_comment_url":"https://api.github.com/repos/demo/paper-reader/pulls/comments/77"},"updated_at":"2026-10-06T00:00:00Z"},{"id":"review-1","unread":true,"reason":"review_requested","repository":$repository,"subject":{"title":"Review notification","type":"PullRequest","url":"https://api.github.com/repos/demo/paper-reader/pulls/3/reviews/99"},"updated_at":"2026-10-06T00:00:00Z"}]"""
                    path == "/notifications" && showCi -> """[{"id":"ci-1","unread":true,"reason":"ci_activity","repository":$repository,"subject":{"title":"CI failed notification","type":"CheckSuite","url":"https://api.github.com/repos/demo/paper-reader/check-suites/90"},"updated_at":"2026-10-06T00:00:00Z"}]"""
                    path == "/notifications" -> """[{"id":"99","unread":true,"reason":"subscribed","repository":$repository,"subject":{"title":"Improve reading layout","type":"Issue","url":"https://api.github.com/repos/demo/paper-reader/issues/3"},"updated_at":"2026-10-06T00:00:00Z"}]"""
                    path.startsWith("/notifications/threads/") -> return MockResponse().setResponseCode(205)
                    path.endsWith("/events") -> "[]"
                    else -> return MockResponse().setResponseCode(404).setBody("{}")
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        container = AppContainer(application) { token ->
            val client = OkHttpClient.Builder().addNetworkInterceptor(GitHubHeaders(token, "localhost")).build()
            createApi(token, server.url("/").toString(), client)
        }
        runBlocking {
            container.repository.logout()
            container.local.updatePreferences { Preferences() }
            container.local.collections.first().later.forEach { container.local.toggleLater(it) }
            container.local.collections.first().following.forEach { container.local.toggleFollowing(it) }
        }
    }
    @After fun teardown() {
        runBlocking { container.repository.logout(); container.local.updatePreferences { Preferences() } }
        server.shutdown()
    }
    private fun launch() { compose.setContent { activity = LocalActivity.current; RepoRoveApp(container, activity?.window) }; waitFor("paper-reader") }
    private fun waitFor(text: String) {
        try { compose.waitUntil(15000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) { screenshot("failure"); throw error }
    }
    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val painted = CountDownLatch(1)
        instrumentation.runOnMainSync { findWeb(activity?.window?.decorView)?.postVisualStateCallback(1, object : WebView.VisualStateCallback() { override fun onComplete(requestId: Long) { painted.countDown() } }) ?: painted.countDown() }
        check(painted.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        // WebView's visual callback can precede the emulator's composited frame.
        android.os.SystemClock.sleep(3000)
        val directory = instrumentation.targetContext.getExternalFilesDir("screenshots")!!
        directory.mkdirs()
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap -> File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }

    private fun findWeb(view: View?): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun js(script: String, webIndex: Int = 0): String {
        val latch = CountDownLatch(1); var answer = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val views = mutableListOf<WebView>()
            fun collect(view: View?) {
                if (view is WebView) views += view
                else if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
            }
            collect(activity?.window?.decorView)
            views.getOrNull(webIndex)?.evaluateJavascript(script) { answer = it; latch.countDown() } ?: latch.countDown()
        }
        check(latch.await(5, TimeUnit.SECONDS))
        return answer
    }
    private fun waitForDocument() { compose.waitUntil(15000) { js("document.documentElement.clientHeight > 500 && !!document.querySelector('pre')") == "true" } }
    private fun anyDocumentMatches(script: String): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val views = mutableListOf<WebView>()
        instrumentation.runOnMainSync {
            fun collect(view: View?) {
                if (view is WebView) views += view
                else if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
            }
            collect(activity?.window?.decorView)
        }
        if (views.isEmpty()) return false
        val done = CountDownLatch(views.size)
        val matched = java.util.concurrent.atomic.AtomicBoolean()
        instrumentation.runOnMainSync { views.forEach { view -> view.evaluateJavascript(script) { if (it == "true") matched.set(true); done.countDown() } } }
        check(done.await(5, TimeUnit.SECONDS))
        return matched.get()
    }

    @Test fun readmeLinksCopyAndOwnerOpenNativeProfile() {
        launch()
        compose.onNodeWithText("paper-reader").performClick()
        waitFor("阅读全文")
        compose.onAllNodesWithText("阅读全文").onFirst().performClick()
        waitForDocument()
        assertEquals("true", js("document.body.textContent.includes('😄') && !!document.querySelector('details') && !!document.querySelector('.markdown-alert') && !!document.querySelector('.pl-k')"))
        js("document.querySelector('a[href*=\"/copy/0\"]').click();true")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboard = activity!!.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertEquals("val x = 1", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        js("document.querySelector('a[href=\"https://github.com/demo\"]').click();true")
        waitFor("Demo Author")
        compose.onNodeWithText("Author profile inside RepoRove").assertIsDisplayed()
        screenshot("native-profile")
        compose.onNodeWithContentDescription("返回").performClick()
        waitForDocument()
    }

    @Test fun themesShareReaderSemanticsAndPreservePositionWithoutRenderingAgain() {
        launch(); compose.onNodeWithText("paper-reader").performClick(); waitFor("阅读全文")
        compose.onAllNodesWithText("阅读全文").onFirst().performClick(); waitForDocument()
        js("window.scrollTo(0,300);true")
        val requests = markupRequests.get()
        runBlocking { container.local.updatePreferences { it.copy(themeId = ThemeId.Paper, theme = ThemeMode.Dark) } }
        compose.waitUntil(15000) { js("getComputedStyle(document.body).backgroundColor").contains("25, 29, 23") }
        assertEquals("300", js("Math.round(window.scrollY)"))
        assertEquals(requests, markupRequests.get())
        assertTrue(js("getComputedStyle(document.querySelector('a')).color").contains("121, 192, 255"))
        screenshot("readme-paper-dark")
        js("document.querySelector('a[href=\"https://github.com/demo\"]').click();true")
        waitFor("Demo Author")
        compose.onNodeWithContentDescription("返回").performClick()
        waitForDocument()
        compose.waitUntil(5000) { js("Math.round(window.scrollY)") == "300" }
        assertEquals(requests, markupRequests.get())
    }

    @Test fun interestEditsReplaceOldChipsAndCancelDoesNotPersist() {
        runBlocking { container.local.updatePreferences { it.copy(interests = listOf("android")) } }
        launch()
        compose.onNodeWithText("android", substring = false).performClick()
        compose.onNodeWithText("调整兴趣").performClick()
        waitFor("已选 1 个")
        compose.onNodeWithText("清空").performClick()
        compose.onNodeWithText("输入或搜索兴趣主题").performTextInput("rust")
        compose.onNodeWithText("添加主题 rust", substring = false).assertExists()
        compose.onNodeWithTag("interests-list").performScrollToNode(hasContentDescription("兴趣rust"))
        compose.onNodeWithContentDescription("兴趣rust").performClick()
        compose.onNodeWithText("保存兴趣").performClick()
        compose.waitUntil(10000) { runBlocking { container.local.preferences.first().interests == listOf("rust") } }
        compose.onNodeWithText("android", substring = false).assertDoesNotExist()
        compose.onNodeWithText("rust", substring = false).assertExists()
        compose.onNodeWithText("调整兴趣").performClick()
        compose.onNodeWithText("清空").performClick()
        compose.onNodeWithText("取消", substring = false).performClick()
        assertEquals(listOf("rust"), runBlocking { container.local.preferences.first().interests })
    }

    @Test fun typedCatalogAndCustomTopicsPersistWithoutCheckboxes() {
        launch(); compose.onNodeWithText("调整兴趣").performClick(); waitFor("已选 0 个")
        compose.onNodeWithText("输入或搜索兴趣主题").performTextInput(" Python ")
        compose.onNodeWithText("保存兴趣").performClick()
        compose.waitUntil(10000) { runBlocking { container.local.preferences.first().interests == listOf("python") } }
        compose.onNodeWithText("python", substring = false).assertExists()
        compose.onNodeWithText("调整兴趣").performClick(); waitFor("已选 1 个")
        compose.onNodeWithText("输入或搜索兴趣主题").performTextInput("my-custom-reporove-topic")
        compose.onNodeWithText("保存兴趣").performClick()
        try { compose.waitUntil(10000) { runBlocking { "my-custom-reporove-topic" in container.local.preferences.first().interests } } }
        catch (error: Throwable) { screenshot("interests-failure"); println("INTEREST_STATE=${runBlocking { container.local.preferences.first().interests }}"); compose.onRoot().printToLog("InterestFailure"); throw error }
        compose.onNodeWithText("调整兴趣").performClick(); waitFor("已选 2 个")
        compose.onNodeWithText("my-custom-reporove-topic", substring = false).assertExists()
        screenshot("interests-custom")
    }

    @Test fun discoverSaveReadAndRelease() {
        launch(); screenshot("discover-light")
        compose.onNodeWithText("paper-reader").performClick()
        waitFor("阅读全文")
        compose.waitUntil(15000) { js("document.body.textContent.includes('Paper Reader')") == "true" }
        screenshot("repository")
        compose.onNodeWithText("保存到 App").performClick()
        compose.onNodeWithText("稍后看").performClick()
        waitFor("已保存")
        compose.onAllNodesWithText("阅读全文").onFirst().performClick()
        waitFor("README")
        waitForDocument()
        screenshot("readme")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("overview-list").performScrollToNode(hasText("全部版本"))
        compose.onNodeWithText("全部版本").performClick()
        waitFor("Paper Reader 1.2")
        compose.onNodeWithText("Paper Reader 1.2").performClick()
        waitFor("v1.2.0")
        assertEquals(1, runBlocking { container.local.collections.first().later.size })
    }
    @Test fun globalHiddenColumnsRemainAccessibleAndPreferencesPersist() {
        launch()
        compose.onNodeWithContentDescription("我的").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        screenshot("settings-categories")
        compose.onNodeWithText("仓库布局").performClick()
        compose.onNodeWithContentDescription("显示代码").performScrollTo().performClick()
        compose.onNodeWithContentDescription("显示PR").performScrollTo().performClick()
        compose.onNodeWithContentDescription("显示概览").performScrollTo().performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("外观与阅读").performClick()
        compose.onNodeWithText("深色").performClick()
        compose.waitUntil(5000) { runBlocking { container.local.preferences.first().theme == ThemeMode.Dark } }
        screenshot("settings-dark")
        val loaded = runBlocking { app.reporove.core.storage.LocalStore(InstrumentationRegistry.getInstrumentation().targetContext).preferences.first() }
        assertFalse(RepoTab.Code in loaded.repoTabs); assertFalse(RepoTab.Pulls in loaded.repoTabs)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasText("发现") and hasClickAction()).performClick()
        waitFor("paper-reader"); screenshot("discover-dark")
        compose.onNodeWithText("paper-reader").performClick()
        waitFor("更多")
        compose.onNodeWithText("更多").performClick()
        compose.onNodeWithText("代码").assertIsDisplayed().performClick()
        waitFor("src")
        compose.onNodeWithText("README.md").assertIsDisplayed()
    }
    @Test fun directoryRowsAndReleaseNotesPrecedeJumpToFiles() {
        launch(); compose.onNodeWithText("paper-reader").performClick(); waitFor("阅读全文")
        compose.onNodeWithText("代码", substring = false).performClick(); waitFor("src")
        compose.onNodeWithText("目录", substring = false).assertDoesNotExist()
        compose.onNodeWithText("200 B", substring = false).assertIsDisplayed()
        screenshot("code-list")
        compose.onNodeWithText("概览", substring = false).performClick()
        compose.onNodeWithTag("overview-list").performScrollToNode(hasText("全部版本"))
        compose.onNodeWithText("全部版本").performClick(); waitFor("Paper Reader 1.2")
        compose.onNodeWithText("下载文件", substring = false).performClick()
        waitFor("版本说明")
        compose.onNodeWithText("版本说明", substring = false).assertIsDisplayed()
        compose.waitUntil(15000) { js("document.body.getBoundingClientRect().height > 1000") == "true" }
        println("RELEASE_HEIGHT=" + js("JSON.stringify({body:document.body.getBoundingClientRect().height,viewport:document.documentElement.clientHeight})"))
        compose.waitUntil(15000) { js("document.documentElement.clientHeight >= document.body.getBoundingClientRect().height - 4") == "true" }
        screenshot("release-notes-first")
        compose.onNodeWithText("跳到下载", substring = false).performClick()
        waitFor("reader-arm64.apk")
        compose.onNodeWithText("reader-arm64.apk").assertIsDisplayed()
        compose.onNodeWithText("源码 ZIP").assertIsDisplayed()
        screenshot("release-downloads")
    }

    @Test fun ciEventsAndMyOrganizationsOpenTheirActualObjects() {
        showCi = true
        runBlocking { container.repository.login("fake-test-token") }
        launch()
        compose.onNodeWithText("收件箱").performClick(); waitFor("CI failed notification")
        compose.onNodeWithText("CI failed notification").performClick()
        waitFor("CI target run #900"); waitFor("Build package")
        assertTrue(requestedPaths.any { it.endsWith("/actions/runs/900") })
        assertFalse(requestedPaths.any { it.endsWith("/actions/runs/90") })
        screenshot("ci-target")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("动态", substring = false).performClick()
        compose.onNodeWithText("关注动态").performClick(); waitFor("推送代码")
        compose.onNodeWithText("推送代码").performClick(); waitFor("Push target commit")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("我的").performClick(); waitFor("我的组织")
        compose.onNodeWithText("我的组织").performScrollTo().performClick(); waitFor("Demo Organization")
        screenshot("my-organizations")
        compose.onNodeWithText("Demo Organization").performClick(); waitFor("3 可访问仓库")
        compose.onNodeWithText("@demo-org · 组织").assertIsDisplayed()
        compose.onNodeWithText("3 可访问仓库").performClick(); waitFor("private-two")
        compose.onNodeWithText("private-one").assertExists()
    }

    @Test fun profilePinsStartCollapsedAndTrackingManagementPreservesLater() {
        runBlocking {
            container.repository.login("fake-test-token")
            val repo = AppJson.decodeFromString<Repository>(repository)
            container.local.toggleFollowing(repo); container.local.toggleLater(repo)
        }
        launch(); compose.onNodeWithContentDescription("我的").performClick(); waitFor("置顶项目")
        try { compose.waitUntil(10000) { compose.onAllNodesWithText("置顶项目 · 1", substring = false).fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) { compose.onNodeWithText("展开", substring = false).performClick(); screenshot("pins-failure"); compose.onRoot().printToLog("PinsFailure"); println("GRAPH_PATHS=$requestedPaths"); throw error }
        compose.onNodeWithText("我的工具").assertDoesNotExist()
        compose.onNodeWithText("Star 的项目", substring = false).assertDoesNotExist()
        compose.onNodeWithText("paper-reader", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索此主页的内容").assertIsDisplayed()
        screenshot("profile-collapsed")
        compose.onNodeWithText("展开", substring = false).performClick(); waitFor("paper-reader")
        compose.onNodeWithText("收起", substring = false).performClick()
        compose.onNodeWithText("paper-reader", substring = false).assertDoesNotExist()
        compose.onNodeWithText("项目库", substring = false).performClick(); waitFor("保存与管理项目")
        compose.onNodeWithText("App 内跟踪", substring = false).assertDoesNotExist()
        val laterX = compose.onNode(hasText("稍后看", substring = false) and hasClickAction()).fetchSemanticsNode().positionInRoot.x
        val myX = compose.onNode(hasText("我的仓库", substring = false) and hasClickAction()).fetchSemanticsNode().positionInRoot.x
        val starX = compose.onNode(hasText("Star", substring = false) and hasClickAction()).fetchSemanticsNode().positionInRoot.x
        assertTrue(laterX < myX && myX < starX)
        compose.onNodeWithText("动态", substring = false).performClick(); waitFor("管理跟踪")
        compose.onNodeWithText("管理跟踪").performClick(); waitFor("停止跟踪")
        screenshot("tracking-management")
        compose.onNodeWithText("停止跟踪").performClick()
        compose.waitUntil(10000) { runBlocking { container.local.collections.first().following.isEmpty() } }
        assertEquals(71L, runBlocking { container.local.collections.first().later.single().id })
        waitFor("撤销"); compose.onNodeWithText("撤销", substring = false).performClick()
        compose.waitUntil(10000) { runBlocking { container.local.collections.first().following.size == 1 } }
    }

    @Test fun languageSharesAndRecommendationUndoPreserveExplicitInterests() {
        runBlocking { container.local.updatePreferences { it.copy(interests = listOf("android")) } }
        launch(); compose.onNodeWithText("paper-reader").performClick(); waitFor("阅读全文")
        compose.onNodeWithContentDescription("查看语言占比").performClick(); waitFor("75.0%")
        compose.onNodeWithText("25.0%").assertIsDisplayed(); screenshot("language-shares")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.onNodeWithContentDescription("返回").performClick(); waitFor("paper-reader")
        compose.onNodeWithContentDescription("推荐反馈").performClick()
        compose.onNodeWithText("不再推荐某类项目", substring = false).performClick()
        val topicName = container.catalogs.explore.topics.first { it.slug == "android" }.name
        compose.onNodeWithText("不再推荐 $topicName 主题", substring = false).performClick()
        compose.waitUntil(10000) { runBlocking { "android" in container.local.preferences.first().mutedTopics } }
        assertEquals(listOf("android"), runBlocking { container.local.preferences.first().interests })
        waitFor("撤销"); compose.onNodeWithText("撤销", substring = false).performClick()
        compose.waitUntil(10000) { runBlocking { container.local.preferences.first().mutedTopics.isEmpty() } }
        waitFor("paper-reader")
        compose.onNodeWithContentDescription("推荐反馈").performClick()
        compose.onNodeWithText("不再推荐这个项目").performClick()
        compose.waitUntil(10000) { runBlocking { 71L in container.local.preferences.first().mutedRepositories } }
        compose.onNodeWithContentDescription("我的").performClick(); compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("推荐排除", substring = false).performClick(); waitFor("demo/paper-reader")
        compose.onNodeWithText("恢复", substring = false).performClick()
        compose.waitUntil(10000) { runBlocking { container.local.preferences.first().mutedRepositories.isEmpty() } }
    }

    @Test fun lineCommentsAndReviewsDisplayTheExactNotifiedObject() {
        showReviews = true
        runBlocking { container.repository.login("fake-test-token") }
        launch(); compose.onNodeWithText("收件箱").performClick(); waitFor("Line comment notification")
        compose.onNodeWithText("Line comment notification").performClick(); waitFor("src/Reader.kt")
        compose.waitUntil(15000) { anyDocumentMatches("document.body?.textContent.includes('Exact line comment 77')===true") }
        assertTrue(requestedPaths.any { it.endsWith("/pulls/comments/77") })
        compose.onNodeWithContentDescription("返回").performClick(); waitFor("Review notification")
        compose.onNodeWithText("Review notification").performClick(); waitFor("此次评审")
        compose.onNodeWithText("请求修改").assertIsDisplayed()
        compose.waitUntil(15000) { anyDocumentMatches("document.body?.textContent.includes('Exact review target 99') && Math.abs(document.documentElement.clientHeight-Math.max(48,document.body.getBoundingClientRect().height))<2") }
        assertTrue(requestedPaths.any { it.endsWith("/pulls/3/reviews/99") })
        screenshot("review-target")
    }

    @Test fun loginStarSearchAndNotificationMutations() {
        launch()
        compose.onNodeWithContentDescription("我的").performClick()
        compose.onNodeWithText("GitHub 访问令牌").performTextInput("fake-test-token")
        compose.onNodeWithText("使用令牌登录").performScrollTo()
        compose.onNodeWithText("使用令牌登录").performClick()
        compose.waitUntil(15000) { container.repository.account.value != null }
        screenshot("after-login")
        compose.onNode(hasText("发现") and hasClickAction()).performClick()
        waitFor("paper-reader")
        compose.onNodeWithText("paper-reader").performClick()
        waitFor("Star")
        compose.onNodeWithText("Star").performClick()
        waitFor("已 Star"); assertTrue(starred)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("搜索").performClick()
        screenshot("search-start")
        compose.onNodeWithText("搜索 GitHub").performTextInput("reader")
        compose.onNode(hasText("搜索", substring = false) and hasClickAction()).performClick()
        waitFor("paper-reader")
        compose.onNodeWithContentDescription("选择搜索类型").performClick()
        compose.onNodeWithText("PR", substring = false).performClick()
        compose.onNode(hasText("搜索", substring = false) and hasClickAction()).performClick()
        waitFor("Improve reading layout")
        assertTrue(queries.any { it == "reader is:pr" })
        compose.onNodeWithText("收件箱").performClick()
        waitFor("Improve reading layout")
        compose.onNodeWithText("完成").performClick()
        waitFor("暂时没有通知")
    }
}
