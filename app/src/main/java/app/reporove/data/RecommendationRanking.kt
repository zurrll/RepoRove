package app.reporove.data

import app.reporove.core.model.*
import java.time.Instant
import kotlin.math.ln

object RecommendationRanking {
    const val VERSION = 4
    fun rank(repos: List<Repository>, explicit: List<String>, inferred: List<String>, now: Long, discoveryIds: Set<Long> = emptySet()): List<Recommendation> {
        fun score(repo: Repository): Double {
            val age = runCatching { (now - Instant.parse(repo.pushedAt ?: repo.updatedAt).toEpochMilli()) / 86400000.0 }.getOrDefault(365.0).coerceAtLeast(0.0)
            return repo.topics.count { it in explicit } * 100.0 + repo.topics.count { it in inferred } * 25.0 + 8.0 / (1 + age / 90) + ln(1.0 + repo.stars.coerceAtLeast(0)) * 4.0
        }
        val candidates = repos.distinctBy(Repository::id).sortedByDescending(::score)
        val main = candidates.filterNot { it.id in discoveryIds }
        val exploration = candidates.filter { it.id in discoveryIds }.take(if (main.isEmpty()) 5 else main.size / 4)
        val remaining = (main + exploration).toMutableList()
        val ownerCount = mutableMapOf<String, Int>()
        val result = mutableListOf<Recommendation>()
        while (remaining.isNotEmpty()) {
            // Keep the main feed useful: a smaller discovery candidate can occupy every fifth slot.
            val discoverySlot = result.size % 5 == 4
            val pool = remaining.filter { (it.id in discoveryIds) == discoverySlot }.ifEmpty { remaining }
            val chosen = pool.maxBy { score(it) - (ownerCount[it.owner.login] ?: 0) * 35 }
            remaining.remove(chosen)
            ownerCount[chosen.owner.login] = (ownerCount[chosen.owner.login] ?: 0) + 1
            val reasons = chosen.topics.filter { it in explicit }.map { "因为你选择了 $it" } + chosen.topics.filter { it in inferred && it !in explicit }.map { "与你 Star 的项目相关 · $it" }
            result += Recommendation(chosen, reasons.ifEmpty { listOf(if (chosen.id in discoveryIds) "近期活跃 · 值得探索" else "活跃的热门项目") }, chosen.topics.filter { it in explicit || it in inferred })
        }
        return result
    }
}
