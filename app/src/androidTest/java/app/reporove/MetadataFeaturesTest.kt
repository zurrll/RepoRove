package app.reporove

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import app.reporove.core.model.GraphRequest
import app.reporove.core.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

/** Validates new GraphQL contracts, real organization DTOs and cursor continuation against HTTP fixtures. */
class MetadataFeaturesTest {
    @Test fun organizationPinsDiscussionCursorsAndProjectsUseServerData() = runBlocking {
        val requests = CopyOnWriteArrayList<GraphRequest>()
        val repository = """{"id":22,"name":"tool","full_name":"acme/tool","owner":{"login":"acme","type":"Organization"},"html_url":"https://github.com/acme/tool"}"""
        val node = """{"id":"D_1","number":3,"title":"From GitHub","body":"**Body**","url":"https://github.com/acme/tool/discussions/3","isAnswered":true,"repository":{"nameWithOwner":"acme/tool"},"category":{"name":"Q&A"},"author":{"login":"alice","avatarUrl":"","__typename":"User"},"comments":{"nodes":[{"id":"C_1","body":"Reply","createdAt":"2026-01-01T00:00:00Z","author":null}]}}"""
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    val body = when (path) {
                        "/user" -> """{"id":10,"login":"reader"}"""
                        "/users/acme" -> """{"id":11,"login":"acme","type":"Organization"}"""
                        "/orgs/acme" -> """{"id":11,"login":"acme","name":"Acme Org","public_repos":42}"""
                        "/repos/acme/tool" -> repository
                        "/graphql" -> {
                            val query = AppJson.decodeFromString<GraphRequest>(request.body.readUtf8()); requests += query
                            when {
                                query.query.contains("pinnedItems") -> """{"data":{"organization":{"pinnedItems":{"nodes":[{"nameWithOwner":"acme/tool"}]}}}}"""
                                query.query.contains("totalCount") -> """{"data":{"organization":{"repositories":{"totalCount":45}}}}"""
                                query.query.contains("projectsV2") -> """{"data":{"repository":{"projectsV2":{"nodes":[{"id":"P1","title":"Server project","readme":"Project intro","closed":false}]}}}}"""
                                query.query.contains("search(") -> """{"data":{"search":{"nodes":[$node],"pageInfo":{"hasNextPage":true,"endCursor":"cursor-one"}}}}"""
                                else -> """{"data":{"repository":{"discussion":$node}}}"""
                            }
                        }
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            server.start()
            val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
            val container = AppContainer(context) { token -> createApi(token, server.url("/").toString()) }
            try {
                container.repository.logout()
                container.repository.login("sample-fixture-token")
                val profile = container.repository.profile("acme").data
                assertEquals("Organization", profile.type); assertEquals(42, profile.publicRepos)
                assertEquals(45, container.repository.organizationRepositoryCount("acme").data)
                assertEquals(22L, container.repository.profilePins("acme", true).data.single().id)
                val first = container.repository.searchDiscussions("repo:acme/tool", 1).data
                assertTrue(first.hasMore); assertEquals("alice", first.items.single().author?.login)
                assertEquals("ghost", first.items.single().comments.single().user.login)
                container.repository.searchDiscussions("repo:acme/tool", 2)
                assertTrue(requests.last().variables.toString().contains("cursor-one"))
                assertEquals("From GitHub", container.repository.discussion("acme/tool", 3).data.title)
                assertTrue(container.repository.projects("acme/tool").data.toString().contains("Server project"))
            } finally { container.repository.logout() }
        }
    }
}
