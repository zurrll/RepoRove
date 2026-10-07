package app.reporove

import app.reporove.core.offline.SafeArchive
import app.reporove.core.storage.readBounded
import app.reporove.core.translation.*
import app.reporove.ui.clipboardGitHubLink
import app.reporove.ui.codePages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.jsoup.Jsoup
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfflineReadingTest {
    @get:Rule val folder = TemporaryFolder()
    private fun archive(vararg entries: Pair<String, String>): File = folder.newFile().also { file -> ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } } }
    @Test fun zipIndexReconstructsDirectoriesAndReadsExactFiles() = runTest {
        val zip = archive("repo-sha/README.md" to "# Readme", "repo-sha/src/nested/a.kt" to "val a = 1")
        val entries = SafeArchive.index(zip)
        assertTrue(entries.any { it.path == "src" && it.directory }); assertTrue(entries.any { it.path == "src/nested" && it.directory })
        assertEquals("val a = 1", String(SafeArchive.bytes(zip, entries.first { it.path == "src/nested/a.kt" })))
        assertEquals(4, entries.size)
    }
    @Test fun zipRejectsTraversalAbsoluteBackslashAndMultipleRoots() = runTest {
        for (name in listOf("repo/../secret", "/absolute/a", "repo/a\\b", "repo/C:/x")) assertTrue(runCatching { SafeArchive.index(archive(name to "x")) }.isFailure)
        assertTrue(runCatching { SafeArchive.index(archive("one/a" to "1", "two/b" to "2")) }.isFailure)
        assertFalse(SafeArchive.validPath("a//b")); assertFalse(SafeArchive.validPath("."))
    }
    @Test fun boundedPreviewRefusesOversizedEntryWithoutExtraction() = runTest {
        val zip = archive("repo/large.txt" to "x".repeat(1025))
        val index = SafeArchive.index(zip)
        assertTrue(runCatching { SafeArchive.bytes(zip, index.first(), 1024) }.isFailure)
        assertEquals(1025, "x".repeat(2000).byteInputStream().readBounded(1024).size)
        assertFalse(File(folder.root, "large.txt").exists())
    }
    @Test fun syntheticDirectoryIndexIsBoundedWhenArchiveOmitsDirectories() = runTest {
        // A small ZIP can describe far more directories than its entry count.
        val entries = (1..60).map { "repo/branch$it/" + "d/".repeat(900) + "file.txt" to "x" }
        val error = runCatching { SafeArchive.index(archive(*entries.toTypedArray())) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("目录索引"))
    }
    @Test fun translationPreservesDomLinksInlineCodeTableAndUnsafeModelTextIsEscaped() {
        val doc = TranslationDocument("<h2 id='intro'>Intro</h2><p><a href='/a'>Link</a> and <code>foo()</code></p><table><tr><td align='right'>Value</td></tr></table><pre><code>val x = 1</code></pre>")
        assertFalse(doc.parts.any { it.text.contains("foo()") || it.text.contains("val x") })
        val rendered = doc.render(doc.parts.associate { it.id to "<script>bad()</script>译文" })
        val html = Jsoup.parseBodyFragment(rendered)
        assertEquals("intro", html.selectFirst("h2")!!.id()); assertEquals("/a", html.selectFirst("a")!!.attr("href")); assertEquals("right", html.selectFirst("td")!!.attr("align"))
        assertEquals("foo()", html.selectFirst("p code")!!.text()); assertEquals("val x = 1", html.selectFirst("pre code")!!.text()); assertTrue(html.select("script").isEmpty())
    }
    @Test fun translationRejectsMissingDuplicateIdsAndKeepsLongParagraphExact() {
        val expected = listOf(TranslationPart(0, "a"), TranslationPart(1, "b"))
        assertTrue(runCatching { TranslationDocument.validate(expected, listOf(TranslationPart(0,"x"))) }.isFailure)
        assertTrue(runCatching { TranslationDocument.validate(expected, listOf(TranslationPart(0,"x"),TranslationPart(0,"y"))) }.isFailure)
        val long = "A long paragraph. ".repeat(400)
        assertEquals(long, TranslationDocument.split(long).joinToString("")); assertTrue(TranslationDocument.split(long).all { it.length <= 1800 })
    }
    @Test fun clipboardAcceptsSupportedObjectsAndRejectsDeceptiveHostsAndCredentials() {
        assertEquals("https://github.com/owner/repo/issues/3", clipboardGitHubLink("分享 https://github.com/owner/repo/issues/3"))
        assertNull(clipboardGitHubLink("https://github.com.evil.test/owner/repo")); assertNull(clipboardGitHubLink("https://evil.test/github.com/owner/repo"))
        assertNull(clipboardGitHubLink("https://secret@github.com/owner/repo")); assertNull(clipboardGitHubLink("https://github.com/settings"))
    }
    @Test fun codePagingPreservesEveryCharacterAndSourceLineNumbers() {
        val text = (1..2300).joinToString("\n") { "val line$it = $it" }
        val pages = codePages(text)
        assertEquals(text, pages.joinToString("") { it.text }); assertEquals(listOf(1,1001,2001), pages.map { it.firstLine }); assertEquals(2300, pages.last().lastLine)
    }
}
