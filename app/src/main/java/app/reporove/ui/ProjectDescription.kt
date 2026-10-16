package app.reporove.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.reporove.core.model.Repository
import app.reporove.core.translation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

@Composable fun ProjectDescription(app: AppModel, repo: Repository) {
    val text = repo.description?.takeIf(String::isNotBlank) ?: return
    val control = rememberBilingualToggle("description:${repo.fullName}")
    val config by app.container.translations.config.collectAsState()
    val revision by app.container.translations.revision.collectAsState()
    var consent by remember(repo.id, config?.endpoint) { mutableStateOf(false) }
    var declined by remember(repo.id, config?.endpoint) { mutableStateOf(false) }
    var translation by remember(repo.id, text, config) { mutableStateOf("") }
    var error by remember(repo.id, config) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(control.enabled) { if (!control.enabled) declined = false }
    LaunchedEffect(control.enabled, consent, text, config, revision, retry) {
        if (!control.enabled || config == null || (repo.isPrivate && !consent)) return@LaunchedEffect
        val bound = app.container.repository.boundSource()
        val store = app.container.translations; val snapshot = store.snapshot(); val settings = snapshot.first ?: return@LaunchedEffect; val generation = snapshot.second
        val block = TranslationBlock("description", text, emptyList())
        val identity = "${repo.fullName}/description"
        error = null
        var latest = ""
        try {
            val key = store.cacheKey(settings, block, identity, bound.scope)
            val cached = store.cached(key)?.takeIf(block::valid)
            if (cached != null) translation = cached
            else {
                app.container.translationClient.translate(settings, text, identity).collect { chunk -> coroutineContext.ensureActive(); if (bound.active() && generation == store.generation) { latest = chunk; translation = chunk } }
                require(block.valid(latest)) { "译文不完整，请重试。" }
                runCatching { store.cache(key, latest, generation, bound.active) }
            }
        } catch (error: CancellationException) { throw error }
        catch (failure: Exception) { error = if (failure is TranslationFailure) failure.message else "翻译失败，请重试。" }
    }
    Column {
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            BilingualButton(control)
        }
        if (control.enabled && (!repo.isPrivate || consent)) {
            if (error != null) TextButton(onClick = { retry++ }) { Text("${error} 重试") }
            else Text(translation.ifEmpty { "正在翻译…" }, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (control.enabled && repo.isPrivate && !consent && !declined) AlertDialog(onDismissRequest = { declined = true }, title = { Text("翻译私有项目介绍？") }, text = { Text("项目名称和介绍将发送到你设置的翻译服务。") }, confirmButton = { TextButton(onClick = { consent = true }) { Text("翻译") } }, dismissButton = { TextButton(onClick = { declined = true }) { Text("取消") } })
}
