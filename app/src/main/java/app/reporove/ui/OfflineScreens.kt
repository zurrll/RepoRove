package app.reporove.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reporove.core.model.*
import app.reporove.core.offline.*
import java.text.DateFormat
import java.util.Date

@Composable fun OfflineSaveDialog(fullName: String, ref: String, dismiss: () -> Unit) {
    val context = LocalContext.current; val app = LocalAppModel.current
    var docs by remember { mutableStateOf(true) }; var code by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("保存到本机") }, text = { Column {
        Text(fullName, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(docs, { docs = it }); Text("仓库资料与 README") }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(code, { code = it }); Text("源码目录与文件") }
        Text("固定 $ref 的当前提交，可断网阅读。资料包含概况和语言；源码不包含 Git 历史、子模块内容或 LFS 实体。", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = { app.action("offline-start:$fullName") { OfflineSaveService.start(context, SaveRequest(fullName, ref, docs, code)); dismiss() } }, enabled = docs || code) { Text("保存") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable fun OfflineLibrary(app: AppModel, prefs: Preferences, nav: AppNavigation) {
    val context = LocalContext.current
    val snapshots by app.container.offline.snapshots.collectAsStateWithLifecycle()
    val tasks by app.container.offline.progress.collectAsStateWithLifecycle()
    val recent by app.container.local.readings.collectAsStateWithLifecycle(emptyList())
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("离线保存") }
    var deleting by remember { mutableStateOf<OfflineSnapshot?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { ChoiceRow(listOf("离线保存", "最近阅读"), section, { it }, { section = it }) }
        if (section == "离线保存") {
            tasks.forEach { (key, task) ->
                item("task:$key") { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(key, style = MaterialTheme.typography.titleMedium)
                    Text("${task.stage}${if (task.bytes > 0) " · ${bytesLabel(task.bytes)}" else ""}", style = MaterialTheme.typography.bodyMedium)
                    task.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row {
                        if (task.running) TextButton(onClick = { OfflineSaveService.cancel(context, key) }) { Text("取消") }
                        else {
                            if (task.error != null) TextButton(onClick = { OfflineSaveService.start(context, task.request) }) { Text("重试") }
                            TextButton(onClick = { app.action("dismiss-task:$key") { app.container.offline.dismissTask(key) } }) { Text("收起") }
                        }
                    }
                } }
            }
            items(snapshots, key = { it.id }) { snapshot ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(snapshot.repository.fullName, style = MaterialTheme.typography.titleMedium)
                    Text("${snapshot.ref} · ${snapshot.sha.take(7)} · ${bytesLabel(snapshot.bytes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${if (snapshot.documents) "资料 " else ""}${if (snapshot.code) "源码 · ${snapshot.entries.count { !it.directory }} 个文件" else ""}", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { nav.offlineRepo(snapshot.id) }) { Text("阅读") }
                        TextButton(onClick = { OfflineSaveService.start(context, SaveRequest(snapshot.repository.fullName, snapshot.ref, snapshot.documents, snapshot.code)) }) { Text("更新") }
                        TextButton(onClick = { deleting = snapshot }, enabled = tasks[snapshot.repository.fullName]?.running != true) { Text("删除") }
                    }
                }
                HorizontalDivider()
            }
            if (snapshots.isEmpty() && tasks.isEmpty()) item { EmptyState("尚未保存离线仓库", "仓库的收藏菜单和更多栏目中可以保存资料与源码。") }
        } else {
            item { Note("最近打开的仓库和文件。临时缓存会被清理；需要可靠断网阅读时，请主动保存。") }
            val visible = recent.filter { it.scope == app.container.repository.scope }
            items(visible, key = { "${it.fullName}:${it.ref}:${it.path}" }) { record -> ActionRow(record.path.ifEmpty { record.fullName }.substringAfterLast('/'), "${record.fullName} · ${if (record.path.isEmpty()) "仓库" else record.path}") { if (record.path.isEmpty()) nav.repo(record.fullName) else nav.code(record.fullName, record.ref, record.path) } }
            if (visible.isEmpty()) item { EmptyState("暂无最近阅读") }
        }
    }
    deleting?.let { snapshot -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除离线快照？") }, text = { Text("${snapshot.repository.fullName} 的本机资料与源码将删除，GitHub 仓库不受影响。") }, confirmButton = { TextButton(onClick = { app.action("delete-offline:${snapshot.id}", "本机快照已删除") { app.container.offline.delete(snapshot.id); deleting = null } }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}

@Composable fun OfflineRepositoryScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, id: String, initialPath: String? = null) {
    val snapshots by app.container.offline.snapshots.collectAsStateWithLifecycle()
    val snapshot = snapshots.firstOrNull { it.id == id } ?: run { EmptyState("离线快照不存在", "快照可能已被删除、更新，或所属账号已退出。"); return }
    var section by rememberSaveable(id) { mutableStateOf(if (initialPath != null || !snapshot.documents) "源码" else "资料") }
    var translate by remember { mutableIntStateOf(0) }
    var information by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (initialPath == null && section == "资料") Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(snapshot.repository.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { information = true }) { Icon(Icons.Outlined.Info, "已保存的仓库信息") }
            SourceBadge(true, snapshot.savedAt, "离线")
            if (section == "资料" && snapshot.readme != null) IconButton(onClick = { translate++ }) { Icon(Icons.Outlined.Translate, "AI 全文 / 原文") }
        }
        if (initialPath == null && snapshot.documents && snapshot.code) ChoiceRow(listOf("资料", "源码"), section, { it }, { section = it })
        if (section == "源码" && snapshot.code) CodeBrowser(app, prefs, nav, snapshot.repository.fullName, snapshot.sha, initialPath.orEmpty(), snapshot = snapshot)
        else if (snapshot.documents) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("★ ${snapshot.repository.stars.toString()}", color = LocalSemanticColors.current.warning, style = MaterialTheme.typography.labelMedium)
                Text(snapshot.repository.language ?: "", color = LocalSemanticColors.current.link, style = MaterialTheme.typography.labelMedium)
                Text(snapshot.sha.take(7), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            if (snapshot.readme != null) MarkdownBody(snapshot.readme, prefs, Modifier.weight(1f), snapshot.repository.fullName, snapshot.sha, snapshot.readmePath ?: "README.md", fill = true, renderedHtml = snapshot.readmeHtml, offlineId = snapshot.id, translationRequest = translate, privateHint = snapshot.repository.isPrivate)
            else EmptyState("没有 README", snapshot.repository.description)
        }
    }
    if (information) AlertDialog(onDismissRequest = { information = false }, title = { Text(snapshot.repository.fullName) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            snapshot.repository.description?.let { Text(it) }
            Text("${snapshot.ref} · ${snapshot.sha.take(7)}")
            Text("Star ${snapshot.repository.stars} · Fork ${snapshot.repository.forks} · Issue / PR ${snapshot.repository.openItems}")
            snapshot.repository.license?.spdxId?.let { Text("许可：$it") }
            if (snapshot.repository.topics.isNotEmpty()) Text("主题：${snapshot.repository.topics.joinToString("、")}")
            val total = snapshot.languages.values.filter { it > 0 }.sum().toDouble()
            if (total > 0) {
                Text("语言构成", style = MaterialTheme.typography.titleSmall)
                snapshot.languages.filterValues { it > 0 }.entries.sortedByDescending { it.value }.forEach { (name, size) ->
                    Text("$name · ${"%.1f".format(size / total * 100)}%", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { (size / total).toFloat() }, modifier = Modifier.fillMaxWidth())
                }
            }
            Text("${DateFormat.getDateTimeInstance().format(Date(snapshot.savedAt))} 保存")
            if (snapshot.missingImages > 0) Text("${snapshot.missingImages} 张仓库图片未保存；外部图片未自动下载。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { information = false }) { Text("关闭") } })

}