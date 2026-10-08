package app.reporove

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.reporove.core.model.*
import app.reporove.core.network.*
import app.reporove.data.DeviceLogin
import app.reporove.ui.RepoRoveApp
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

/** Exercises the production login UI and encrypted account boundary with local OAuth fixtures. */
class BrowserLoginFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: MockWebServer
    private lateinit var container: AppContainer
    @Volatile private var allowAuthorization = false
    private val identityTokens = CopyOnWriteArrayList<String>()

    @Before fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.path) {
                    "/login/device/code" -> """{"device_code":"fixture-device","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
                    "/login/oauth/access_token" -> if (allowAuthorization) """{"access_token":"gho_fixture-access","token_type":"bearer","refresh_token":"ghr_fixture-refresh","expires_in":28800,"refresh_token_expires_in":15897600}""" else """{"error":"authorization_pending"}"""
                    "/user" -> { identityTokens += request.getHeader("Authorization").orEmpty(); """{"id":44,"login":"reader","name":"Reader"}""" }
                    "/users/reader" -> """{"id":44,"login":"reader","name":"Reader","public_repos":1,"followers":2,"following":3}"""
                    "/users/reader/repos" -> "[]"
                    else -> return MockResponse().setResponseCode(404).setBody("{}")
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        container = AppContainer(application, DeviceLogin("fixture-client", baseUrl = server.url("/"), pause = { delay(200) })) { token ->
            createApi(token, server.url("/").toString(), OkHttpClient.Builder().addNetworkInterceptor(GitHubHeaders(token, "localhost")).build())
        }
        runBlocking {
            container.offline.ready.await()
            container.repository.logout()
            container.local.updatePreferences { Preferences(tabs = listOf(MainTab.Profile), home = MainTab.Profile, clipboardLinks = false) }
        }
    }
    @After fun cleanup() {
        runBlocking { container.repository.logout(); container.local.updatePreferences { Preferences(clipboardLinks = false) } }
        compose.waitForIdle(); server.shutdown()
    }
    private fun waitFor(text: String) { compose.waitUntil(15000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() } }

    @Test fun browserLoginIsPrimaryAndCancellationAllowsFreshAuthorization() {
        compose.setContent { RepoRoveApp(container) }
        waitFor("通过浏览器登录")
        compose.onNodeWithText("GitHub 访问令牌").assertDoesNotExist()
        compose.onNodeWithText("通过浏览器登录").performClick()
        waitFor("ABCD-1234")
        compose.onNodeWithText("复制授权码并打开 GitHub").assertIsDisplayed()
        compose.onNodeWithText("取消登录").performClick()
        waitFor("通过浏览器登录")
        assertNull(container.repository.account.value)
        assertTrue(identityTokens.isEmpty())
        compose.onNodeWithText("通过浏览器登录").performClick()
        waitFor("ABCD-1234")
        allowAuthorization = true
        compose.waitUntil(15000) { container.repository.account.value?.login == "reader" }
        assertEquals(listOf("Bearer gho_fixture-access"), identityTokens.toList())
        val credentials = app.reporove.core.storage.Credentials(InstrumentationRegistry.getInstrumentation().targetContext).read()!!
        assertEquals("ghr_fixture-refresh", credentials.oauth?.refreshToken)
        assertEquals("fixture-client", credentials.oauth?.clientId)
    }

    @Test fun tokenFallbackCanBeExpandedWithoutStartingBrowserAuthorization() {
        compose.setContent { RepoRoveApp(container) }
        waitFor("使用访问令牌登录")
        compose.onNodeWithText("使用访问令牌登录").performClick()
        compose.onNodeWithText("GitHub 访问令牌").assertExists()
        compose.onNodeWithText("通过浏览器登录").assertExists()
        assertEquals(0, server.requestCount)
    }
}
