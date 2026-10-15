package app.reporove.data

import app.reporove.core.model.*
import app.reporove.core.network.AppJson
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

class TrendingSourceException(message: String) : IllegalArgumentException(message)

/** Public HTML is data only. A broken row fails the board instead of silently shortening it. */
object TrendingParser {
    fun parse(html: String, period: TrendingPeriod, language: String, fetchedAt: Long): TrendingBoard {
        require(TrendingRules.validLanguage(language))
        val document = Jsoup.parse(html, "https://github.com/")
        if (document.select("h1").none { it.text().trim() == "Trending" }) throw TrendingSourceException("GitHub 热榜暂时无法识别，请稍后刷新。")
        val rows = document.select("article.Box-row")
        if (rows.isEmpty() && document.select(".blankslate").none { it.text().contains("trending repositories", true) }) {
            throw TrendingSourceException("GitHub 热榜返回内容不完整，请稍后刷新。")
        }
        val names = mutableSetOf<String>()
        val entries = rows.mapIndexed { index, row ->
            val anchor = row.selectFirst("h2 a[href]") ?: throw TrendingSourceException("GitHub 热榜条目不完整，请稍后刷新。")
            val url = anchor.absUrl("href").toHttpUrlOrNull()
            val name = url?.pathSegments?.joinToString("/").orEmpty()
            if (url?.host != "github.com" || url.scheme != "https" || !TrendingRules.validName(name) || !names.add(name.lowercase())) {
                throw TrendingSourceException("GitHub 热榜条目地址无效，请稍后刷新。")
            }
            val periodText = row.selectFirst("span.float-sm-right")?.text().orEmpty()
            val expected = when (period) { TrendingPeriod.Daily -> "today"; TrendingPeriod.Weekly -> "this week"; TrendingPeriod.Monthly -> "this month" }
            if (periodText.isNotBlank() && !periodText.endsWith(expected)) throw TrendingSourceException("GitHub 返回的热榜时间范围不一致，请刷新。")
            val id = runCatching { AppJson.parseToJsonElement(anchor.attr("data-hydro-click")).jsonObject["payload"]?.jsonObject?.get("record_id")?.jsonPrimitive?.longOrNull }.getOrNull()?.takeIf { it > 0 }
            TrendingEntry(
                rank = index + 1, fullName = name, repositoryId = id,
                description = row.selectFirst("p")?.text().orEmpty(),
                language = row.selectFirst("[itemprop=programmingLanguage]")?.text()?.takeIf(String::isNotBlank),
                languageColor = Regex("#[0-9a-fA-F]{6}\\b").find(row.selectFirst(".repo-language-color")?.attr("style").orEmpty())?.value,
                stars = count(row.selectFirst("a[href=/$name/stargazers]")?.text()),
                forks = count(row.selectFirst("a[href=/$name/forks]")?.text()),
                growth = count(Regex("^([0-9][0-9,\\s]*)\\s+stars?\\s+").find(periodText)?.groupValues?.get(1)),
            )
        }
        val languages = document.select("a[href^=/trending/]").mapNotNull { anchor ->
            val url = anchor.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            if (url.host != "github.com" || url.pathSegments.size != 2 || url.queryParameter("since") == null) return@mapNotNull null
            val slug = url.pathSegments[1]
            val name = anchor.text().trim()
            if (!TrendingRules.validLanguage(slug) || name.isBlank()) null else TrendingLanguage(slug, name)
        }.distinctBy(TrendingLanguage::slug)
        val source = "https://github.com/".toHttpUrlOrNull()!!.newBuilder().addPathSegment("trending").apply { if (language.isNotEmpty()) addPathSegment(language) }.addQueryParameter("since", period.query).build()
        return TrendingBoard(period, language, source.toString(), fetchedAt, entries, languages)
    }

    private fun count(text: String?): Long? = text?.replace(Regex("[,\\s]"), "")?.toLongOrNull()?.takeIf { it >= 0 }
}
