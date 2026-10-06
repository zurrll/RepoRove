package app.reporove.data

import java.time.Instant
import java.time.ZoneOffset

data class RecommendationRequest(val query: String, val sort: String, val page: Int, val discovery: Boolean)

/** Search candidates first, then rank them. Dates advance daily; no repository IDs are curated here. */
object RecommendationCandidates {
    private const val SEEDS_PER_PAGE = 3
    fun requests(topics: List<String>, page: Int, now: Long): List<RecommendationRequest> {
        require(page > 0)
        val today = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
        val seeds = topics.distinct().ifEmpty { listOf("") }
        val count = minOf(SEEDS_PER_PAGE, seeds.size)
        return (0 until count).flatMap { offset ->
            val position = (page - 1) * count + offset
            val seed = seeds[position % seeds.size]
            val sourcePage = position / seeds.size + 1
            val base = "is:public archived:false fork:false" + if (seed.isEmpty()) "" else " topic:$seed"
            listOf(
                RecommendationRequest("$base stars:>=${if (seed.isEmpty()) 100 else 50} pushed:>=${today.minusYears(1)}", "stars", sourcePage, false),
                RecommendationRequest("$base stars:${if (seed.isEmpty()) 50 else 10}..499 pushed:>=${today.minusDays(90)}", "updated", sourcePage, true),
            )
        }
    }

    fun hasUnvisitedTopics(topics: List<String>, page: Int): Boolean = page * SEEDS_PER_PAGE < topics.distinct().size
}
