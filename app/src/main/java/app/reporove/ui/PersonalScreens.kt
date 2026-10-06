package app.reporove.ui

import android.app.DownloadManager
import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.reporove.BuildConfig
import app.reporove.core.model.*
import app.reporove.data.DownloadProgress
import app.reporove.core.network.userMessage
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive

private fun <T> List<T>.moved(value: T, offset: Int): List<T> {
    val from = indexOf(value)
    val to = from + offset
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { removeAt(from); add(to, value) }
}

@Composable private fun <T> OptionRow(value: T, selected: List<T>, label: String, onToggle: () -> Unit, onMove: (Int) -> Unit) {
    val index = selected.indexOf(value)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(index >= 0, { onToggle() }, Modifier.semantics { contentDescription = "显示$label" })
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (index >= 0) {
            IconButton(enabled = index > 0, onClick = { onMove(-1) }) { Icon(Icons.Outlined.KeyboardArrowUp, "上移$label") }
            IconButton(enabled = index < selected.lastIndex, onClick = { onMove(1) }) { Icon(Icons.Outlined.KeyboardArrowDown, "下移$label") }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable fun SettingsScreen(app: AppModel, prefs: Preferences, nav: AppNavigation) {
    LazyColumn(Modifier.testTag("settings-list")) {
        item { ActionRow("外观与阅读", "${prefs.themeId.label} · ${prefs.theme.label} · 字号 ${(prefs.textScale * 100).toInt()}%") { nav.settingsSection("appearance") } }
        item { ActionRow("导航与启动", "启动：${prefs.home.label}") { nav.settingsSection("navigation") } }
        item { ActionRow("仓库布局", "${prefs.repoTabs.size} 个常驻栏目 · 全仓库共用") { nav.settingsSection("repository") } }
        item { ActionRow("推荐兴趣", "${prefs.interests.size} 个兴趣 · 也可在为你调整", nav::interests) }
        item { ActionRow("推荐排除", "${prefs.mutedRepositories.size} 个项目 · ${prefs.mutedTopics.size} 个主题") { nav.settingsSection("feedback") } }
        item { ActionRow("关于", "RepoRove ${BuildConfig.VERSION_NAME}") { nav.settingsSection("about") } }
    }
}

@Composable fun SettingsDetailScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, section: String) {
    if (section == "feedback") { RecommendationExclusionsScreen(app, prefs); return }
    var reset by remember { mutableStateOf(false) }
    val resetLabel = when (section) { "appearance" -> "恢复默认外观与阅读"; "navigation" -> "恢复默认导航"; else -> "恢复默认仓库布局" }
    LazyColumn(Modifier.testTag("settings-detail-list")) {
        if (section == "navigation") {
        item { SectionTitle("底部导航") }
        item { Note("选择 1–5 项，使用箭头调整顺序。搜索和我的也可从工具栏进入，设置在我的页面中。") }
        items(prefs.tabs + MainTab.entries.filterNot { it in prefs.tabs }, key = { "tab:${it.name}" }) { tab ->
            OptionRow(tab, prefs.tabs, tab.label, {
                when {
                    tab in prefs.tabs && prefs.tabs.size == 1 -> app.message("至少保留一个底部入口")
                    tab !in prefs.tabs && prefs.tabs.size >= 5 -> app.message("最多显示 5 个入口，请先移除一个")
                    else -> app.updatePreferences { it.copy(tabs = if (tab in it.tabs) it.tabs - tab else it.tabs + tab) }
                }
            }, { offset -> app.updatePreferences { it.copy(tabs = it.tabs.moved(tab, offset)) } })
        }
        item { SectionTitle("启动页面") }
        item { ChoiceRow(prefs.tabs, prefs.home, { it.label }, { tab -> app.updatePreferences { it.copy(home = tab) } }) }
        }
        if (section == "repository") {
        item { SectionTitle("仓库栏目") }
        item { Note("选中的栏目常驻；其余收进“更多”，随时可访问。") }
        items(prefs.repoTabs + RepoTab.entries.filterNot { it in prefs.repoTabs }, key = { "repo:${it.name}" }) { tab -> OptionRow(tab, prefs.repoTabs, tab.label, { app.updatePreferences { it.copy(repoTabs = if (tab in it.repoTabs) it.repoTabs - tab else it.repoTabs + tab) } }, { offset -> app.updatePreferences { it.copy(repoTabs = it.repoTabs.moved(tab, offset)) } }) }
        item { SectionTitle("仓库概览模块") }
        items(prefs.modules + RepoModule.entries.filterNot { it in prefs.modules }, key = { "module:${it.name}" }) { module -> OptionRow(module, prefs.modules, module.label, { app.updatePreferences { it.copy(modules = if (module in it.modules) it.modules - module else it.modules + module) } }, { offset -> app.updatePreferences { it.copy(modules = it.modules.moved(module, offset)) } }) }
        }
        if (section == "appearance") {
            item { SectionTitle("主题") }
            item { ChoiceRow(ThemeId.entries, prefs.themeId, { it.label }, { id -> app.updatePreferences { it.copy(themeId = id) } }) }
            item { SectionTitle("明暗模式") }
            item { ChoiceRow(ThemeMode.entries, prefs.theme, { it.label }, { mode -> app.updatePreferences { it.copy(theme = mode) } }) }
            item { SectionTitle("阅读字号") }
            item { ChoiceRow(listOf(.9f, 1f, 1.1f, 1.2f), prefs.textScale, { "${(it * 100).toInt()}%" }, { value -> app.updatePreferences { it.copy(textScale = value) } }) }
            item { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("阅读示例", style = MaterialTheme.typography.titleLarge)
                Text("清楚的文字、自然的间距，让项目介绍更容易阅读。", style = MaterialTheme.typography.bodyLarge)
                Text("链接与重点信息", color = LocalSemanticColors.current.link, style = MaterialTheme.typography.bodyLarge)
                Text("字号会与系统字体大小共同生效。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { SectionTitle("列表密度") }
            item { ChoiceRow(listOf(false, true), prefs.compact, { if (it) "紧凑" else "舒展" }, { value -> app.updatePreferences { it.copy(compact = value) } }) }
        }
        if (section == "about") {
            item { SectionTitle("RepoRove ${BuildConfig.VERSION_NAME}") }
            item { Note("GitHub Android 客户端。探索使用官方目录；为你根据兴趣推荐，可选择参考 Star。") }
        } else item { TextButton(onClick = { reset = true }, Modifier.padding(20.dp)) { Text(resetLabel) } }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("$resetLabel？") }, confirmButton = { TextButton(onClick = {
        app.updatePreferences { val defaults = Preferences(); when (section) {
            "appearance" -> it.copy(themeId = defaults.themeId, theme = defaults.theme, textScale = defaults.textScale, compact = defaults.compact)
            "navigation" -> it.copy(tabs = defaults.tabs, home = defaults.home)
            else -> it.copy(repoTabs = defaults.repoTabs, modules = defaults.modules)
        } }; reset = false
    }) { Text("恢复") } }, dismissButton = { TextButton(onClick = { reset = false }) { Text("取消") } })
}

@Composable fun ProfileScreen(app: AppModel, nav: AppNavigation) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    val prefs by app.container.local.preferences.collectAsStateWithLifecycle(Preferences())
    if (account != null) { UserProfileScreen(app, prefs, nav, account!!.login, own = true); return }
    val model = screenModel("account") { AccountModel(app.container) }
    val busy by model.busy.collectAsStateWithLifecycle()
    val code by model.code.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf("") }
    var privateRepositories by rememberSaveable { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    LazyColumn {
        if (account == null) {
            item { SectionTitle("连接你的 GitHub") }
            item { Note("登录后同步 Star、访问你的仓库并处理通知。访问令牌加密保存在手机上。") }
            if (BuildConfig.GITHUB_OAUTH_CLIENT_ID.isNotBlank()) item {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(privateRepositories, { privateRepositories = it }); Text("同时访问私有仓库") }
                    Button(enabled = !busy, onClick = { model.deviceLogin(privateRepositories) }) { Text("通过浏览器登录") }
                }
            }
            code?.let { deviceCode -> item {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("在 GitHub 输入此授权码", style = MaterialTheme.typography.bodyMedium)
                    Text(deviceCode.userCode, style = MaterialTheme.typography.headlineSmall)
                    Row { TextButton(onClick = { app.action("copy-auth", "已复制授权码") { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("GitHub 授权码", deviceCode.userCode))) } }) { Text("复制授权码") }; TextButton(onClick = { openUrl(context, deviceCode.verificationUri) }) { Text("打开 GitHub 授权") } }
                    TextButton(onClick = model::cancel) { Text("取消登录") }
                }
            } }
            item {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("GitHub 访问令牌") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
                    Button(enabled = token.isNotBlank() && !busy, onClick = { model.tokenLogin(token) }) { Text(if (busy) "正在连接…" else "使用令牌登录") }
                    if (busy && code == null) LoadingIndicator()
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = { openUrl(context, "https://github.com/settings/tokens") }) { Text("在 GitHub 创建访问令牌") }
                    Text("建议使用 classic PAT。Star 需要 public_repo，收件箱需要 notifications，组织需要 read:org；私有仓库需要 repo。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { ActionRow("界面与内容", "主题、导航、仓库栏目与阅读", nav::settings) }
        item { ActionRow("下载", "查看进度与已下载文件", nav::downloads) }
        if (account != null) item { ActionRow("退出账号", onClick = { logout = true }) }
    }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("退出 GitHub？") }, text = { Text("清除凭据、内容缓存和本机收藏中的私有仓库。保留公开收藏及界面设置。") }, confirmButton = { TextButton(onClick = { logout = false; app.action("logout") { app.container.repository.logout() } }) { Text("退出") } }, dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } })
}

@Composable fun DownloadsScreen(app: AppModel) {
    val records by app.container.local.downloads.collectAsStateWithLifecycle(emptyList())
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var progress by remember { mutableStateOf<Map<Long, DownloadProgress?>>(emptyMap()) }
    var progressError by remember { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<DownloadRecord?>(null) }
    var forget by remember { mutableStateOf<DownloadRecord?>(null) }
    val busy by app.busy.collectAsStateWithLifecycle()
    LaunchedEffect(records, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                try { progress = records.associate { it.id to app.container.downloads.progress(it.id) }; progressError = null }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { progressError = userMessage(error) }
                delay(1500)
            }
        }
    }
    LazyColumn {
        item { Note("文件保存到手机 Downloads/RepoRove 文件夹。") }
        progressError?.let { item { Note("无法读取下载状态：$it") } }
        items(records, key = DownloadRecord::id) { record ->
            val item = progress[record.id]
            val status = when (item?.status) { DownloadManager.STATUS_SUCCESSFUL -> "已完成"; DownloadManager.STATUS_FAILED -> "下载失败（${item.reason}）"; DownloadManager.STATUS_PAUSED -> "等待恢复"; DownloadManager.STATUS_RUNNING -> "${bytesLabel(item.bytes)} / ${if (item.total > 0) bytesLabel(item.total) else "未知大小"}"; DownloadManager.STATUS_PENDING -> "等待下载"; else -> if (progress.containsKey(record.id)) "系统下载记录已移除，磁盘文件需检查" else "正在读取状态" }
            val running = item?.status in listOf(DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED)
            val canLocate = record.fileName != null || item?.localUri != null
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.name, style = MaterialTheme.typography.titleMedium)
                Text("${record.repository} · $status", style = MaterialTheme.typography.bodySmall)
                if (item?.status == DownloadManager.STATUS_RUNNING && item.total > 0) LinearProgressIndicator(progress = { (item.bytes.toFloat() / item.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row {
                    if (item?.status == DownloadManager.STATUS_SUCCESSFUL) TextButton(onClick = { app.action("open:${record.id}") { app.container.downloads.open(record.id) } }) { Text("打开文件") }
                    if (canLocate) TextButton(enabled = progress.containsKey(record.id) && "remove:${record.id}" !in busy, onClick = { remove = record }) { Text(if (running) "取消下载" else "删除文件") }
                    if (progress.containsKey(record.id) && item == null) TextButton(enabled = "forget:${record.id}" !in busy, onClick = { forget = record }) { Text("移除记录") }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (records.isEmpty()) item { EmptyState("暂无下载", "在仓库发布页面选择需要的文件。") }
    }
    remove?.let { record ->
        val running = progress[record.id]?.status in listOf(DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED)
        val action = if (running) "取消下载" else "删除文件"
        AlertDialog(onDismissRequest = { remove = null }, title = { Text("$action？") }, text = { Text(if (running) "取消 ${record.name} 的下载，并删除未完成的文件。" else "从 Downloads/RepoRove 删除 ${record.name}，确认文件删除后才移除记录。") }, confirmButton = { TextButton(onClick = { remove = null; app.action("remove:${record.id}", if (running) "下载已取消，未完成文件已删除" else "文件已删除") { app.container.downloads.remove(record.id) } }) { Text(action) } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("返回") } })
    }
    forget?.let { record -> AlertDialog(onDismissRequest = { forget = null }, title = { Text("仅移除记录？") }, text = { Text("移除本页的 ${record.name} 记录，磁盘文件会保留。请在 Downloads/RepoRove 中确认并管理文件。") }, confirmButton = { TextButton(onClick = { forget = null; app.action("forget:${record.id}", "记录已移除，磁盘文件未删除") { app.container.downloads.forget(record.id) } }) { Text("移除记录") } }, dismissButton = { TextButton(onClick = { forget = null }) { Text("返回") } }) }
}
