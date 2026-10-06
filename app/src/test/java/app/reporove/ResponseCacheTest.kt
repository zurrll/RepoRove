package app.reporove

import app.reporove.core.storage.ResponseCache
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResponseCacheTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun accountIsolationLogoutAndStaleRequestWrites() = runTest {
        val cache = ResponseCache(folder.root)
        cache.write("alice", "repo", String.serializer(), "private")
        assertNull(cache.read("bob", "repo", String.serializer()))
        assertEquals("private", cache.read("alice", "repo", String.serializer())?.data)
        cache.clear()
        cache.write("alice", "repo", String.serializer(), "old", stillActive = { false })
        assertNull(cache.read("alice", "repo", String.serializer()))
    }
    @Test fun corruptCacheDoesNotPreventNetworkRecovery() = runTest {
        val cache = ResponseCache(folder.root)
        cache.write("public", "repo", String.serializer(), "good")
        folder.root.walkTopDown().first { it.isFile }.writeText("{broken")
        assertNull(cache.read("public", "repo", String.serializer()))
        cache.write("public", "repo", String.serializer(), "recovered")
        assertEquals("recovered", cache.read("public", "repo", String.serializer())?.data)
    }
}
