package app.reporove

import androidx.test.platform.app.InstrumentationRegistry
import app.reporove.core.model.*
import app.reporove.core.network.*
import app.reporove.core.storage.*
import app.reporove.data.GitHubRepository
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class OAuthAccountTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val credentials = Credentials(context)
    private val metadata = OAuthMetadata("test-client", "ghr_old-refresh", 150_000, 900_000)

    @Test fun concurrentReadsRefreshOnceAndKeepCacheScopeAcrossRestart() = runBlocking {
        MockWebServer().use { server ->
            val requests = CopyOnWriteArrayList<String>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request.getHeader("Authorization").orEmpty()
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(if (request.path == "/user") """{"id":44,"login":"reader"}""" else """{"Kotlin":100}""")
                }
            }
            server.start(); credentials.clear()
            val clock = AtomicLong(0)
            val rotations = AtomicInteger()
            val cache = ResponseCache(File(context.cacheDir, "oauth-account-test").apply { deleteRecursively() })
            val refresh: suspend (OAuthGrant) -> OAuthGrant = { previous ->
                assertEquals("ghr_old-refresh", previous.metadata.refreshToken)
                rotations.incrementAndGet(); delay(100)
                OAuthGrant("gho_new-access", OAuthMetadata("test-client", "ghr_new-refresh", clock.get() + 28_800_000, 900_000_000))
            }
            val factory: (String?) -> GitHubApi = { token -> createApi(token, server.url("/").toString(), OkHttpClient.Builder().addNetworkInterceptor(GitHubHeaders(token, "localhost")).build()) }
            val repository = GitHubRepository(credentials, LocalStore(context), cache, refresh, clock::get, factory)
            try {
                repository.login("gho_old-access", metadata)
                val scope = repository.scope
                val bound = repository.boundSource()
                repository.languages("reader/public", true)
                clock.set(160_000); requests.clear()
                coroutineScope { (1..4).map { async { repository.languages("reader/public", true) } }.awaitAll() }
                assertEquals(1, rotations.get())
                assertTrue(requests.all { it == "Bearer gho_new-access" })
                assertEquals(scope, repository.scope)
                assertTrue(bound.active())
                val saved = credentials.read()!!
                assertEquals("ghr_new-refresh", saved.oauth?.refreshToken)
                assertEquals(scope, saved.cacheScope)
                val blob = context.getSharedPreferences("credentials", 0).getString("account", "")!!
                assertFalse(blob.contains("gho_new-access")); assertFalse(blob.contains("ghr_new-refresh"))
                val reloaded = GitHubRepository(credentials, LocalStore(context), cache, refresh, clock::get, factory)
                assertEquals(scope, reloaded.scope)
                assertEquals(100L, reloaded.languages("reader/public").data["Kotlin"])
                assertEquals(1, rotations.get())
            } finally { repository.logout() }
            assertNull(credentials.read())
        }
    }

    @Test fun revokedGrantDoesNotMasqueradeAsSuccessfulCachedRefresh() = runBlocking {
        MockWebServer().use { server ->
            server.start(); credentials.clear()
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"id":44,"login":"reader"}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Kotlin":100}"""))
            val clock = AtomicLong(0)
            val repository = GitHubRepository(credentials, LocalStore(context), ResponseCache(File(context.cacheDir, "oauth-revoked-test").apply { deleteRecursively() }),
                refreshTokens = { throw IllegalArgumentException("登录已撤销，请重新登录。") }, now = clock::get,
                apiFactory = { token -> createApi(token, server.url("/").toString()) })
            try {
                repository.login("gho_old-access", metadata)
                repository.languages("reader/public", true)
                clock.set(160_000)
                val failure = runCatching { repository.languages("reader/public", true) }.exceptionOrNull()
                assertTrue(failure?.message.orEmpty().contains("重新登录"))
                assertEquals("gho_old-access", credentials.read()?.token)
                assertEquals(2, server.requestCount)
            } finally { repository.logout() }
        }
    }

    @Test fun identityLookupFailureKeepsRotatedPairAndRetriesValidationWithoutRefreshingAgain() = runBlocking {
        MockWebServer().use { server ->
            server.start(); credentials.clear()
            val clock = AtomicLong(0)
            val rotations = AtomicInteger()
            val repository = GitHubRepository(credentials, LocalStore(context), ResponseCache(File(context.cacheDir, "oauth-validation-test").apply { deleteRecursively() }),
                refreshTokens = { rotations.incrementAndGet(); OAuthGrant("gho_rotated-access", OAuthMetadata("test-client", "ghr_rotated-refresh", 28_800_000, 900_000_000)) },
                now = clock::get, apiFactory = { token -> createApi(token, server.url("/").toString()) })
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"id":44,"login":"reader"}"""))
            try {
                repository.login("gho_old-access", metadata)
                clock.set(160_000)
                server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
                assertTrue(runCatching { repository.languages("reader/public", true) }.isFailure)
                assertEquals("ghr_rotated-refresh", credentials.read()?.oauth?.refreshToken)
                assertTrue(credentials.read()!!.needsIdentityValidation)
                server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"id":44,"login":"renamed-reader"}"""))
                server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Kotlin":100}"""))
                assertEquals(100L, repository.languages("reader/public", true).data["Kotlin"])
                assertFalse(credentials.read()!!.needsIdentityValidation)
                assertEquals("renamed-reader", repository.account.value?.login)
                assertEquals(1, rotations.get())
            } finally { repository.logout() }
        }
    }

    @Test fun cancelledLoginCannotPersistLateIdentityResponse() = runBlocking {
        MockWebServer().use { server ->
            server.start(); credentials.clear()
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"id":44,"login":"reader"}""").setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS))
            val repository = GitHubRepository(credentials, LocalStore(context), ResponseCache(File(context.cacheDir, "oauth-cancel-test")), apiFactory = { token -> createApi(token, server.url("/").toString()) })
            val job = launch { repository.login("gho_late-access", metadata) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)) }
            withTimeout(1500) { job.cancelAndJoin() }
            assertNull(credentials.read())
            assertNull(repository.account.value)
            assertEquals("public", repository.scope)
        }
    }
}
