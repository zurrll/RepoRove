package app.reporove.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.reporove.core.translation.TranslationConfig
import kotlinx.coroutines.*
import app.reporove.core.network.userMessage

@Composable fun TranslationSettings(app: AppModel) {
    var config by remember { mutableStateOf(app.container.translation.configuration()) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("翻译服务", style = MaterialTheme.typography.titleLarge)
        Text("使用你自己的 Chat Completions 兼容服务。全文和选段共用配置，费用由服务商收取；密钥只加密保存在本机。", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(config.endpoint, { config = config.copy(endpoint = it.trim()); saved = false }, label = { Text("完整接口地址（HTTPS）") }, placeholder = { Text("https://你的服务/v1/chat/completions") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(config.model, { config = config.copy(model = it); saved = false }, label = { Text("模型名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(config.key, { config = config.copy(key = it.trim()); saved = false }, label = { Text("API 密钥") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(config.target, { config = config.copy(target = it); saved = false }, label = { Text("目标语言") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row { Text("允许发送私有仓库的正文或选段", Modifier.weight(1f)); Switch(config.allowPrivate, { config = config.copy(allowPrivate = it); saved = false }) }
        Text("默认只翻译公开内容；允许私有内容后，所选文字将发送到上方服务。原文与代码不会被覆盖。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { app.action("translation-reset", "翻译配置已清除") { app.container.translation.resetConfiguration(); config = TranslationConfig(); saved = false } }) { Text("清除服务与密钥") }
        Button(onClick = { val draft = config.copy(model = config.model.trim(), target = config.target.trim()); app.action("translation-config", "翻译配置已保存") { app.container.translation.configure(draft); saved = true } }) { Text(if (saved) "已保存" else "保存") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TranslationSheet(source: String, fullName: String?, privateHint: Boolean? = null, document: Boolean = false, onTranslated: (String) -> Unit = {}, onDismiss: () -> Unit) {
    val app = LocalAppModel.current; val nav = LocalAppNavigation.current
    val scope = rememberCoroutineScope()
    var output by remember(source) { mutableStateOf<String?>(null) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf("准备翻译") }
    val configuration = app.container.translation.configuration()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 580.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (document) "翻译全文" else "翻译选中文字", style = MaterialTheme.typography.titleLarge)
            Text("${configuration.target} · ${if (configuration.model.isBlank()) "尚未配置服务" else configuration.model}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (configuration.endpoint.isBlank() || configuration.key.isBlank()) {
                Text("先在设置中填写服务地址、模型和密钥。")
                Button(onClick = { onDismiss(); nav.settingsSection("translation") }) { Text("配置翻译服务") }
            } else {
                if (!document) SelectionContainer { Text(source, style = MaterialTheme.typography.bodyMedium, maxLines = 8) }
                if (document) Text("保留链接、代码和排版；按段生成并保存译文。完成的段落会在重试时复用，已有译文可断网读取。")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (job?.isActive == true) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress); TextButton(onClick = { job?.cancel(); job = null; progress = "已停止，已完成段落保留" }) { Text("停止") } }
                else if (output == null) Button(onClick = {
                    error = null
                    job = scope.launch {
                        try {
                            val bound = app.container.repository.boundSource()
                            val private = privateHint ?: fullName?.let { app.container.repository.repository(it).data.isPrivate } ?: false
                            val translated = if (document) app.container.translation.document(source, bound.scope, private, bound.active) { done, total -> progress = "$done / $total 段" }
                                else app.container.translation.text(source, bound.scope, private, bound.active)
                            output = translated
                            if (document) { onTranslated(translated); onDismiss() }
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = userMessage(e) }
                        finally { job = null }
                    }
                }) { Text(if (error == null) "翻译" else "重试") }
                output?.takeIf { !document }?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            }
        }
    }
}
