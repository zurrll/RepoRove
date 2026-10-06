package app.reporove

import app.reporove.core.model.*
import app.reporove.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RecommendationTest {
    private val now = Instant.parse("2026-10-06T12:00:00Z").toEpochMilli()
    private fun repo(id: Long, stars: Int, date: String, topics: List<String> = emptyList()) = Repository(id, "r$id", "o$id/r$id", User(login = "o$id"), stars = stars, pushedAt = date, topics = topics, htmlUrl = "https://github.com/o$id/r$id")

    @Test fun coldStartUsesGuardedMainAndExplorationCandidates() {
        val requests = RecommendationCandidates.requests(emptyList(), 1, now)
        assertEquals(2, requests.size)
        assertEquals("stars", requests.first().sort)
        assertTrue(requests.first().query.contains("stars:>=100 pushed:>=2025-10-06"))
        assertTrue(requests.last().query.contains("stars:50..499 pushed:>=2026-07-08"))
        assertTrue(requests.all { it.query.contains("archived:false fork:false") })
    }
    @Test fun rotatingTopicsDoNotSkipTheirFirstSearchPage() {
        val topics = listOf("python", "android", "rust", "kotlin")
        val first = RecommendationCandidates.requests(topics, 1, now)
        val second = RecommendationCandidates.requests(topics, 2, now)
        assertEquals(6, first.size)
        assertTrue(second.first().query.contains("topic:kotlin"))
        assertEquals(1, second.first().page)
        assertEquals(2, second[2].page)
        assertTrue(RecommendationCandidates.hasUnvisitedTopics(topics, 1))
        assertFalse(RecommendationCandidates.hasUnvisitedTopics(topics, 2))
    }
    @Test fun popularityAndMaintenanceBeatTinyRecentlyPushedReposWithinSameInterest() {
        val mature = repo(1, 4000, "2026-07-01T00:00:00Z", listOf("python"))
        val tiny = repo(2, 10, "2026-10-06T00:00:00Z", listOf("python"))
        val result = RecommendationRanking.rank(listOf(tiny, mature), listOf("python"), emptyList(), now)
        assertEquals(1L, result.first().repository.id)
        assertEquals(listOf("因为你选择了 python"), result.first().reasons)
    }
    @Test fun explorationDoesNotTakeOverFeedEvenIfManyMoreCandidatesExist() {
        val main = (1L..20L).map { repo(it, 4000, "2026-08-01T00:00:00Z") }
        val discovery = (100L..199L).map { repo(it, 20, "2026-10-06T00:00:00Z") }
        val ids = discovery.map(Repository::id).toSet()
        val result = RecommendationRanking.rank(main + discovery, emptyList(), emptyList(), now, ids)
        assertEquals(25, result.size)
        assertEquals(5, result.count { it.repository.id in ids })
        assertTrue(result.take(4).none { it.repository.id in ids })
        assertTrue(result[4].repository.id in ids)
    }
    @Test fun interestNormalizationIsIndependentOfCatalogAndOldDownloadsStillDecode() {
        assertEquals("python", InterestTopics.normalize(" Python "))
        assertEquals("my-custom-topic", InterestTopics.normalize("my-custom-topic"))
        assertNull(InterestTopics.normalize("python OR stars:0"))
        val old = app.reporove.core.network.AppJson.decodeFromString<DownloadRecord>("""{"id":1,"name":"x.zip","repository":"a/b","createdAt":10}""")
        assertNull(old.fileName)
    }
}
