package app.reporove

import app.reporove.core.model.*
import app.reporove.core.network.AppJson
import app.reporove.data.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TrendingTest {
    private fun row(name: String, growth: String = "1,234 stars today", language: String = "Python") = """
        <article class="Box-row"><h2><a href="/$name" data-hydro-click='{"payload":{"record_id":71}}'>$name</a></h2>
        <p>A useful project</p><span itemprop="programmingLanguage">$language</span><span class="repo-language-color" style="background-color: #3572A5"></span>
        <a href="/$name/stargazers">12,345</a><a href="/$name/forks"> 240 </a><span class="float-sm-right">$growth</span></article>
    """.trimIndent()
    private fun page(vararg rows: String) = "<h1>Trending</h1><a href='/trending/python?since=daily'>Python</a><a href='/trending/c%2B%2B?since=daily'>C++</a>" + rows.joinToString("")

    @Test fun parsesEveryRowInSourceOrderAndKeepsPeriodCountsAndLanguageOptions() {
        val board = TrendingParser.parse(page(row("acme/first"), row("acme/second"), row("acme/third")), TrendingPeriod.Daily, "", 100)
        assertEquals(listOf("acme/first", "acme/second", "acme/third"), board.entries.map { it.fullName })
        assertEquals(listOf(1, 2, 3), board.entries.map { it.rank })
        assertEquals(1234L, board.entries[0].growth); assertEquals(12345L, board.entries[0].stars)
        assertEquals(71L, board.entries[0].repositoryId); assertEquals("#3572A5", board.entries[0].languageColor)
        assertEquals(listOf("python", "c++"), board.languages.map { it.slug })
        assertTrue(board.sourceUrl.endsWith("since=daily"))
    }

    @Test fun rejectsPartialDuplicateForeignAndWrongPeriodBoards() {
        for (html in listOf(page(row("acme/first"), "<article class='Box-row'><p>broken</p></article>"), page(row("acme/first"), row("acme/first")), page(row("acme/first").replace("href=\"/acme/first\"", "href=\"https://evil.test/acme/first\"")), "<h1>Sign in</h1>")) {
            assertThrows(TrendingSourceException::class.java) { TrendingParser.parse(html, TrendingPeriod.Daily, "", 100) }
        }
        assertThrows(TrendingSourceException::class.java) { TrendingParser.parse(page(row("acme/first")), TrendingPeriod.Weekly, "", 100) }
    }

    @Test fun genuineEmptyBoardAndMissingCountsNeverInventGrowth() {
        val empty = TrendingParser.parse("<h1>Trending</h1><div class='blankslate'>We don't have any trending repositories.</div>", TrendingPeriod.Daily, "python", 100)
        assertTrue(empty.entries.isEmpty())
        val missing = TrendingParser.parse(page(row("acme/tool", "")), TrendingPeriod.Daily, "", 100)
        assertNull(missing.entries.single().growth)
        assertThrows(TrendingSourceException::class.java) { TrendingParser.parse("<h1>Trending</h1>", TrendingPeriod.Daily, "", 100) }
    }

    @Test fun picksRespectInterestsExclusionsAndCooldownWithoutChangingCompleteBoard() {
        val entries = (1..9).map { TrendingEntry(it, "acme/tool$it", it.toLong(), language = if (it == 7) "Python" else "Rust", growth = 100L) }
        val board = TrendingBoard(TrendingPeriod.Daily, "", "https://github.com/trending", 20000, entries)
        val history = TrendingHistory(opened = listOf(TrendingSeen("acme/tool1", 19000)), notified = listOf(TrendingSeen("acme/tool2", 1000)))
        val prefs = Preferences(interests = listOf("python"), mutedRepositories = listOf(3))
        val digest = TrendingSelections.select(listOf(board), prefs, history, setOf("acme/tool4"), 20000, "2026-10-15", 10000)
        assertEquals("acme/tool7", digest.picks.first().entry.fullName)
        assertEquals(5, digest.picks.size)
        assertTrue(digest.picks.none { it.entry.fullName in setOf("acme/tool1", "acme/tool2", "acme/tool3", "acme/tool4") })
        assertEquals((1..9).toList(), board.entries.map { it.rank })
        assertEquals(9, board.entries.size)
    }

    @Test fun wordMatchingDoesNotMistakeEmailForAiAndSavedNotificationsDoNotHideTodayPreview() {
        val board = TrendingBoard(TrendingPeriod.Daily, "", "https://github.com/trending", 10000, listOf(TrendingEntry(1, "acme/email", description = "email client"), TrendingEntry(2, "acme/model", description = "AI model")))
        val history = TrendingHistory(notified = listOf(TrendingSeen("acme/model", 9999)))
        val result = TrendingSelections.select(listOf(board), Preferences(interests = listOf("ai")), history, emptySet(), 10000, "2026-10-15", 0)
        assertEquals("acme/model", result.picks.first().entry.fullName)
        assertTrue(result.picks.first().reason.contains("匹配 ai"))
        assertFalse(result.picks.last().reason.contains("匹配"))
    }

    @Test fun languagePicksRetainTheirOwnSourceScope() {
        val global = TrendingBoard(TrendingPeriod.Daily, "", "https://github.com/trending", 1000, listOf(TrendingEntry(1, "acme/rust", language = "Rust")))
        val python = TrendingBoard(TrendingPeriod.Daily, "python", "https://github.com/trending/python", 1000, listOf(TrendingEntry(1, "acme/python", language = "Python")), listOf(TrendingLanguage("python", "Python")))
        val digest = TrendingSelections.select(listOf(global, python), Preferences(interests = listOf("python")), TrendingHistory(), emptySet(), 1000, "2026-10-15", 0)
        assertEquals("https://github.com/trending/python", digest.picks.first().sourceUrl)
        assertTrue(digest.picks.first().reason.contains("Python 日榜"))
        assertFalse(digest.picks.last().reason.contains("Python"))
    }

    @Test fun publicCacheSeparatesRangesAndLanguagesAndFallsBackWithoutCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(page(row("acme/daily"))))
            server.enqueue(MockResponse().setBody(page(row("acme/weekly", "4,200 stars this week"))))
            server.enqueue(MockResponse().setResponseCode(503))
            server.start()
            val folder = Files.createTempDirectory("trending-test").toFile()
            try {
                val repository = TrendingRepository(folder, baseUrl = server.url("/"), now = { 1000 })
                val daily = repository.board(TrendingPeriod.Daily)
                assertEquals(daily, repository.board(TrendingPeriod.Daily))
                val weekly = repository.board(TrendingPeriod.Weekly, "c++")
                assertEquals("acme/weekly", weekly.data.entries.single().fullName)
                assertTrue(repository.board(TrendingPeriod.Daily, refresh = true).offline)
                val first = server.takeRequest(); assertNull(first.getHeader("Authorization")); assertEquals("en", first.getHeader("Accept-Language"))
                assertEquals("c++", server.takeRequest().requestUrl!!.pathSegments.last())
                assertEquals(3, server.requestCount)
            } finally { folder.deleteRecursively() }
        }
    }

    @Test fun cancelledRefreshCannotFallBackOrReplaceAValidSnapshot() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(page(row("acme/original"))))
            server.enqueue(MockResponse().setHeadersDelay(1, TimeUnit.SECONDS).setBody(page(row("acme/new"))))
            server.start()
            val folder = Files.createTempDirectory("trending-cancel").toFile()
            try {
                val repository = TrendingRepository(folder, baseUrl = server.url("/"), now = { 1000 })
                repository.board(TrendingPeriod.Daily)
                try { withTimeout(100) { repository.board(TrendingPeriod.Daily, refresh = true) }; fail("Refresh should be cancelled") } catch (_: TimeoutCancellationException) { }
                assertEquals("acme/original", repository.board(TrendingPeriod.Daily).data.entries.single().fullName)
            } finally { folder.deleteRecursively() }
        }
    }

    @Test fun notificationClaimIsOncePerDayAndFailedOrDisallowedPostsRollBack() = runBlocking {
        val folder = Files.createTempDirectory("trending-notify").toFile()
        try {
            val repository = TrendingRepository(folder)
            val digest = TrendingDigest("2026-10-15", 1000, 1000, listOf(TrendingPick(TrendingEntry(1, "acme/tool"), "reason", "https://github.com/trending")))
            val posted = AtomicInteger()
            assertFalse(repository.notifyOnce(digest, { false }) { posted.incrementAndGet(); true })
            assertFalse(repository.notifyOnce(digest, { true }) { throw IllegalStateException("notification unavailable") })
            assertNull(repository.history().lastNotificationDay)
            coroutineScope { (1..8).map { async { repository.notifyOnce(digest, { true }) { posted.incrementAndGet(); true } } }.awaitAll() }
            assertEquals(1, posted.get()); assertEquals(digest, repository.savedDigest(digest.day))
            assertEquals("2026-10-15", repository.history().lastNotificationDay)
            assertTrue(repository.notifyOnce(digest.copy(day = "2026-10-16"), { true }) { posted.incrementAndGet(); true })
            assertEquals(2, posted.get())
            assertNull(repository.savedDigest("../../outside"))
        } finally { folder.deleteRecursively() }
    }

    @Test fun existingPreferencesKeepNavigationAndDefaultNotificationsOff() {
        val old = AppJson.decodeFromString<Preferences>("""{"tabs":["Discover","Library"],"home":"Library"}""").normalized()
        assertEquals(MainTab.Library, old.home); assertFalse(old.trendingNotifications)
        assertEquals(TrendingPeriod.Daily, old.trendingPeriod)
        val invalid = Preferences(trendingLanguage = "../outside", tabs = listOf(MainTab.Trending), home = MainTab.Trending).normalized()
        assertEquals("", invalid.trendingLanguage); assertEquals(MainTab.Trending, invalid.home)
    }
}
