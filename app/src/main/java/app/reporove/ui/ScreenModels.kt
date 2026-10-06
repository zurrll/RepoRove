package app.reporove.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.reporove.AppContainer
import app.reporove.core.model.*
import app.reporove.core.network.userMessage
import app.reporove.data.DeviceCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ListState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val offline: Boolean = false,
    val incomplete: Boolean = false,
    val error: String? = null,
)

/** Every query owns one cancellable request; old results cannot replace a new query. */
class PagedModel<T>(private val itemKey: (T) -> Any? = { it }) : ViewModel() {
    private val mutable = MutableStateFlow(ListState<T>())
    val state = mutable.asStateFlow()
    private var loader: (suspend (Int, Boolean) -> Loaded<Page<T>>)? = null
    private var identity: String? = null
    private var job: Job? = null
    private var page = 0
    private var generation = 0L

    fun prepare(key: String): Boolean {
        if (identity == key) return false
        job?.cancel()
        generation++
        identity = key
        loader = null
        mutable.value = ListState()
        return true
    }

    fun configure(key: String, load: suspend (Int, Boolean) -> Loaded<Page<T>>) {
        if (identity == key && loader != null) return
        identity = key
        loader = load
        mutable.value = ListState()
        start(reset = true, force = false)
    }

    fun refresh() = start(reset = true, force = true)
    fun more() { if (mutable.value.hasMore && !mutable.value.loading) start(reset = false, force = false) }
    fun remove(predicate: (T) -> Boolean) { mutable.value = mutable.value.copy(items = mutable.value.items.filterNot(predicate)) }

    private fun start(reset: Boolean, force: Boolean) {
        val fetch = loader ?: return
        job?.cancel()
        val requestGeneration = ++generation
        val requestIdentity = identity
        val next = if (reset) 1 else page + 1
        mutable.value = mutable.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val result = fetch(next, force)
                if (identity != requestIdentity || generation != requestGeneration) return@launch
                page = next
                val items = if (reset) result.data.items else mutable.value.items + result.data.items
                mutable.value = ListState(items.distinctBy(itemKey), hasMore = result.data.hasMore, offline = result.offline, incomplete = result.data.incomplete)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (identity == requestIdentity && generation == requestGeneration) mutable.value = mutable.value.copy(loading = false, error = userMessage(e))
            }
        }
    }
}

class ResourceModel<T> : ViewModel() {
    private val mutable = MutableStateFlow<LoadState<T>>(LoadState.Loading)
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var loader: (suspend (Boolean) -> Loaded<T>)? = null
    private var identity: String? = null
    private var generation = 0L

    fun configure(key: String, load: suspend (Boolean) -> Loaded<T>) {
        if (identity == key) return
        identity = key
        loader = load
        refresh(false)
    }

    fun refresh(force: Boolean = true) {
        val fetch = loader ?: return
        job?.cancel()
        val requestGeneration = ++generation
        mutable.value = LoadState.Loading
        job = viewModelScope.launch {
            try {
                val result = fetch(force)
                if (generation == requestGeneration) mutable.value = LoadState.Ready(result.data, result.offline)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (generation == requestGeneration) mutable.value = LoadState.Failed(userMessage(e))
            }
        }
    }
}

data class AppMessage(val text: String, val undo: (suspend () -> Unit)? = null)

class AppModel(val container: AppContainer) : ViewModel() {
    private val messages = Channel<AppMessage>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private val mutableBusy = MutableStateFlow<Set<String>>(emptySet())
    val busy = mutableBusy.asStateFlow()

    fun action(key: String, success: String? = null, block: suspend () -> Unit) {
        if (key in mutableBusy.value) return
        mutableBusy.value += key
        viewModelScope.launch {
            try { block(); success?.let { messages.send(AppMessage(it)) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { messages.send(AppMessage(userMessage(e))) }
            finally { mutableBusy.value -= key }
        }
    }

    fun message(text: String, undo: (suspend () -> Unit)? = null) { viewModelScope.launch { messages.send(AppMessage(text, undo)) } }
    fun updatePreferences(update: (Preferences) -> Preferences) {
        viewModelScope.launch {
            try { container.local.updatePreferences(update) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { messages.send(AppMessage(userMessage(e))) }
        }
    }
}

class AccountModel(private val container: AppContainer) : ViewModel() {
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableCode = MutableStateFlow<DeviceCode?>(null)
    val code = mutableCode.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    fun tokenLogin(token: String) = launch { container.repository.login(token) }
    fun deviceLogin(privateRepositories: Boolean) = launch {
        val code = container.deviceLogin.start(privateRepositories)
        mutableCode.value = code
        container.repository.login(container.deviceLogin.awaitToken(code))
    }
    fun cancel() { generation++; job?.cancel(); mutableCode.value = null; mutableBusy.value = false }
    private fun launch(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = null
        val requestGeneration = ++generation
        job = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == requestGeneration) mutableError.value = userMessage(e) }
            finally { if (generation == requestGeneration) { mutableBusy.value = false; mutableCode.value = null } }
        }
    }
}
