package app.reporove.data

import app.reporove.core.model.*
import app.reporove.core.storage.LocalStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

class TrendingDigests(private val trending: TrendingRepository, private val local: LocalStore, private val catalogs: Catalogs) {
    suspend fun preview(refresh: Boolean = false): Loaded<TrendingDigest> = withContext(Dispatchers.IO) {
        val prefs = local.preferences.first()
        val main = trending.board(TrendingPeriod.Daily, refresh = refresh)
        val aliases = prefs.interests.associateWith { interest ->
            val topic = catalogs.explore.topics.find { it.slug == interest }
            val language = catalogs.languages.items.find { it.name.equals(interest, true) || it.aliases.any { name -> name.equals(interest, true) } }
            (topic?.aliases.orEmpty() + listOfNotNull(topic?.name, language?.name) + language?.aliases.orEmpty()).distinct()
        }
        val boards = mutableListOf(main.data)
        val languages = main.data.languages.filter { language -> prefs.interests.any { interest -> language.slug == interest || language.name.equals(interest, true) || aliases[interest].orEmpty().any { it.equals(language.name, true) } } }.take(2)
        for (language in languages) {
            try {
                val board = trending.board(TrendingPeriod.Daily, language.slug, refresh)
                if (!board.offline) boards += board.data
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* The complete global board is still a valid candidate source. */ }
        }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val digest = TrendingSelections.select(boards, prefs, trending.history(), local.collections.first().later.map { it.fullName.lowercase() }.toSet(), now, date.toString(), date.atStartOfDay(zone).toInstant().toEpochMilli(), aliases)
        Loaded(digest, main.cachedAt, main.offline)
    }
}
