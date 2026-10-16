package app.reporove.core.translation

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/** One actor owns state. Cache reads and two independent network slots never wait for a batch barrier. */
class TranslationSession(
    private val blocks: List<TranslationBlock>,
    private val cached: suspend (TranslationBlock) -> String?,
    private val save: suspend (TranslationBlock, String) -> Unit,
    private val translate: (List<TranslationBlock>) -> Flow<TranslationUpdate>,
    private val update: suspend (TranslationUpdate) -> Unit,
    private val active: () -> Boolean,
    private val cacheFailed: () -> Unit = {},
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private sealed interface Event {
        data class Cache(val values: List<Pair<TranslationBlock, String?>>) : Event
        data class Output(val value: TranslationUpdate) : Event
        data class Finished(val id: Int, val batch: List<TranslationBlock>, val failure: TranslationFailure?) : Event
        data object CacheFailure : Event
    }
    private data class State(var checked: Boolean = false, var running: Boolean = false, var done: Boolean = false, var failed: Boolean = false, var attempts: Int = 0, var retryAt: Long = 0, var single: Boolean = false)
    private fun now() = clock()

    suspend fun run(viewport: suspend () -> List<String>, retries: suspend () -> List<String>) = coroutineScope {
        val map = blocks.associateBy { it.id }; val states = map.mapValues { State() }
        val events = Channel<Event>(64); val jobs = mutableMapOf<Int, Job>()
        val requested = linkedSetOf<String>()
        var viewportIds = emptyList<String>(); var viewportAt = 0L
        var cacheJob: Job? = null; var serial = 0; var authenticationFailed = false; var cacheNotice = false
        try {
            while (isActive && active()) {
                retries().filter { it in map }.forEach { id ->
                    val state = states.getValue(id)
                    if (!state.running && !state.done) { state.failed = false; state.attempts = 0; requested += id }
                }
                if (now() >= viewportAt) { viewportIds = viewport(); viewportAt = now() + 150 }
                val visible = (requested + viewportIds).distinct().filter { it in map }
                if (cacheJob?.isActive != true) {
                    val missing = visible.filter { !states.getValue(it).checked }.take(32).map(map::getValue)
                    if (missing.isNotEmpty()) cacheJob = launch {
                        val values = missing.map { block -> block to cached(block)?.takeIf(block::valid) }
                        events.send(Event.Cache(values))
                    }
                }
                if (!authenticationFailed) {
                    while (jobs.size < 2) {
                        val pending = visible.filter { id -> states.getValue(id).let { it.checked && !it.running && !it.done && !it.failed && it.retryAt <= now() } }.map(map::getValue)
                        if (pending.isEmpty()) break
                        val first = pending.first()
                        val batch = if (states.getValue(first.id).single) listOf(first) else TranslationBatch.pack(pending.takeWhile { !states.getValue(it.id).single })
                        val id = ++serial
                        batch.forEach { states.getValue(it.id).apply { running = true; attempts++ } }
                        jobs[id] = launch {
                            var failure: TranslationFailure? = null
                            val completed = mutableSetOf<String>(); val lastDisplay = mutableMapOf<String, Long>()
                            try {
                                batch.forEach { events.send(Event.Output(TranslationUpdate(it.id, ""))) }
                                translate(batch).collect { value ->
                                    ensureActive(); if (!active()) throw CancellationException()
                                    if (value.id in completed) return@collect
                                    if (value.complete) {
                                        val block = map.getValue(value.id)
                                        if (!block.valid(value.text)) throw TranslationFailure("译文对应关系不完整，请重试。", TranslationFailureKind.FORMAT)
                                        try { save(block, value.text) } catch (error: CancellationException) { throw error } catch (_: Exception) { events.send(Event.CacheFailure) }
                                        completed += value.id
                                    }
                                    if (value.complete || now() - (lastDisplay[value.id] ?: 0) >= 100) {
                                        lastDisplay[value.id] = now(); events.send(Event.Output(value))
                                    }
                                }
                                if (completed.size != batch.size) failure = TranslationFailure("译文没有完整返回，可重试此段。", TranslationFailureKind.FORMAT)
                            } catch (error: CancellationException) { throw error }
                            catch (error: Exception) { failure = error as? TranslationFailure ?: TranslationFailure("翻译暂时失败，可重试此段。") }
                            events.send(Event.Finished(id, batch, failure))
                        }
                    }
                }
                when (val event = withTimeoutOrNull(100) { events.receive() }) {
                    is Event.Cache -> for ((block, text) in event.values) {
                        val state = states.getValue(block.id); state.checked = true
                        if (text != null) { state.done = true; requested -= block.id; update(TranslationUpdate(block.id, text, true)) }
                    }
                    is Event.Output -> {
                        if (event.value.complete) { states.getValue(event.value.id).done = true; requested -= event.value.id }
                        update(event.value)
                    }
                    is Event.CacheFailure -> if (!cacheNotice) { cacheNotice = true; cacheFailed() }
                    is Event.Finished -> {
                        jobs.remove(event.id)
                        val failure = event.failure
                        if (failure?.kind in setOf(TranslationFailureKind.AUTH, TranslationFailureKind.CONFIGURATION)) authenticationFailed = true
                        for (block in event.batch) {
                            val state = states.getValue(block.id); state.running = false
                            if (state.done) continue
                            if (failure?.kind == TranslationFailureKind.FORMAT && event.batch.size > 1) {
                                state.single = true; state.failed = false
                            } else if (failure != null) {
                                val retry = failure.kind in setOf(TranslationFailureKind.TRANSIENT, TranslationFailureKind.RATE_LIMIT) && state.attempts < 2
                                state.failed = !retry
                                state.retryAt = now() + maxOf(failure.retryAfterMillis, 1500)
                                requested -= block.id
                                update(TranslationUpdate(block.id, "", failure = failure))
                            }
                        }
                    }
                    null -> Unit
                }
            }
        } finally {
            cacheJob?.cancel(); jobs.values.forEach(Job::cancel); events.close()
        }
    }
}
