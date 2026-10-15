package app.reporove.data

import app.reporove.core.model.*

/** This is a separate view over immutable source boards. It never filters the complete board. */
object TrendingSelections {
    fun select(
        boards: List<TrendingBoard>, prefs: Preferences, history: TrendingHistory,
        laterNames: Set<String>, now: Long, day: String, dayStart: Long,
        aliases: Map<String, List<String>> = emptyMap(),
    ): TrendingDigest {
        val excluded = (laterNames + prefs.mutedRepositoryNames.filterKeys { it in prefs.mutedRepositories }.values).map(String::lowercase).toSet()
        val recent = (history.opened + history.notified.filter { it.at < dayStart }).filter { now - it.at in 0..TrendingRules.COOLDOWN_MILLIS }.map { it.fullName.lowercase() }.toSet()
        val interests = prefs.interests.filterNot { it in prefs.mutedTopics }
        data class Candidate(val entry: TrendingEntry, val board: TrendingBoard, val matches: List<String>)
        val candidates = boards.flatMap { board -> board.entries.map { entry ->
            val text = listOf(entry.fullName, entry.description, entry.language.orEmpty()).joinToString(" ")
            val matches = interests.filter { interest -> (listOf(interest) + aliases[interest].orEmpty()).any { word ->
                word.isNotBlank() && Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])").containsMatchIn(text)
            } }
            Candidate(entry, board, matches)
        } }.distinctBy { it.entry.fullName.lowercase() }.filter {
            it.entry.fullName.lowercase() !in excluded && it.entry.fullName.lowercase() !in recent && it.entry.repositoryId !in prefs.mutedRepositories
        }.sortedWith(compareByDescending<Candidate> { it.matches.size }.thenBy { it.entry.rank })
        val picks = candidates.take(5).map { candidate ->
            val language = candidate.board.languages.find { it.slug == candidate.board.language }?.name ?: candidate.board.language
            val source = "GitHub ${if (language.isNotEmpty()) "$language " else ""}${candidate.board.period.label}第 ${candidate.entry.rank} 名"
            val reason = if (candidate.matches.isEmpty()) source else "匹配 ${candidate.matches.take(2).joinToString("、")} · $source"
            TrendingPick(candidate.entry, reason, candidate.board.sourceUrl)
        }
        return TrendingDigest(day, now, boards.minOfOrNull(TrendingBoard::fetchedAt) ?: now, picks)
    }
}
