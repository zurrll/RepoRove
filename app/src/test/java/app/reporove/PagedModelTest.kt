package app.reporove

import app.reporove.core.model.*
import app.reporove.ui.PagedModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PagedModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun staleResultsCannotReplaceNewSearch() = runTest(dispatcher) {
        val model = PagedModel<String>()
        model.configure("old") { _, _ -> withContext(NonCancellable) { delay(1000); Loaded(Page(listOf("old"), false), 0) } }
        runCurrent()
        model.prepare("new")
        model.configure("new") { _, _ -> Loaded(Page(listOf("new"), false), 0) }
        advanceUntilIdle()
        assertEquals(listOf("new"), model.state.value.items)
    }
    @Test fun paginationDeduplicatesAndRetryDoesNotSkipFailedPage() = runTest(dispatcher) {
        val calls = mutableListOf<Int>()
        var fail = true
        val model = PagedModel<Int>()
        model.configure("query") { page, _ ->
            calls += page
            if (page == 2 && fail) { fail = false; throw IOException() }
            Loaded(Page(if (page == 1) listOf(1, 2) else listOf(2, 3), page == 1), 0)
        }
        advanceUntilIdle(); model.more(); advanceUntilIdle()
        assertEquals(listOf(1, 2), model.state.value.items)
        assertNotNull(model.state.value.error)
        model.more(); advanceUntilIdle()
        assertEquals(listOf(1, 2, 3), model.state.value.items)
        assertEquals(listOf(1, 2, 2), calls)
    }
    @Test fun failedRefreshKeepsReadablePreviousContent() = runTest(dispatcher) {
        val model = PagedModel<Int>()
        model.configure("query") { _, force -> if (force) throw IOException() else Loaded(Page(listOf(7), false), 0) }
        advanceUntilIdle(); model.refresh(); advanceUntilIdle()
        assertEquals(listOf(7), model.state.value.items)
        assertFalse(model.state.value.loading)
        assertNotNull(model.state.value.error)
    }
}
