package app.reporove

import androidx.test.platform.app.InstrumentationRegistry
import app.reporove.core.model.*
import app.reporove.core.network.*
import app.reporove.core.storage.*
import app.reporove.data.GitHubRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import retrofit2.HttpException
import java.io.File

class RepositoryPolicyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    private lateinit var repository: GitHubRepository
    private val privateRepo = """{"id":90,"name":"secret","full_name":"reader/secret","owner":{"login":"reader"},"html_url":"https://github.com/reader/secret","private":true}"""
    @Before fun setup() {
        server = MockWebServer(); server.start()
        Credentials(context).clear()
        repository = GitHubRepository(Credentials(context), LocalStore(context), ResponseCache(File(context.cacheDir, "policy-test"))) { token ->
            createApi(token, server.url("/").toString(), OkHttpClient.Builder().addNetworkInterceptor(GitHubHeaders(token, "localhost")).build())
        }
    }
    @After fun cleanup() { runBlocking { repository.logout() }; server.shutdown() }
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test fun credentialsRoundTripEncryptedAndLogoutRemovesPrivateCollections() = runBlocking {
        server.enqueue(response("""{"id":44,"login":"reader"}"""))
        repository.login("fake-policy-token")
        val blob = context.getSharedPreferences("credentials", 0).getString("account", "")!!
        assertFalse(blob.contains("fake-policy-token")); assertFalse(blob.contains("reader"))
        assertEquals("reader", Credentials(context).read()?.user?.login)
        server.enqueue(response(privateRepo))
        val repo = repository.repository("reader/secret", true).data
        repository.local.toggleLater(repo)
        repository.logout()
        assertNull(Credentials(context).read())
        assertFalse(repository.collections.first().later.any { it.id == repo.id })
    }
    @Test fun serverFailureMayUseCacheButDeniedPermissionNeverDoes() = runBlocking {
        server.enqueue(response(privateRepo))
        repository.repository("reader/secret", true)
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        assertTrue(repository.repository("reader/secret", true).offline)
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        try { repository.repository("reader/secret", true); fail("403 must not fall back to private cache") }
        catch (error: HttpException) { assertEquals(403, error.code()) }
    }
}
