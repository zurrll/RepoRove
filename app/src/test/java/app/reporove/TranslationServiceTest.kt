package app.reporove

import app.reporove.core.storage.SecretSettings
import app.reporove.core.network.AppJson
import app.reporove.core.translation.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class TranslationServiceTest {
    @get:Rule val folder = TemporaryFolder()
    private class MemorySecrets : SecretSettings { var data: String? = null; override fun read() = data; override fun save(value: String) { data = value } }
    private fun response(request: Request, text: String, code: Int = 200) = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test").body(text.toResponseBody("application/json".toMediaType())).build()
    private val config = TranslationConfig("https://translate.example/v1/chat/completions", "model", "test-secret")
    @Test fun successfulTranslationIsCachedOfflineAndConfigurationChangesInvalidateIt() = runTest {
        val requests = AtomicInteger(); val secrets = MemorySecrets()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet(); assertEquals("Bearer test-secret", chain.request().header("Authorization"))
            response(chain.request(), """{"choices":[{"finish_reason":"stop","message":{"content":"[{\"id\":0,\"text\":\"译文\"}]"}}]}""")
        }.build()
        val service = TranslationService(secrets, folder.root, client); service.configure(config)
        assertEquals("译文", service.text("Original", "a", false)); assertEquals("译文", service.text("Original", "a", false)); assertEquals(1, requests.get())
        service.configure(config.copy(model = "other")); service.text("Original", "a", false); assertEquals(2, requests.get())
    }
    @Test fun privateTextBlockedBeforeRequestAndPrivateCachesSeparatedAndCleared() = runTest {
        val requests = AtomicInteger(); val secrets = MemorySecrets()
        val client = OkHttpClient.Builder().addInterceptor { chain -> requests.incrementAndGet(); response(chain.request(), """{"choices":[{"message":{"content":"[{\"id\":0,\"text\":\"私有译文\"}]"}}]}""") }.build()
        val service = TranslationService(secrets, folder.root, client); service.configure(config)
        assertTrue(runCatching { service.text("Private", "alice", true) }.isFailure); assertEquals(0, requests.get())
        service.configure(config.copy(allowPrivate = true)); service.text("Private", "alice", true); service.text("Private", "bob", true); assertEquals(2, requests.get())
        service.clearPrivate(); service.text("Private", "alice", true); assertEquals(3, requests.get())
    }
    @Test fun failureTruncationAndInvalidIdAreNotCachedAsSuccess() = runTest {
        for (body in listOf("""{"choices":[{"finish_reason":"length","message":{"content":"[]"}}]}""", """{"choices":[{"message":{"content":"[{\"id\":99,\"text\":\"bad\"}]"}}]}""")) {
            val service = TranslationService(MemorySecrets(), folder.newFolder(), OkHttpClient.Builder().addInterceptor { response(it.request(), body) }.build()); service.configure(config)
            assertTrue(runCatching { service.text("Original", "a", false) }.isFailure)
        }
    }
    @Test fun sessionChangePreventsLatePrivateResponseAndCacheWrite() = runTest {
        var active = true
        val service = TranslationService(MemorySecrets(), folder.root, OkHttpClient.Builder().addInterceptor { chain -> active = false; response(chain.request(), """{"choices":[{"message":{"content":"[{\"id\":0,\"text\":\"late\"}]"}}]}""") }.build())
        service.configure(config.copy(allowPrivate = true))
        assertTrue(runCatching { service.text("Private", "alice", true, { active }) }.isFailure)
        assertEquals(0, folder.root.walkTopDown().count { it.isFile })
    }
}
