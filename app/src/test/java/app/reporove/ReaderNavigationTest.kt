package app.reporove

import app.reporove.core.model.*
import app.reporove.core.reader.MarkdownLinks
import app.reporove.ui.CodeBrowserModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderNavigationTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun sameDocumentLinksKeepDecodedUnicodeAndSlashedRefs() {
        assertEquals("中文-说明", MarkdownLinks.sameDocumentAnchor("https://reader.reporove.invalid/#%E4%B8%AD%E6%96%87-%E8%AF%B4%E6%98%8E", "a/b", "main", "README.md"))
        assertEquals("heading", MarkdownLinks.sameDocumentAnchor("https://github.com/a/b/blob/feature/read/docs/guide.md#heading", "a/b", "feature/read", "docs/guide.md"))
        assertNull(MarkdownLinks.sameDocumentAnchor("https://github.com/a/b/blob/main/docs/guide.md#heading", "a/b", "main", "README.md"))
        assertNull(MarkdownLinks.sameDocumentAnchor("https://github.com/a/b/blob/other/README.md#heading", "a/b", "main", "README.md"))
        assertNull(MarkdownLinks.sameDocumentAnchor("https://github.com.evil.test/a/b/blob/main/README.md#heading", "a/b", "main", "README.md"))
    }
    @Test fun directLinkedFileHasNoInventedDirectoryBackStep() = runTest(dispatcher) {
        val model = CodeBrowserModel()
        model.configure("main", "docs/guide.md") { _, _, _ -> Loaded(emptyList(), 0) }
        advanceUntilIdle()
        assertFalse(model.state.value.canGoBack)
        model.open("src"); advanceUntilIdle()
        model.open("src/File.kt"); advanceUntilIdle()
        model.open("src/File.kt", refresh = true); advanceUntilIdle()
        model.back(); advanceUntilIdle(); assertEquals("src", model.state.value.path)
        model.back(); advanceUntilIdle(); assertEquals("docs/guide.md", model.state.value.path)
        assertFalse(model.state.value.canGoBack)
    }
    @Test fun branchReturnRestoresActualPreviousDirectory() = runTest(dispatcher) {
        val model = CodeBrowserModel()
        model.configure("main", "") { _, _, _ -> Loaded(emptyList(), 0) }
        advanceUntilIdle(); model.open("docs"); advanceUntilIdle()
        model.open("", "feature/read"); advanceUntilIdle()
        model.back(); advanceUntilIdle()
        assertEquals("main", model.state.value.ref); assertEquals("docs", model.state.value.path)
    }
}
