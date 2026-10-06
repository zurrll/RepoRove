package app.reporove

import app.reporove.core.model.*
import app.reporove.core.network.AppJson
import app.reporove.core.reader.*
import app.reporove.data.RecommendationRanking
import org.junit.Assert.*
import org.junit.Test

class ReadingAndSearchTest {
    private val palette = ReaderPalette("#111111", "#555555", "#ffffff", "#dddddd", "#f6f8fa", "#0969da", "#1a7f37", "#cf222e", "#8250df", "#9a6700", false)
    @Test fun readerKeepsStructureAndRejectsActiveContent() {
        val html = """<h1>标题</h1><h2>标题</h2><a href="#标题">锚点</a><a href="javascript:alert(1)" onclick="alert(2)">危险</a><script>alert(3)</script><iframe src="https://x.test"></iframe><details><summary>展开</summary>正文</details><input type="checkbox" checked><div class="highlight highlight-source-kotlin"><pre><span class="pl-k">val</span> x = 1</pre></div><table><tr><td>内容</td></tr></table>"""
        val result = ReaderDocument.prepare(html, palette, 1f, "a/repo", "feature/read", "docs/README.md")
        assertFalse(result.html.contains("<script")); assertFalse(result.html.contains("javascript:")); assertFalse(result.html.contains("onclick")); assertFalse(result.html.contains("<iframe"))
        assertTrue(result.html.contains("<details>")); assertTrue(result.html.contains("user-content-标题-1")); assertTrue(result.html.contains("table-scroll")); assertTrue(result.html.contains("/copy/0"))
        assertEquals(listOf("val x = 1"), result.codeBlocks)
    }
    @Test fun relativeLinksRespectDirectoryRepositoryRootAndSlashedRef() {
        assertEquals("#heading", MarkdownLinks.resolve("#heading", "a/b", "main", "README.md", false))
        assertEquals("https://github.com/a/b/blob/feature/read/docs/guide.md", MarkdownLinks.resolve("guide.md", "a/b", "feature/read", "docs/README.md", false))
        assertEquals("https://raw.githubusercontent.com/a/b/main/assets/pic.png", MarkdownLinks.resolve("/assets/pic.png", "a/b", "main", "docs/README.md", true))
        assertEquals("https://github.com/a/b/blob/main/LICENSE", MarkdownLinks.resolve("../LICENSE", "a/b", "main", "docs/README.md", false))
    }
    @Test fun queryBuilderQuotesLanguagesAndReplacesSelectedConflicts() {
        val spec = SearchSpec(SearchKind.Repositories, "cache language:Java archived:true foo:bar", "repo:a/b", mapOf("language" to "C++", "archived" to "false", "label" to "good first issue"))
        assertEquals("cache foo:bar language:C++ archived:false label:\"good first issue\" repo:a/b", spec.query())
        assertEquals("language:\"Visual Basic .NET\"", SearchSpec(SearchKind.Code, filters = mapOf("language" to "Visual Basic .NET")).query())
        assertEquals("is:pr is:open", SearchSpec(SearchKind.Pulls, "is:issue is:closed", filters = mapOf("is" to "open")).query())
        assertEquals("is:public archived:false is:pr is:open", SearchSpec(SearchKind.Pulls, "is:issue is:closed is:public archived:false", filters = mapOf("is" to "open")).query())
        assertEquals("cache repo:a/b", SearchSpec(SearchKind.Repositories, "cache repo:x/y org:other", scope = "repo:a/b").query())
    }
    @Test fun profileAndFileRoutingPreserveIdentityAndSearchRevision() {
        assertEquals(GitHubTarget.Profile("alice", "stars"), GitHubLinks.parse("https://github.com/alice?tab=stars"))
        assertNull(GitHubLinks.parse("https://github.com.evil.test/alice"))
        assertNull(GitHubLinks.parse("javascript:alert(1)"))
        assertEquals("feature/read" to "docs/README.md", GitHubLinks.fileParts("feature/read/docs/README.md", listOf("feature", "feature/read"), "main"))
        assertEquals("aabbccd123" to "src/main.kt", GitHubLinks.fileParts("aabbccd123/src/main.kt", listOf("main"), "main"))
    }
    @Test fun oldPreferencesRetainInterestsAndAppearanceWithoutAddingDefaults() {
        val old = AppJson.decodeFromString<Preferences>("""{"schemaVersion":1,"theme":"Dark","interests":["android"]}""").normalized()
        assertEquals(ThemeMode.Dark, old.theme); assertEquals(ThemeId.Clear, old.themeId); assertEquals(listOf("android"), old.interests)
        assertTrue(Preferences().interests.isEmpty()); assertFalse(Preferences().inferStarInterests)
    }
    @Test fun relevanceWinsOverPopularityAndReasonIdentifiesSeedSource() {
        fun repo(id: Long, topic: String, stars: Int) = Repository(id, "repo$id", "owner/repo$id", User(login = "owner$id"), stars = stars, topics = listOf(topic), htmlUrl = "https://github.com/owner/repo$id")
        val small = repo(1, "android", 2); val popular = repo(2, "rust", 100000)
        val ranked = RecommendationRanking.rank(listOf(popular, small), listOf("android"), listOf("rust"), 0)
        assertEquals(1L, ranked.first().repository.id)
        assertEquals(listOf("android"), ranked.first().relatedTopics)
        assertEquals(listOf("因为你选择了 android"), ranked.first().reasons)
        assertEquals(listOf("与你 Star 的项目相关 · rust"), ranked.last().reasons)
    }
}
