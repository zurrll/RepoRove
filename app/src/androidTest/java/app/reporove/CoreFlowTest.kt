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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
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
    @get:Rule val testName = org.junit.rules.TestName()
    private lateinit var server: MockWebServer
    private lateinit var container: AppContainer
    private var activity: Activity? = null
    private val queries = CopyOnWriteArrayList<String>()
    private val markupRequests = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var starred = false
    @Volatile private var showCi = false
    @Volatile private var showReviews = false
    @Volatile private var disconnected = false
    @Volatile private var archiveFailure = false
    @Volatile private var privateRepository = false
    @Volatile private var navigationDocument = false
    private val longCode = (1..2300).joinToString("\n") { "val line$it = $it" }
    @Volatile private var responsiveDocument = false
    private val requestedPaths = CopyOnWriteArrayList<String>()
    private val repository = """{"id":71,"name":"paper-reader","full_name":"demo/paper-reader","owner":{"id":9,"login":"demo"},"description":"A quiet place to read code and discover useful projects.","language":"Kotlin","stargazers_count":4200,"forks_count":210,"open_issues_count":12,"default_branch":"main","html_url":"https://github.com/demo/paper-reader","pushed_at":"2026-10-05T12:00:00Z","topics":["android","productivity"]}"""
    private val release = """{"id":5,"tag_name":"v1.2.0","name":"Paper Reader 1.2","body":"## Changes\nFaster browsing and a calmer reading experience.","published_at":"2026-10-04T00:00:00Z","html_url":"https://github.com/demo/paper-reader/releases/tag/v1.2.0","zipball_url":"https://api.github.com/repos/demo/paper-reader/zipball/v1.2.0","tarball_url":"https://api.github.com/repos/demo/paper-reader/tarball/v1.2.0","assets":[{"id":500,"name":"reader-arm64.apk","size":1024,"content_type":"application/vnd.android.package-archive","browser_download_url":"https://github.com/demo/paper-reader/releases/download/v1.2.0/reader-arm64.apk"}]}"""
    private val issue = """{"id":8,"number":3,"title":"Improve reading layout","state":"open","user":{"login":"contributor"},"body":"Please keep the page easy to read.","html_url":"https://github.com/demo/paper-reader/issues/3"}"""
    private val readme = "# Paper Reader\n\nRead comfortably on your phone.\n\n## Features\n\n- Fast project discovery\n- Local reading list\n- Release downloads\n\n| Mode | Purpose |\n| --- | --- |\n| Light | Daytime reading |\n| Dark | Evening reading |\n\n[License](LICENSE)"

    private fun content(path: String, text: String) = """{"name":"${path.substringAfterLast('/')}","path":"$path","type":"file","size":${text.toByteArray().size},"encoding":"base64","content":"${Base64.getEncoder().encodeToString(text.toByteArray())}"}"""
    private fun navigationHtml() = """
        <h1>Navigation document</h1>
        <p><a id="bare-link" href="#deep-section">Bare fragment</a>
        <a id="same-link" href="README.md#deep-section">Same file</a>
        <a id="guide-link" href="docs/guide.md#deep-section">Guide link</a>
        <a id="code-link" href="src/Long.kt#L2100">Code link</a></p>
        ${"<p>Paragraph before the destination.</p>".repeat(65)}
        <div class="markdown-heading"><h2 id="heading-with-hidden-anchor">Deep Section</h2><a id="user-content-deep-section" class="anchor" href="#deep-section"></a></div>
        <p>Target content</p>
        <details><summary>Closed section</summary><h3 id="user-content-中文-说明">中文 说明</h3><p>Nested destination.</p></details>
        ${"<p>Paragraph after the destination.</p>".repeat(30)}
    """.trimIndent()

    private fun guideHtml() = "<h1>Linked Guide</h1>" + "<p>Guide reading paragraph.</p>".repeat(80) + "<h2 id='user-content-deep-section'>Deep Section</h2><p>Guide destination.</p>" + "<p>More guide content.</p>".repeat(30)

    @Before fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val path = url.encodedPath
                requestedPaths += path
                if (disconnected) return MockResponse().setResponseCode(503)
                if (path.contains("/zipball/")) {
                    if (archiveFailure) return MockResponse().setResponseCode(500)
                    val bytes = java.io.ByteArrayOutputStream()
                    java.util.zip.ZipOutputStream(bytes).use { zip ->
                        mapOf("repo-sha/README.md" to readme, "repo-sha/src/Reader.kt" to "package demo\n\nfun read() = \"Hello offline\"\n", "repo-sha/LICENSE" to "Fixture license", "repo-sha/docs/guide.md" to "# Guide\n\n" + "Paragraph for reading.\n\n".repeat(80) + "## Deep Section\n\nEnd of guide.", "repo-sha/src/Long.kt" to longCode).forEach { (name, text) -> zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
                    }
                    return MockResponse().setHeader("Content-Type", "application/zip").setBody(okio.Buffer().write(bytes.toByteArray()))
                }
                if (path.contains("/commits/")) return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"sha":"${"a".repeat(40)}","commit":{"message":"Snapshot commit"}}""")
                if (path.startsWith("/search/")) queries += url.queryParameter("q").orEmpty()
                val body = when {
                    path == "/markdown" && navigationDocument -> {
                        markupRequests.incrementAndGet()
                        return MockResponse().setHeader("Content-Type", "text/html").setBody(if (request.body.clone().readUtf8().contains("# Guide")) guideHtml() else navigationHtml())
                    }
                    path == "/markdown" && responsiveDocument -> {
                        markupRequests.incrementAndGet()
                        val headings = (1..8).joinToString("") { "<th align=\"${if (it == 2) "right" else "left"}\">Column $it</th>" }
                        val cells = (1..8).joinToString("") { "<td>${if (it == 2) "<code>${"long_identifier_".repeat(30)}</code>" else "Readable table content ".repeat(8)}</td>" }
                        return MockResponse().setHeader("Content-Type", "text/html").setBody("""
                            <h1>Mobile content layout</h1>
                            <p><a href="https://github.com/demo/paper-reader">${"long-link-segment-".repeat(30)}</a></p>
                            <table id="wide-table"><thead><tr>$headings</tr></thead><tbody><tr>$cells</tr></tbody></table>
                            <table id="prose-table"><tr><th>Feature</th><th style="text-align:center">Explanation</th></tr><tr><td>Reading</td><td>${"A long explanation should wrap into readable paragraphs. ".repeat(12)}</td></tr></table>
                            <table id="small-table"><tr><th>Item</th><th>State</th></tr><tr><td>A</td><td>Ready</td></tr></table>
                            <div class="highlight highlight-source-kotlin"><pre><code>${"val longName = ".repeat(50)}</code></pre></div>
                            <img width="1200" height="600" alt="Wide illustration">
                            <details><summary>More information</summary><p>Expanded content remains in the reading flow.</p></details>
                        """.trimIndent())
                    }
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
                    path == "/repos/demo/paper-reader" -> if (privateRepository) repository.replace("\"topics\"", "\"private\":true,\"topics\"") else repository
                    path.endsWith("/readme") -> """{"name":"README.md","path":"README.md","type":"file","size":${readme.toByteArray().size},"encoding":"base64","content":"${Base64.getEncoder().encodeToString(readme.toByteArray())}"}"""
                    path.endsWith("/releases/5") -> release
                    path.endsWith("/releases") -> "[$release]"
                    path.endsWith("/branches") -> """[{"name":"main"},{"name":"feature/reading"}]"""
                    path.endsWith("/contents/docs/guide.md") -> content("docs/guide.md", "# Guide\n\n" + "Reading paragraph\n\n".repeat(80))
                    path.endsWith("/contents/src/Long.kt") -> content("src/Long.kt", longCode)
                    path.endsWith("/contents/README.md") -> content("README.md", readme)
                    path.endsWith("/contents/src") -> """[{"name":"Long.kt","path":"src/Long.kt","type":"file","size":50000}]"""
                    path.endsWith("/contents/docs") -> """[{"name":"guide.md","path":"docs/guide.md","type":"file","size":200}]"""
                    path.contains("/contents/") -> """[{"name":"README.md","path":"README.md","type":"file","size":200},{"name":"src","path":"src","type":"dir"},{"name":"docs","path":"docs","type":"dir"}]"""
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
            container.offline.ready.await()
            container.offline.snapshots.value.filter { it.repository.fullName == "demo/paper-reader" }.forEach { container.offline.delete(it.id) }
            container.repository.logout()
            container.local.updatePreferences { Preferences(clipboardLinks = false) }
            container.local.collections.first().later.forEach { container.local.toggleLater(it) }
            container.local.collections.first().following.forEach { container.local.toggleFollowing(it) }
        }
    }
    @After fun teardown() {
        runBlocking { container.offline.snapshots.value.filter { it.repository.fullName == "demo/paper-reader" }.forEach { container.offline.delete(it.id) } }
        compose.waitForIdle()
        runBlocking { container.repository.logout(); container.local.updatePreferences { Preferences(clipboardLinks = false) } }
        // Let preference-driven recomposition finish before the rule destroys the Activity.
        compose.waitForIdle()
        server.shutdown()
    }
    private fun launch() { compose.setContent { activity = LocalActivity.current; RepoRoveApp(container, activity?.window) }; waitFor("paper-reader") }
    private fun waitFor(text: String) {
        try { compose.waitUntil(15000) { runCatching { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) } }
        catch (error: Throwable) { screenshot("failure-${testName.methodName}"); throw error }
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
        // Navigation animations briefly retain the outgoing repository's WebView.
        // Advance Compose to idle before selecting a view, so JS and touch input
        // exercise the reader the user currently sees rather than the old page.
        compose.waitForIdle()
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
    private fun swipeWeb(startX: Float, startY: Float, endX: Float, endY: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val painted = CountDownLatch(1)
        instrumentation.runOnMainSync {
            findWeb(activity?.window?.decorView)!!.postVisualStateCallback(2, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { painted.countDown() }
            })
        }
        check(painted.await(10, TimeUnit.SECONDS))
        // JS scroll callbacks precede compositor updates; input must target the painted table.
        android.os.SystemClock.sleep(500)
        val location = IntArray(2)
        var width = 0
        instrumentation.runOnMainSync { val web = findWeb(activity?.window?.decorView)!!; web.getLocationOnScreen(location); width = web.width }
        val scale = width / js("window.innerWidth").toFloat()
        val downTime = android.os.SystemClock.uptimeMillis()
        fun event(action: Int, step: Int) {
            val fraction = step / 20f
            val input = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action,
                location[0] + (startX + (endX - startX) * fraction) * scale,
                location[1] + (startY + (endY - startY) * fraction) * scale, 0)
            input.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { check(instrumentation.uiAutomation.injectInputEvent(input, true)) } finally { input.recycle() }
        }
        event(android.view.MotionEvent.ACTION_DOWN, 0)
        for (step in 1..20) { android.os.SystemClock.sleep(20); event(android.view.MotionEvent.ACTION_MOVE, step) }
        event(android.view.MotionEvent.ACTION_UP, 20)
        instrumentation.waitForIdleSync()
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

    @Test fun aboutShowsCurrentAndHistoricalVersionChanges() {
        launch(); compose.onNodeWithContentDescription("我的").performClick(); waitFor("访问令牌")
        compose.onNodeWithContentDescription("设置").performClick(); waitFor("关于")
        compose.onNodeWithText("关于", substring = false).performClick(); waitFor("版本更新记录")
        compose.onNodeWithText("版本更新记录").performClick(); waitFor("0.6.1 · 当前")
        compose.onNodeWithText("新增 GitHub 浏览器设备授权登录", substring = true).assertExists()
        compose.onNodeWithText("0.6.0", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("修复 README 章节定位", substring = true).assertExists()
        screenshot("version-history")
        compose.onNodeWithContentDescription("返回").performClick(); waitFor("开源许可与致谢")
        compose.onNodeWithText("开源许可与致谢").performClick()
        compose.waitUntil(15000) { js("document.body.innerText").contains("Robin Stocker") }
    }

    @Test fun documentAnchorsAndCrossFileBackKeepTheSourcePosition() {
        navigationDocument = true
        launch(); compose.onNodeWithText("paper-reader").performClick(); waitFor("阅读全文")
        compose.onAllNodesWithText("阅读全文").onFirst().performClick()
        compose.waitUntil(15000) { js("!!document.querySelector('#bare-link')") == "true" }
        for (link in listOf("bare-link", "same-link")) {
            js("window.scrollTo(0,0);document.getElementById('$link').click();true")
            compose.waitUntil(10000) { js("Math.abs(document.querySelector('.markdown-heading').getBoundingClientRect().top) < 30 && window.scrollY > 500") == "true" }
            compose.onNodeWithContentDescription("刷新 README").assertExists()
        }
        js("window.scrollTo(0,400);true")
        val position = js("window.scrollY").toInt()
        js("document.getElementById('guide-link').click();true")
        waitFor("docs/guide.md")
        compose.waitUntil(15000) { js("document.querySelector('h1')?.innerText === 'Linked Guide' && window.scrollY > 500") == "true" }
        compose.waitForIdle(); android.os.SystemClock.sleep(500)
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        waitFor("README")
        compose.onNodeWithContentDescription("刷新 README").assertExists()
        compose.waitUntil(10000) { js("window.scrollY").toIntOrNull()?.let { kotlin.math.abs(it - position) < 20 } == true }
        js("document.getElementById('code-link').click();true")
        waitFor("src/Long.kt"); waitFor("2001–2300 行")
        compose.waitUntil(5000) {
            var positioned = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                var view: View? = findCodeView(activity!!.window.decorView)
                while (view != null && view !is androidx.core.widget.NestedScrollView) view = view.parent as? View
                positioned = (view as? androidx.core.widget.NestedScrollView)?.scrollY?.let { it > 50 } == true
            }; positioned
        }
        compose.onNodeWithContentDescription("文件操作").performClick(); waitFor("复制整个文件")
        compose.onNodeWithText("复制整个文件").performClick()
        compose.waitUntil(5000) {
            var copied = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val clipboard = activity!!.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString() == longCode
            }; copied
        }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        compose.waitForIdle(); android.os.SystemClock.sleep(500)
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        waitFor("README")
        compose.waitUntil(10000) { js("window.scrollY").toIntOrNull()?.let { kotlin.math.abs(it - position) < 20 } == true }
        // A numeric line link belongs to its original file; it must not force later
        // Markdown files opened in the same browser into source mode.
        js("document.getElementById('code-link').click();true")
        waitFor("src/Long.kt")
        repeat(2) { compose.onNodeWithContentDescription("文件操作").performClick(); compose.onNodeWithText("上一级", substring = false).performClick(); compose.waitForIdle() }
        waitFor("docs"); compose.onNodeWithText("docs", substring = false).performClick()
        waitFor("guide.md"); compose.onNodeWithText("guide.md", substring = false).performClick()
        compose.waitUntil(15000) { js("document.querySelector('h1')?.innerText === 'Linked Guide'") == "true" }
    }

    @Test fun nonReadmeMarkdownScrollsAfterSwitchingModeInsideRepository() {
        navigationDocument = true
        launch(); compose.onNodeWithText("paper-reader").performClick(); waitFor("阅读全文")
        compose.onNodeWithText("代码", substring = false).performClick(); waitFor("根目录")
        waitFor("docs")
        compose.onNodeWithText("docs", substring = false).performClick(); waitFor("guide.md")
        compose.onNodeWithText("guide.md", substring = false).performClick()
        compose.waitUntil(15000) { js("document.querySelector('h1')?.innerText === 'Linked Guide'") == "true" }
        compose.onNodeWithContentDescription("文件操作").performClick(); compose.onNodeWithText("在源码中查找 / 跳行").performClick()
        waitFor("查找与跳行"); compose.onNodeWithText("查找全文", substring = false).assertExists()
        compose.onNodeWithText("关闭", substring = false).performClick()
        compose.onNodeWithContentDescription("文件操作").performClick(); compose.onNodeWithText("阅读模式").performClick()
        compose.waitUntil(15000) { js("document.querySelector('h1')?.innerText === 'Linked Guide'") == "true" }
        val x = js("window.innerWidth/2").toFloat(); val height = js("window.innerHeight").toFloat()
        swipeWeb(x, height * .82f, x, height * .2f)
        try { compose.waitUntil(8000) { js("window.scrollY > 50") == "true" } }
        catch (error: Throwable) { screenshot("failure-md-scroll"); throw AssertionError(js("JSON.stringify({h:innerHeight,y:scrollY,body:document.body.getBoundingClientRect().height,title:document.querySelector('h1')?.innerText})"), error) }
        screenshot("ordinary-markdown-scroll")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        waitFor("guide.md")
    }

    @Test fun nativeLongCodeCanSwipeAndFindMarksTheMatchingText() {
        launch(); compose.onNodeWithText("paper-reader").performClick(); waitFor("阅读全文")
        compose.onNodeWithText("代码", substring = false).performClick(); waitFor("src")
        compose.onNodeWithText("src", substring = false).performClick(); waitFor("Long.kt")
        compose.onNodeWithText("Long.kt", substring = false).performClick(); waitFor("1–1000 行")
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val nativeActions = java.util.concurrent.CopyOnWriteArrayList<Int>()
        var before = 0; val location = IntArray(2); var width = 0; var height = 0
        instrumentation.runOnMainSync {
            val codeView = findCodeView(activity!!.window.decorView)!!
            codeView.setOnTouchListener { _, event -> nativeActions += event.actionMasked; false }
            var view: View? = codeView
            while (view != null && view !is androidx.core.widget.NestedScrollView) view = view.parent as? View
            val scroller = view as androidx.core.widget.NestedScrollView
            before = scroller.scrollY; scroller.getLocationOnScreen(location); width = scroller.width; height = scroller.height
        }
        repeat(3) {
            compose.waitForIdle()
            instrumentation.runOnMainSync {
                var view: View? = findCodeView(activity!!.window.decorView)
                while (view != null && view !is androidx.core.widget.NestedScrollView) view = view.parent as? View
                val scroller = view as androidx.core.widget.NestedScrollView
                scroller.getLocationOnScreen(location); width = scroller.width; height = scroller.height
            }
            val down = android.os.SystemClock.uptimeMillis()
            for (step in 0..20) {
                val event = android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), when (step) { 0 -> 0; 20 -> 1; else -> 2 }, location[0] + width / 2f, location[1] + height * (.8f - .6f * step / 20), 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
                android.os.SystemClock.sleep(20)
            }
        }
        try { compose.waitUntil(8000) {
            var moved = false
            instrumentation.runOnMainSync {
                var view: View? = findCodeView(activity!!.window.decorView)
                while (view != null && view !is androidx.core.widget.NestedScrollView) view = view.parent as? View
                moved = (view as androidx.core.widget.NestedScrollView).scrollY > before + 50
            }; moved
        } }
        catch (error: Throwable) {
            var diagnostic = ""
            instrumentation.runOnMainSync {
                val code = findCodeView(activity!!.window.decorView)!!
                var view: View? = code
                while (view != null && view !is androidx.core.widget.NestedScrollView) view = view.parent as? View
                val scroller = view as androidx.core.widget.NestedScrollView
                diagnostic = "code=${code.width}x${code.height} lines=${code.layout?.lineCount} selection=${code.selectionStart}-${code.selectionEnd} textScrollY=${code.scrollY} scroll=${scroller.scrollY}, scroller=${scroller.width}x${scroller.height}, child=${scroller.getChildAt(0).height}, location=${location.toList()}, focused=${code.hasWindowFocus()}, nativeActions=$nativeActions"
            }
            screenshot("failure-native-code-scroll"); throw AssertionError(diagnostic, error)
        }
        compose.onNodeWithContentDescription("文件操作").performClick(); compose.onNodeWithText("文件内查找 / 跳行").performClick()
        compose.onNodeWithText("查找全文").performTextInput("line2100")
        compose.onNodeWithText("查找", substring = false).performClick(); waitFor("2001–2300 行")
        compose.waitUntil(5000) {
            var highlighted = false
            instrumentation.runOnMainSync {
                val text = findCodeView(activity!!.window.decorView)?.text as? android.text.Spanned
                highlighted = text?.getSpans(0, text.length, android.text.style.BackgroundColorSpan::class.java)?.isNotEmpty() == true
            }; highlighted
        }
    }

    @Test fun offlineLinksKeepAnchorsAndReturnToLocalReadme() {
        navigationDocument = true
        runBlocking { container.offline.save(app.reporove.core.offline.SaveRequest("demo/paper-reader", "main", true, true)) }
        launch(); disconnected = true
        compose.onNodeWithText("项目库", substring = false).performClick()
        waitFor("离线与最近阅读"); compose.onNodeWithText("离线与最近阅读").performClick(); waitFor("阅读")
        compose.onNodeWithText("阅读", substring = false).performClick()
        compose.waitUntil(15000) { js("!!document.querySelector('#guide-link')") == "true" }
        js("(()=>{const a=document.createElement('a');a.href='https://github.com/demo/paper-reader/blob/main/README.md#deep-section';document.body.append(a);a.click();return true})()")
        compose.waitUntil(10000) { js("window.scrollY > 500") == "true" }
        compose.onNodeWithContentDescription("已保存的仓库信息").assertExists()
        js("window.scrollTo(0,400);document.getElementById('guide-link').click();true")
        waitFor("docs/guide.md")
        compose.waitUntil(15000) { js("document.querySelector('h1')?.innerText === 'Guide' && !!document.getElementById('user-content-deep-section') && window.scrollY > 500") == "true" }
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(10000) { js("!!document.querySelector('#guide-link') && window.scrollY > 300") == "true" }
    }

    @Test fun offlineSnapshotReadsFilesWithoutNetworkAndDeletesDiskFiles() {
        launch()
        runBlocking { container.offline.save(app.reporove.core.offline.SaveRequest("demo/paper-reader", "main", true, true)) }
        val snapshot = container.offline.snapshots.value.single()
        assertEquals("a".repeat(40), snapshot.sha)
        assertTrue(requestedPaths.any { it.endsWith("/zipball/${snapshot.sha}") })
        disconnected = true
        compose.onNodeWithText("项目库", substring = false).performClick()
        waitFor("离线与最近阅读"); compose.onNodeWithText("离线与最近阅读").performClick()
        waitFor("阅读"); compose.onNodeWithText("阅读", substring = false).performClick()
        try { waitForDocument() } catch (e: Throwable) { screenshot("offline-readme-failure"); throw AssertionError("Offline DOM " + js("JSON.stringify({height:document.documentElement.clientHeight,body:document.body.innerText,html:document.body.innerHTML})"), e) }
        assertTrue(js("document.body.innerText").contains("Reading content"))
        compose.onNodeWithText("源码", substring = false).performClick(); waitFor("src")
        compose.onNodeWithText("src", substring = false).performClick(); waitFor("Reader.kt")
        compose.onNodeWithText("Reader.kt", substring = false).performClick()
        compose.waitUntil(10000) { var present = false; InstrumentationRegistry.getInstrumentation().runOnMainSync { present = findCodeView(activity!!.window.decorView)?.text?.contains("Hello offline") == true }; present }
        compose.onNodeWithContentDescription("文件树").performClick(); waitFor("文件树")
        screenshot("offline-code-tree")
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "offline-repositories/${snapshot.id}")
        assertTrue(File(dir, "source.zip").exists())
        runBlocking { container.offline.delete(snapshot.id) }
        assertFalse(dir.exists()); assertNull(container.offline.find(snapshot.id))
    }

    @Test fun failedSnapshotUpdatePreservesOldFilesAndPrivateLogoutPurgesSnapshots() {
        runBlocking {
            container.offline.save(app.reporove.core.offline.SaveRequest("demo/paper-reader", "main", false, true))
            val original = container.offline.snapshots.value.single()
            assertNull(original.readme); assertTrue(original.entries.isNotEmpty())
            archiveFailure = true
            container.offline.save(app.reporove.core.offline.SaveRequest("demo/paper-reader", "main", true, true))
            assertEquals(original.id, container.offline.snapshots.value.single().id)
            assertEquals("LICENSE", container.offline.contents(original.id, "LICENSE").single().name)
            assertFalse(container.offline.progress.value.getValue("demo/paper-reader").running)
            assertNotNull(container.offline.progress.value.getValue("demo/paper-reader").error)
            container.offline.delete(original.id)
            archiveFailure = false; privateRepository = true
            container.repository.login("test-token-not-a-real-secret")
            container.offline.save(app.reporove.core.offline.SaveRequest("demo/paper-reader", "main", true, false))
            val private = container.offline.snapshots.value.single()
            assertTrue(private.repository.isPrivate); assertTrue(private.documents); assertFalse(private.code)
            container.repository.logout()
            assertTrue(container.offline.snapshots.value.isEmpty())
            assertFalse(File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "offline-repositories/${private.id}").exists())
        }
    }
    private fun findCodeView(view: View): app.reporove.ui.CodeTextView? {
        if (view is app.reporove.ui.CodeTextView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findCodeView(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun repositoryHeaderScrollsAwayAndTabsRemainAvailable() {
        launch(); compose.onNodeWithText("paper-reader").performClick(); waitFor("阅读全文")
        compose.waitUntil(15000) { anyDocumentMatches("!!document.querySelector('pre')") }
        val header = compose.onNodeWithTag("repository-header")
        val tabs = compose.onNodeWithTag("repository-tabs")
        val expandedHeight = header.fetchSemanticsNode().boundsInRoot.height
        val expandedTabsTop = tabs.fetchSemanticsNode().boundsInRoot.top
        assertTrue(expandedHeight > 0f)
        screenshot("repository-expanded")
        // Start inside the HTML preview, as a reader would, rather than on a title row.
        compose.onNodeWithTag("overview-list").performTouchInput { swipeUp(startY = height * .55f, endY = height * .02f, durationMillis = 700) }
        compose.waitUntil(5000) { header.fetchSemanticsNode().boundsInRoot.height < 1f }
        assertTrue(tabs.fetchSemanticsNode().boundsInRoot.top < expandedTabsTop - expandedHeight * .9f)
        compose.onNodeWithText("代码", substring = false).assertIsDisplayed().performClick()
        waitFor("根目录"); compose.onNodeWithText("src", substring = false).assertIsDisplayed()
        assertTrue(header.fetchSemanticsNode().boundsInRoot.height < 1f)
        screenshot("repository-collapsed-code")
        // Short lists must restore the header too; no content scroll is required.
        compose.onNodeWithTag("repository-layout").performTouchInput { swipeDown(durationMillis = 700) }
        compose.waitUntil(5000) { header.fetchSemanticsNode().boundsInRoot.height >= expandedHeight - 1f }
        compose.onNodeWithContentDescription("查看语言占比").assertIsDisplayed()
        screenshot("repository-expanded-code")
        header.performTouchInput { swipeUp(durationMillis = 700) }
        compose.waitUntil(5000) { header.fetchSemanticsNode().boundsInRoot.height < 1f }
        compose.onNodeWithText("发布", substring = false).performClick(); waitFor("下载文件")
        compose.onNodeWithText("下载文件", substring = true).performClick(); waitFor("版本说明")
        compose.onNodeWithContentDescription("返回").performClick(); waitFor("发布版本")
        assertTrue(header.fetchSemanticsNode().boundsInRoot.height < 1f)
    }

    @Test fun tablesCodeAndLongContentFitTheViewportAcrossThemes() {
        responsiveDocument = true
        launch(); compose.onNodeWithText("paper-reader").performClick(); waitFor("阅读全文")
        compose.onAllNodesWithText("阅读全文").onFirst().performClick()
        compose.waitUntil(15000) { js("document.querySelector('#wide-table')?.parentElement.dataset.overflow === 'true'") == "true" }
        val fits = """(() => {
            const table = document.querySelector('#wide-table').parentElement;
            const prose = document.querySelector('#prose-table td:last-child .table-cell');
            const small = document.querySelector('#small-table').parentElement;
            const pre = document.querySelector('pre');
            const viewport = document.documentElement.clientWidth;
            return document.documentElement.scrollWidth <= viewport + 1
              && table.clientWidth < table.scrollWidth
              && table.getBoundingClientRect().width <= viewport
              && prose.getBoundingClientRect().width <= parseFloat(getComputedStyle(prose).fontSize) * 22 + 1
              && prose.getBoundingClientRect().height > 80
              && getComputedStyle(document.querySelector('#prose-table th:last-child')).textAlign === 'center'
              && small.dataset.overflow === 'false'
              && getComputedStyle(small.previousElementSibling).display === 'none'
              && getComputedStyle(table.previousElementSibling).display !== 'none'
              && pre.scrollWidth > pre.clientWidth
              && document.querySelector('img').getBoundingClientRect().width <= viewport;
        })()""".trimIndent()
        assertEquals("true", js(fits))
        js("document.querySelector('#wide-table').scrollIntoView();true")
        val viewport = js("window.innerWidth").toFloat()
        js("window.__touches=[];document.addEventListener('touchstart',e=>{window.__touches.push({x:e.touches[0].clientX,y:e.touches[0].clientY,target:e.target.tagName})},{passive:true});true")
        val tableY = js("document.querySelector('#wide-table').parentElement.getBoundingClientRect().top + 20").toFloat()
        swipeWeb(viewport - 30f, tableY, 35f, tableY)
        try { compose.waitUntil(5000) { js("document.querySelector('#wide-table').parentElement.scrollLeft > 0 && window.scrollX === 0") == "true" } }
        catch (e: Throwable) { screenshot("table-gesture-failure"); throw AssertionError("Horizontal gesture: " + js("JSON.stringify({touches:window.__touches,rect:document.querySelector('#wide-table').parentElement.getBoundingClientRect().toJSON(),scroll:document.querySelector('#wide-table').parentElement.scrollLeft,viewport:[innerWidth,innerHeight],windowY:scrollY})"), e) }
        assertEquals("true", js("(() => { const p=document.querySelector('pre'); p.scrollLeft=100; return p.scrollLeft>0 && window.scrollX===0; })()"))
        js("document.querySelector('#wide-table').parentElement.scrollLeft=0;document.querySelector('#wide-table').parentElement.previousElementSibling.scrollIntoView();true")
        screenshot("reader-responsive-light")
        val requests = markupRequests.get()
        assertTrue(requests > 0)
        runBlocking { container.local.updatePreferences { it.copy(themeId = ThemeId.Paper, theme = ThemeMode.Dark, textScale = 1.2f) } }
        compose.waitUntil(15000) { js("getComputedStyle(document.body).backgroundColor").contains("25, 29, 23") && js("document.querySelector('#wide-table')?.parentElement.dataset.overflow === 'true'") == "true" }
        assertEquals("true", js(fits))
        assertEquals(requests, markupRequests.get())
        js("document.querySelector('#wide-table').parentElement.previousElementSibling.scrollIntoView();true")
        screenshot("reader-responsive-dark")
        js("document.querySelector('#prose-table').parentElement.previousElementSibling.scrollIntoView();true")
        screenshot("reader-responsive-prose")
    }

    @Test fun readmeLinksCopyAndOwnerOpenNativeProfile() {
        launch()
        compose.onNodeWithText("paper-reader").performClick()
        waitFor("阅读全文")
        compose.onAllNodesWithText("阅读全文").onFirst().performClick()
        waitForDocument()
        assertEquals("true", js("document.body.textContent.includes('😄') && !!document.querySelector('details') && !!document.querySelector('.markdown-alert') && !!document.querySelector('.pl-k')"))
        js("document.querySelector('a[href*=\"/copy/0\"]').click();true")
        compose.waitUntil(5000) { var copied = false; InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboard = activity!!.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString() == "val x = 1"
        }; copied }
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
        // Let the IME's bring-into-view animation settle before scrolling the lazy list.
        compose.waitForIdle(); android.os.SystemClock.sleep(1000)
        compose.onNodeWithTag("interests-list").performScrollToNode(hasContentDescription("兴趣rust"))
        compose.onNodeWithContentDescription("兴趣rust").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
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
        // Invoke the accessible action while the system IME is animating;
        // this checks pending-input persistence without stale touch coordinates.
        compose.onNodeWithText("保存兴趣").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(10000) { runBlocking { container.local.preferences.first().interests == listOf("python") } }
        compose.onNodeWithText("python", substring = false).assertExists()
        compose.onNodeWithText("调整兴趣").performClick(); waitFor("已选 1 个")
        compose.onNodeWithText("输入或搜索兴趣主题").performTextInput("my-custom-reporove-topic")
        compose.onNodeWithText("保存兴趣").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
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
        waitFor("请求修改")
        compose.onNodeWithText("请求修改").assertIsDisplayed()
        compose.waitUntil(15000) { anyDocumentMatches("document.body?.textContent.includes('Exact review target 99') && Math.abs(document.documentElement.clientHeight-Math.max(48,document.body.getBoundingClientRect().height))<2") }
        assertTrue(requestedPaths.any { it.endsWith("/pulls/3/reviews/99") })
        screenshot("review-target")
    }

    @Test fun loginStarSearchAndNotificationMutations() {
        launch()
        compose.onNodeWithContentDescription("我的").performClick()
        if (container.deviceLogin.configured) compose.onNodeWithText("使用访问令牌登录").performClick()
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
