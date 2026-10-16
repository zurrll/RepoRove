package app.reporove.ui

import app.reporove.R
import android.webkit.WebView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.reporove.core.network.AppJson
import app.reporove.core.translation.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal suspend fun WebView.evaluate(script: String): String = withContext(Dispatchers.Main.immediate) {
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resumeWith(Result.success(result)) }
    }
}

/** Two requests at most; visible blocks are reprioritized before each small batch. */
@Composable internal fun BilingualReader(app: AppModel, view: WebView?, blocks: List<TranslationBlock>, enabled: Boolean, loaded: Boolean, identity: String, privateDocument: Boolean?, context: String) {
    val config by app.container.translations.config.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var privateConsent by remember(identity, config?.endpoint) { mutableStateOf(false) }
    var declined by remember(identity, config?.endpoint) { mutableStateOf(false) }
    // Requests and completed UI updates are invalidated on document/config/session changes.
    val bound = remember(identity) { app.container.repository.boundSource() }
    LaunchedEffect(view, enabled, loaded, identity, config, privateDocument, privateConsent) {
        if (view == null || !loaded) return@LaunchedEffect
        view.evaluate(TranslationScripts.mode(enabled))
        if (!enabled || config == null || privateDocument == null || (privateDocument && !privateConsent)) return@LaunchedEffect
        val settings = config!!; val store = app.container.translations; val generation = store.generation
        val completed = mutableSetOf<String>()
        var paused = false
        val map = blocks.associateBy { it.id }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive && bound.active() && store.generation == generation) {
                val requested = withContext(Dispatchers.Main.immediate) { (view.getTag(R.id.translation_retry) as? String).also { view.setTag(R.id.translation_retry, null) } }
                if (requested != null) { completed.remove(requested); paused = false }
                if (!paused) {
                    val ids = runCatching { AppJson.parseToJsonElement(view.evaluate(TranslationScripts.visible)).jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList())
                    val batch = ids.filterNot { it in completed }.mapNotNull(map::get).take(2)
                    coroutineScope {
                        batch.map { block -> launch {
                            completed += block.id
                            val active = { bound.active() && store.generation == generation }
                            try {
                                val key = store.cacheKey(settings, block, context, bound.scope)
                                var output = store.cached(key)?.takeIf(block::valid)
                                if (output == null) {
                                    view.evaluate(TranslationScripts.insert(block.id, "正在翻译…"))
                                    var latest = ""
                                    app.container.translationClient.translate(settings, block.text, context).collect { chunk ->
                                        ensureActive(); if (!active()) throw CancellationException()
                                        latest = chunk
                                        // Partial text is escaped; placeholders never become model-supplied HTML.
                                        var partial = org.jsoup.nodes.TextNode(chunk.replace(Regex("__RR_\\d*$"), "")).outerHtml()
                                        block.protected.forEachIndexed { index, html -> partial = partial.replace(TranslationBlock.token(index), html) }
                                        view.evaluate(TranslationScripts.insert(block.id, partial))
                                    }
                                    require(block.valid(latest)) { "译文没有保留代码或链接，请重试。" }
                                    output = latest
                                    runCatching { store.cache(key, latest, generation, bound.active) }
                                }
                                ensureActive(); if (active()) { view.evaluate(TranslationScripts.insert(block.id, block.render(output))) }
                            } catch (error: CancellationException) { completed.remove(block.id); throw error }
                            catch (error: Exception) {
                                if (error is TranslationFailure && error.pause) paused = true
                                if (active()) view.evaluate(TranslationScripts.error(block.id, if (error is TranslationFailure || error is IllegalArgumentException) error.message ?: "翻译失败。" else "翻译失败，请稍后重试。"))
                            }
                        } }.joinAll()
                    }
                }
                delay(400)
            }
        }
    }
    if (enabled && privateDocument == true && !privateConsent && !declined) AlertDialog(
        onDismissRequest = { declined = true }, title = { Text("翻译这份私有文档？") },
        text = { Text("需要翻译的段落和当前仓库 / 文件名称将发送到你设置的翻译服务。GitHub 登录凭据不会发送。") },
        confirmButton = { TextButton(onClick = { privateConsent = true }) { Text("翻译此文档") } },
        dismissButton = { TextButton(onClick = { declined = true }) { Text("暂不翻译") } })
    LaunchedEffect(enabled) { if (!enabled) declined = false }
}
