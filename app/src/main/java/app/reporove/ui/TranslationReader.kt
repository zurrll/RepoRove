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

@Composable internal fun BilingualReader(app: AppModel, view: WebView?, blocks: List<TranslationBlock>, enabled: Boolean, loaded: Boolean, documentToken: String, identity: String, privateDocument: Boolean?, context: String) {
    val config by app.container.translations.config.collectAsState()
    val revision by app.container.translations.revision.collectAsState()
    val renderedRevision = remember(view, documentToken) { longArrayOf(-1L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var privateConsent by remember(identity, config?.endpoint) { mutableStateOf(false) }
    var declined by remember(identity, config?.endpoint) { mutableStateOf(false) }
    val bound = remember(identity) { app.container.repository.boundSource() }
    LaunchedEffect(view, enabled, loaded, documentToken, identity, blocks, config, revision, privateDocument, privateConsent) {
        if (view == null || !loaded) return@LaunchedEffect
        suspend fun evaluate(script: String) = view.evaluate(TranslationScripts.inDocument(documentToken, script))
        evaluate(TranslationScripts.mode(enabled))
        if (!enabled || privateDocument == null || (privateDocument && !privateConsent)) return@LaunchedEffect
        val store = app.container.translations
        val snapshot = store.snapshot(); val settings = snapshot.first ?: return@LaunchedEffect; val generation = snapshot.second
        val map = blocks.associateBy { it.id }
        if (renderedRevision[0] != generation) { evaluate(TranslationScripts.reset); renderedRevision[0] = generation }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val session = TranslationSession(blocks,
                cached = { block -> store.cached(store.cacheKey(settings, block, context, bound.scope)) },
                save = { block, text -> store.cache(store.cacheKey(settings, block, context, bound.scope), text, generation, bound.active) },
                translate = { batch -> app.container.translationClient.translateBlocks(settings, batch, context) },
                update = { result ->
                    if (bound.active() && store.generation == generation) {
                        val block = map.getValue(result.id)
                        evaluate(if (result.failure != null) TranslationScripts.error(result.id, result.failure.message ?: "翻译失败。", result.failure.retryable)
                        else TranslationScripts.insert(result.id, if (result.complete) block.render(result.text) else block.preview(result.text).ifBlank { "正在翻译…" }, complete = result.complete))
                    }
                },
                active = { bound.active() && store.generation == generation },
                cacheFailed = { app.message("译文已显示，但本机缓存保存失败。") })
            session.run(
                viewport = { runCatching { AppJson.parseToJsonElement(evaluate(TranslationScripts.visible)).jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList()) },
                retries = { withContext(Dispatchers.Main.immediate) {
                    val pending = view.getTag(R.id.translation_retry)
                    view.setTag(R.id.translation_retry, null)
                    when (pending) { is String -> listOf(pending); is List<*> -> pending.filterIsInstance<String>(); else -> emptyList() }
                } })
        }
    }
    if (enabled && privateDocument == true && !privateConsent && !declined) AlertDialog(
        onDismissRequest = { declined = true }, title = { Text("翻译这份私有文档？") },
        text = { Text("需要翻译的段落和当前仓库 / 文件名称将发送到你设置的翻译服务。GitHub 登录凭据不会发送。") },
        confirmButton = { TextButton(onClick = { privateConsent = true }) { Text("翻译此文档") } },
        dismissButton = { TextButton(onClick = { declined = true }) { Text("暂不翻译") } })
    LaunchedEffect(enabled) { if (!enabled) declined = false }
}
