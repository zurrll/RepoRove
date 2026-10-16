package app.reporove.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.reporove.core.translation.TranslationConfig

@Composable fun TranslationSettings(app: AppModel, nav: AppNavigation) {
    val saved by app.container.translations.config.collectAsState()
    var engine by remember { mutableStateOf(saved?.engine ?: "qwen") }
    var model by remember { mutableStateOf(saved?.model ?: "qwen-mt-flash") }
    var endpoint by remember { mutableStateOf(saved?.endpoint ?: TranslationConfig().endpoint) }
    var key by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf(false) }
    val busy by app.busy.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("段落双语", style = MaterialTheme.typography.titleLarge) }
        item { Text("自带 API Key，费用由翻译服务收取。只在你开启双语后发送项目介绍或文档段落；代码不翻译。", style = MaterialTheme.typography.bodyMedium) }
        item { ChoiceRow(listOf("qwen", "compatible"), engine, { if (it == "qwen") "千问翻译" else "兼容服务" }, {
            if (engine != it) { engine = it; key = ""; model = if (it == "qwen") "qwen-mt-flash" else ""; endpoint = if (it == "qwen") TranslationConfig().endpoint else "" }
        }) }
        item { OutlinedTextField(key, { key = it; error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (saved != null && engine == saved?.engine && endpoint == saved?.endpoint) "API Key（留空保留）" else "API Key") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)) }
        if (engine == "qwen") {
            item { Text("默认使用 qwen-mt-flash，翻译为简体中文。北京区接口；其他地域或业务空间请调整地址。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "收起模型与接口" else "模型与接口") } }
        }
        if (advanced || engine == "compatible") {
            item { OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("模型名称") }) }
            item { OutlinedTextField(endpoint, { endpoint = it; key = "" }, Modifier.fillMaxWidth(), label = { Text("完整 HTTPS 请求地址") }, supportingText = { Text("填写服务的 chat/completions 地址。更改地址需重新填写 Key。") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false)) }
        }
        item { Text("发送到：${runCatching { java.net.URI(endpoint).host }.getOrNull() ?: "请填写接口地址"}", style = MaterialTheme.typography.bodyMedium) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { Button(enabled = "translation-save" !in busy, onClick = {
            val retained = saved?.takeIf { it.engine == engine && it.endpoint == endpoint }?.key.orEmpty()
            val value = runCatching { TranslationConfig(engine, endpoint, model, key.ifBlank { retained }).validated() }
            if (value.isFailure) error = value.exceptionOrNull()?.message
            else app.action("translation-save", "翻译配置已保存") { app.container.translations.save(value.getOrThrow()); key = ""; nav.back() }
        }) { Text("保存") } }
        if (saved != null) {
            item { TextButton(onClick = { app.action("translation-cache", "译文缓存已清除") { app.container.translations.clearCache() } }) { Text("清除译文缓存") } }
            item { TextButton(onClick = { removing = true }) { Text("删除翻译配置") } }
        }
        item { Text("Key 加密保存在本机，不上传到 RepoRove。已完成的译文可复用；双语按阅读位置加载，不等于全文已保存。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text("删除翻译配置和缓存？") }, confirmButton = { TextButton(onClick = { removing = false; app.action("translation-remove", "翻译配置已删除") { app.container.translations.remove(); key = "" } }) { Text("删除") } }, dismissButton = { TextButton(onClick = { removing = false }) { Text("取消") } })
}
