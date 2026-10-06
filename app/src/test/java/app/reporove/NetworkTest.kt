package app.reporove

import app.reporove.core.network.GitHubHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NetworkTest {
    @Test fun contentMediaTypeIsPreserved() {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody("image"))
            val client = OkHttpClient.Builder().addNetworkInterceptor(GitHubHeaders("sample-token", "localhost")).build()
            client.newCall(Request.Builder().url(server.url("/image")).header("Accept", "application/vnd.github.raw+json").build()).execute().close()
            val request = server.takeRequest()
            assertEquals("application/vnd.github.raw+json", request.getHeader("Accept"))
            assertEquals("Bearer sample-token", request.getHeader("Authorization"))
        }
    }

    @Test fun tokenIsNeverForwardedToForeignRedirectHost() {
        MockWebServer().use { source -> MockWebServer().use { destination ->
            source.start(); destination.start()
            val target = destination.url("/asset").newBuilder().host("127.0.0.1").build()
            source.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target))
            destination.enqueue(MockResponse().setBody("ok"))
            val client = OkHttpClient.Builder().addNetworkInterceptor(GitHubHeaders("test-secret", "localhost")).build()
            client.newCall(Request.Builder().url(source.url("/repo")).header("Authorization", "wrong-token").build()).execute().use { assertTrue(it.isSuccessful) }
            assertEquals("Bearer test-secret", source.takeRequest().getHeader("Authorization"))
            assertNull(destination.takeRequest().getHeader("Authorization"))
        } }
    }
}
