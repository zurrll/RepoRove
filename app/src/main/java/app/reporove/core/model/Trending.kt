package app.reporove.core.model

import kotlinx.serialization.Serializable

@Serializable enum class TrendingPeriod(val query: String, val label: String, val growthLabel: String) {
    Daily("daily", "日榜", "今日新增"), Weekly("weekly", "周榜", "本周新增"), Monthly("monthly", "月榜", "本月新增")
}

@Serializable data class TrendingLanguage(val slug: String, val name: String)
@Serializable data class TrendingEntry(
    val rank: Int,
    val fullName: String,
    val repositoryId: Long? = null,
    val description: String = "",
    val language: String? = null,
    val languageColor: String? = null,
    val stars: Long? = null,
    val forks: Long? = null,
    val growth: Long? = null,
)

@Serializable data class TrendingBoard(
    val period: TrendingPeriod,
    val language: String,
    val sourceUrl: String,
    val fetchedAt: Long,
    val entries: List<TrendingEntry>,
    val languages: List<TrendingLanguage> = emptyList(),
)

@Serializable data class TrendingSeen(val fullName: String, val at: Long)
@Serializable data class TrendingHistory(val opened: List<TrendingSeen> = emptyList(), val notified: List<TrendingSeen> = emptyList(), val lastNotificationDay: String? = null)
@Serializable data class TrendingPick(val entry: TrendingEntry, val reason: String, val sourceUrl: String)
@Serializable data class TrendingDigest(val day: String, val createdAt: Long, val fetchedAt: Long, val picks: List<TrendingPick>)

object TrendingRules {
    val repositoryName = Regex("[A-Za-z0-9-]{1,39}/[A-Za-z0-9_.-]{1,100}")
    fun validName(name: String) = repositoryName.matches(name) && name.substringAfter('/') !in setOf(".", "..")
    fun validLanguage(slug: String) = slug.isEmpty() || slug.matches(Regex("[a-z0-9][a-z0-9+#._-]{0,100}"))
    const val FRESH_MILLIS = 60 * 60 * 1000L
    const val MAX_NOTIFY_AGE = 24 * 60 * 60 * 1000L
    const val COOLDOWN_MILLIS = 7 * 24 * 60 * 60 * 1000L
}
