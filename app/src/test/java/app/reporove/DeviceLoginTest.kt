package app.reporove

import app.reporove.data.DeviceCode
import app.reporove.data.DeviceLogin
import app.reporove.core.model.OAuthGrant
import app.reporove.core.model.OAuthMetadata
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class DeviceLoginTest {
    private val code = DeviceCode("device-test", "ABCD-1234", "https://github.com/login/device", 900)
    private val codeJson = """{"device_code":"device-test","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test fun permissionChoiceUsesOfficialEndpointsWithoutClientSecret() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val login = DeviceLogin("public-client-id", baseUrl = server.url("/"))
            for (private in listOf(false, true)) {
                server.enqueue(response(codeJson))
                assertEquals(code, login.start(private))
                val request = server.takeRequest()
                assertEquals("/login/device/code", request.path)
                val body = java.net.URLDecoder.decode(request.body.readUtf8(), "UTF-8")
                assertTrue(body.contains("client_id=public-client-id"))
                assertTrue(body.contains(if (private) "read:org repo " else "public_repo"))
                assertFalse(body.contains("client_secret"))
                assertNull(request.getHeader("Authorization"))
            }
        }
    }

    @Test fun pendingAndSlowDownRespectServerIntervalThenReturnExpiringPair() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            var elapsed = 0L
            val waits = mutableListOf<Long>()
            val login = DeviceLogin("client", baseUrl = server.url("/"), monotonicMillis = { elapsed }, now = { 1000 }, pause = { waits += it; elapsed += it })
            server.enqueue(response("""{"error":"authorization_pending"}"""))
            server.enqueue(response("""{"error":"slow_down","interval":20}"""))
            server.enqueue(response("""{"access_token":"gho_test-access","token_type":"bearer","expires_in":28800,"refresh_token":"ghr_test-refresh","refresh_token_expires_in":15897600}"""))
            val grant = login.awaitToken(code)
            assertEquals(listOf(5000L, 5000L, 20000L), waits)
            assertEquals("gho_test-access", grant.accessToken)
            assertEquals(28_801_000L, grant.metadata.expiresAt)
            assertEquals("ghr_test-refresh", grant.metadata.refreshToken)
            repeat(3) {
                val request = server.takeRequest()
                assertEquals("/login/oauth/access_token", request.path)
                assertTrue(request.body.readUtf8().contains("device_code=device-test"))
            }
        }
    }

    @Test fun localExpirationDoesNotPollAfterDeadline() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(response("""{"error":"authorization_pending"}"""))
            var elapsed = 0L
            val login = DeviceLogin("client", baseUrl = server.url("/"), monotonicMillis = { elapsed }, pause = { elapsed += it })
            val error = runCatching { login.awaitToken(code.copy(expiresIn = 10)) }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("过期"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun rejectedAndDisabledAuthorizationHaveUsefulErrors() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val login = DeviceLogin("client", baseUrl = server.url("/"), pause = {})
            for ((error, text) in listOf("access_denied" to "取消", "expired_token" to "过期", "incorrect_client_credentials" to "配置无效")) {
                server.enqueue(response("""{"error":"$error"}"""))
                assertTrue(runCatching { login.awaitToken(code) }.exceptionOrNull()?.message.orEmpty().contains(text))
            }
            server.enqueue(response("""{"error":"device_flow_disabled"}"""))
            assertTrue(runCatching { login.start(false) }.exceptionOrNull()?.message.orEmpty().contains("尚未开启"))
        }
    }

    @Test fun foreignVerificationPageAndMalformedTokenAreRejected() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val login = DeviceLogin("client", baseUrl = server.url("/"), pause = {})
            for (url in listOf("https://github.com.evil.example/login/device", "https://github.com/login/device?next=evil", "http://github.com/login/device")) {
                server.enqueue(response(codeJson.replace("https://github.com/login/device", url)))
                assertTrue(runCatching { login.start(false) }.exceptionOrNull()?.message.orEmpty().contains("地址无效"))
            }
            server.enqueue(response("""{"access_token":"gho_test-token","token_type":"bearer","expires_in":28800}"""))
            assertTrue(runCatching { login.awaitToken(code) }.exceptionOrNull()?.message.orEmpty().contains("续期凭据不完整"))
        }
    }

    @Test fun refreshRotatesPairAndNeverSendsAnAppSecret() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val login = DeviceLogin("client", baseUrl = server.url("/"), now = { 2000 })
            server.enqueue(response("""{"access_token":"gho_rotated-access","token_type":"bearer","expires_in":28800,"refresh_token":"ghr_rotated-refresh","refresh_token_expires_in":15897600}"""))
            val grant = login.refresh(OAuthGrant("gho_old-access", OAuthMetadata("client", "ghr_old-refresh", 0, 10000)))
            assertEquals("ghr_rotated-refresh", grant.metadata.refreshToken)
            assertEquals(28_802_000L, grant.metadata.expiresAt)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("grant_type=refresh_token"))
            assertTrue(body.contains("refresh_token=ghr_old-refresh"))
            assertFalse(body.contains("client_secret"))
            assertFalse(body.contains("gho_old-access"))
            server.enqueue(response("""{"error":"bad_refresh_token"}"""))
            assertTrue(runCatching { login.refresh(grant) }.exceptionOrNull()?.message.orEmpty().contains("重新登录"))
        }
    }

    @Test fun cancellationStopsAnInFlightAuthorizationRequest() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(response(codeJson).setBodyDelay(3, TimeUnit.SECONDS))
            val login = DeviceLogin("client", baseUrl = server.url("/"))
            val job = launch { login.start(false); fail("Cancelled request must not deliver a code") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            withTimeout(1500) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }

    @Test fun unavailableServiceDoesNotExposeItsResponse() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setResponseCode(503).setBody("private-internal-response"))
            val failure = runCatching { DeviceLogin("client", baseUrl = server.url("/")).start(false) }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertFalse(failure?.message.orEmpty().contains("private-internal-response"))
        }
    }
}
