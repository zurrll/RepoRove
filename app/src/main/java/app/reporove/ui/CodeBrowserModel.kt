package app.reporove.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.reporove.core.model.*
import app.reporove.core.network.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrowserState(val ref: String = "", val path: String = "", val contents: List<Content> = emptyList(), val loading: Boolean = true, val error: String? = null, val offline: Boolean = false, val directories: Map<String, List<Content>> = emptyMap(), val expanded: Set<String> = setOf(""), val recent: List<String> = emptyList())
class CodeBrowserModel : ViewModel() {
    private val mutable = MutableStateFlow(BrowserState()); val state = mutable.asStateFlow()
    private var loader: (suspend (String, String, Boolean) -> Loaded<List<Content>>)? = null
    private var job: Job? = null; private var generation = 0L
    val positions = mutableMapOf<String, Pair<Int, Int>>()
    fun configure(ref: String, path: String, fetch: suspend (String, String, Boolean) -> Loaded<List<Content>>) {
        if (loader != null) return
        loader = fetch; mutable.value = BrowserState(ref = ref, path = path); open(path)
    }
    fun open(path: String, ref: String = mutable.value.ref, refresh: Boolean = false) {
        val fetch = loader ?: return; job?.cancel(); val current = ++generation
        val changingRef = ref != mutable.value.ref
        mutable.value = mutable.value.copy(ref = ref, path = path, contents = emptyList(), loading = true, error = null, recent = if (changingRef) emptyList() else mutable.value.recent, directories = if (changingRef) emptyMap() else mutable.value.directories, expanded = if (changingRef) setOf("") else mutable.value.expanded)
        job = viewModelScope.launch {
            try {
                val result = fetch(ref, path, refresh)
                if (current != generation) return@launch
                val file = result.data.singleOrNull()?.takeIf { it.path == path && it.type != "dir" }
                var directory = if (file != null) path.substringBeforeLast('/', "") else path
                val parents = mutableSetOf(""); while (directory.isNotEmpty()) { parents += directory; directory = directory.substringBeforeLast('/', "") }
                mutable.value = mutable.value.copy(contents = result.data, loading = false, offline = result.offline, directories = if (file == null) mutable.value.directories + (path to result.data) else mutable.value.directories, expanded = mutable.value.expanded + parents, recent = if (file != null) (listOf(path) + mutable.value.recent).distinct().take(20) else mutable.value.recent)
                if (file != null) loadDirectory(path.substringBeforeLast('/', ""))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (current == generation) mutable.value = mutable.value.copy(loading = false, error = userMessage(e)) }
        }
    }
    fun expand(path: String) {
        mutable.value = mutable.value.copy(expanded = if (path in mutable.value.expanded) mutable.value.expanded - path else mutable.value.expanded + path)
        if (path in mutable.value.expanded) loadDirectory(path)
    }
    fun loadDirectory(path: String, force: Boolean = false) {
        if (!force && path in mutable.value.directories) return
        val fetch = loader ?: return; val ref = mutable.value.ref
        viewModelScope.launch {
            try {
                val items = fetch(ref, path, force).data
                if (ref == mutable.value.ref && items.none { it.path == path && it.type != "dir" }) mutable.value = mutable.value.copy(directories = mutable.value.directories + (path to items))
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* The tree offers an explicit retry; current document remains usable. */ }
        }
    }
}
