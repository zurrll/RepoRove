package app.reporove.core.model

import android.content.Context
import app.reporove.core.network.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class LanguageEntry(val name: String, val aliases: List<String> = emptyList(), val color: String? = null)
@Serializable data class LanguageCatalog(val source: String, val revision: String, val items: List<LanguageEntry>)
@Serializable data class ExploreEntry(val slug: String, val name: String, val description: String = "", val body: String = "", val aliases: List<String> = emptyList(), val logo: String? = null, val items: List<JsonElement> = emptyList())
@Serializable data class ExploreCatalog(val source: String, val revision: String, val topics: List<ExploreEntry>, val collections: List<ExploreEntry>)

/** Generated from pinned official sources; names/colors are data, never UI enums. */
class Catalogs(context: Context) {
    private val assets = context.applicationContext.assets
    val languages: LanguageCatalog by lazy { assets.open("catalog/languages.json").bufferedReader().use { AppJson.decodeFromString(it.readText()) } }
    val explore: ExploreCatalog by lazy { assets.open("catalog/explore.json").bufferedReader().use { AppJson.decodeFromString(it.readText()) } }
}

data class Recommendation(val repository: Repository, val reasons: List<String>, val relatedTopics: List<String> = emptyList())
