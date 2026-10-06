package app.reporove

import app.reporove.core.model.*
import app.reporove.core.network.AppJson
import app.reporove.data.GitHubRepository
import app.reporove.ui.MarkdownLinks
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ContentTest {
    @Test fun sparsePublicEventsAndMergedPullsDecodeCorrectly() {
        val event = AppJson.decodeFromString<Event>("""{"id":"1","type":"PullRequestEvent","actor":{"login":"a"},"repo":{"name":"a/b"},"created_at":"2026-01-01T00:00:00Z","payload":{"pull_request":{"number":3}}}""")
        assertEquals(3, event.payload.pullRequest?.number)
        val issue = AppJson.decodeFromString<Issue>("""{"id":2,"number":3,"title":"Fix","state":"closed","user":{"login":"a"},"html_url":"https://github.com/a/b/pull/3","merged_at":"2026-01-01T00:00:00Z"}""")
        assertEquals("已合并", issue.status)
    }
    @Test fun unicodePathsAndBranchNamesEncodeWithoutLosingSeparators() {
        assertEquals("docs/a%20b%23.md", GitHubRepository.encodePath("docs/a b#.md"))
        assertEquals("feature/new", GitHubRepository.encodePath("feature/new"))
        assertEquals("a" to "b.git", GitHubRepository.parts("a/b.git"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectTraversal() { GitHubRepository.encodePath("docs/../secret") }
    @Test fun base64LineBreaksPreserveSourceText() {
        val original = "第一行\n  indent\n"
        val encoded = Base64.getMimeEncoder(8, byteArrayOf(10)).encodeToString(original.toByteArray())
        assertEquals(original, GitHubRepository.text(Content("a", "a", "file", original.length.toLong(), encoded, "base64")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectBinaryPreview() { GitHubRepository.text(Content("a", "a", "file", 1, "AA==", "base64")) }
    @Test fun markdownResourcesResolveRelativeToActualReadme() {
        assertEquals("https://raw.githubusercontent.com/a/b/main/docs/logo.png", MarkdownLinks.resolve("logo.png", "a/b", "main", "docs/README.md", true))
        assertEquals("https://github.com/a/b/blob/main/LICENSE", MarkdownLinks.resolve("../LICENSE", "a/b", "main", "docs/README.md", false))
        assertEquals("https://example.com/a", MarkdownLinks.resolve("https://example.com/a", "a/b", "main", "README.md", false))
    }
}
