package app.reporove.ui

import android.text.Selection
import android.text.Spannable
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reporove.core.model.*
import app.reporove.core.offline.OfflineSnapshot
import app.reporove.data.GitHubRepository
import java.text.DateFormat
import java.util.Date

@Composable fun SourceBadge(cached: Boolean, savedAt: Long = 0, label: String = "缓存", refresh: (() -> Unit)? = null) {
    var details by remember { mutableStateOf(false) }
    if (cached) TextButton(onClick = { details = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelMedium) }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("${label}内容") }, text = { Text(if (savedAt > 0) "保存时间：${DateFormat.getDateTimeInstance().format(Date(savedAt))}。" else "当前使用本机保存的内容，联网后可以刷新。") }, confirmButton = { TextButton(onClick = { details = false; refresh?.invoke() }) { Text(if (refresh != null) "刷新" else "知道了") } }, dismissButton = if (refresh != null) ({ TextButton(onClick = { details = false }) { Text("关闭") } }) else null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CodeBrowser(app: AppModel, prefs: Preferences, nav: AppNavigation, fullName: String, ref: String, initialPath: String, anchor: String = "", snapshot: OfflineSnapshot? = null) {
    val context = LocalContext.current
    val browserScope = remember { app.container.repository.scope }
    val model = screenModel("browser:$fullName:${snapshot?.id ?: "online"}:$ref:$initialPath") { CodeBrowserModel() }
    val state by model.state.collectAsStateWithLifecycle()
    val branches = screenModel("browser-branches:$fullName") { ResourceModel<List<Branch>>() }
    val branchState by branches.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) {
        model.configure(ref, initialPath) { currentRef, path, refresh ->
            if (snapshot != null) Loaded(app.container.offline.contents(snapshot.id, path), snapshot.savedAt, true)
            else app.container.repository.contents(fullName, path, currentRef, refresh)
        }
        if (snapshot == null) branches.configure(fullName) { app.container.repository.branches(fullName) }
    }
    LaunchedEffect(state.path, state.loading) { if (!state.loading && state.error == null && snapshot == null) app.container.local.recordReading(ReadingRecord(browserScope, fullName, state.ref, state.path, System.currentTimeMillis())) { app.container.repository.scope == browserScope } }
    BackHandler(enabled = state.path.isNotEmpty()) { model.open(state.path.substringBeforeLast('/', "")) }
    var tree by rememberSaveable { mutableStateOf(false) }
    var quick by rememberSaveable { mutableStateOf(false) }
    var branchMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var rendered by rememberSaveable(state.path) { mutableStateOf(false) }
    var wrap by rememberSaveable { mutableStateOf(false) }
    var find by remember { mutableStateOf(false) }
    var translate by remember { mutableIntStateOf(0) }
    val file = state.contents.singleOrNull()?.takeIf { it.path == state.path && it.type != "dir" }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { tree = true; model.loadDirectory("") }) { Icon(Icons.Outlined.AccountTree, "文件树") }
            Text(state.path.ifEmpty { "根目录" }, Modifier.weight(1f).clickable { tree = true; model.loadDirectory("") }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            SourceBadge(state.offline, snapshot?.savedAt ?: 0, if (snapshot != null) "离线" else "缓存")
            IconButton(onClick = { quick = true }) { Icon(Icons.Outlined.Search, "快速打开文件") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "文件操作") }
                DropdownMenu(menu, { menu = false }) {
                    if (state.path.isNotEmpty()) DropdownMenuItem(text = { Text("上一级") }, onClick = { menu = false; model.open(state.path.substringBeforeLast('/', "")) })
                    if (snapshot == null) DropdownMenuItem(text = { Text("刷新代码") }, onClick = { menu = false; model.open(state.path, refresh = true) })
                    if (file != null) {
                        if (file.name.substringAfterLast('.').lowercase() in listOf("md", "markdown", "mdown")) {
                            DropdownMenuItem(text = { Text(if (rendered) "查看源码" else "阅读模式") }, onClick = { menu = false; rendered = !rendered })
                            if (rendered) DropdownMenuItem(text = { Text("AI 全文 / 原文") }, onClick = { menu = false; translate++ })
                        }
                        DropdownMenuItem(text = { Text("复制内容") }, onClick = { menu = false; runCatching { GitHubRepository.text(file) }.getOrNull()?.let { copyText(context, file.name, it) } })
                        DropdownMenuItem(text = { Text(if (wrap) "关闭自动换行" else "自动换行") }, onClick = { menu = false; wrap = !wrap })
                        DropdownMenuItem(text = { Text("文件内查找 / 跳行") }, onClick = { menu = false; find = true })
                    }
                }
            }
        }
        if (file == null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box { TextButton(onClick = { if (snapshot == null) branchMenu = true }) { Text(if (snapshot == null) state.ref else "${snapshot.ref} · ${snapshot.sha.take(7)}", maxLines = 1); if (snapshot == null) Icon(Icons.Outlined.ArrowDropDown, "选择分支") }
                DropdownMenu(branchMenu, { branchMenu = false }) {
                    (branchState as? LoadState.Ready)?.value?.forEach { branch -> DropdownMenuItem(text = { Text(branch.name) }, onClick = { branchMenu = false; model.open("", branch.name) }) }
                    if (branchState is LoadState.Failed) DropdownMenuItem(text = { Text("重试加载分支") }, onClick = { branches.refresh() })
                }
            }
            Spacer(Modifier.weight(1f)); if (state.path.isNotEmpty()) IconButton(onClick = { model.open(state.path.substringBeforeLast('/', "")) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "上一级") }
        }
        when {
            state.loading -> LoadingIndicator()
            state.error != null -> EmptyState("无法读取文件", state.error, "重试", { model.open(state.path, refresh = true) })
            file != null -> {
                val text = remember(file) { runCatching { GitHubRepository.text(file) } }
                text.getOrNull()?.let { content ->
                    if (rendered) MarkdownBody(content, prefs, Modifier.weight(1f), fullName, state.ref, state.path, fill = true, initialAnchor = anchor, offlineId = snapshot?.id, translationRequest = translate, privateHint = snapshot?.repository?.isPrivate)
                    else CodePane(content, state.path, state.ref, prefs, model, Modifier.weight(1f), wrap, find, { find = false }, fullName, snapshot?.repository?.isPrivate, anchor)
                } ?: EmptyState("无法预览", text.exceptionOrNull()?.message)
            }
            else -> LazyColumn(Modifier.weight(1f)) {
                items(state.contents.sortedWith(compareBy<Content> { it.type != "dir" }.thenBy { it.name.lowercase() }), key = Content::path) { entry -> FileEntry(entry, state.path == entry.path) { model.open(entry.path) } }
                if (state.contents.isEmpty()) item { EmptyState("这个目录没有文件") }
            }
        }
    }
    if (tree) ModalBottomSheet(onDismissRequest = { tree = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            item { SectionTitle("文件树", "根目录", { tree = false; model.open("") }) }
            fun androidx.compose.foundation.lazy.LazyListScope.directory(path: String, depth: Int) {
                if (depth > 30) return
                val entries = state.directories[path]
                if (entries == null) { item("loading:$path") { TextButton(onClick = { model.loadDirectory(path, true) }, Modifier.padding(start = (20 + depth * 14).dp)) { Text("加载 / 重试这个目录") } }; return }
                entries.sortedWith(compareBy<Content> { it.type != "dir" }.thenBy { it.name.lowercase() }).forEach { entry ->
                    item("tree:${entry.path}") { Row(Modifier.padding(start = (depth * 14).dp), verticalAlignment = Alignment.CenterVertically) {
                        if (entry.type == "dir") IconButton(onClick = { model.expand(entry.path) }) { Icon(if (entry.path in state.expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, if (entry.path in state.expanded) "折叠目录" else "展开目录") }
                        else Spacer(Modifier.width(48.dp))
                        FileEntry(entry, entry.path == state.path, Modifier.weight(1f)) { tree = false; model.open(entry.path) }
                    } }
                    if (entry.type == "dir" && entry.path in state.expanded) directory(entry.path, depth + 1)
                }
            }
            directory("", 0)
        }
    }
    if (quick) {
        var query by remember { mutableStateOf("") }
        val known = remember(state.directories, snapshot) { (snapshot?.entries?.filterNot { it.directory }?.map { it.path } ?: state.directories.values.flatten().filter { it.type != "dir" }.map { it.path }).distinct() }
        val files = if (query.isBlank()) state.recent else (known + state.recent).distinct().filter { it.contains(query, true) }.take(100)
        ModalBottomSheet(onDismissRequest = { quick = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("文件名或路径") }, singleLine = true)
                Note(if (query.isBlank()) "最近打开" else if (snapshot != null) "全部已保存源码" else "已加载目录和最近文件；展开文件树可加载更多目录。")
                LazyColumn { items(files, key = { it }) { path -> ActionRow(path.substringAfterLast('/'), path) { quick = false; model.open(path) } }; if (files.isEmpty()) item { Note("暂无匹配文件。") } }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun FileEntry(entry: Content, selected: Boolean, modifier: Modifier = Modifier, open: () -> Unit) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth().combinedClickable(onClick = open, onLongClick = { copyText(context, "路径", entry.path) }).heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (entry.type == "dir") Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile, null, Modifier.size(20.dp), tint = if (entry.type == "dir" || selected) LocalSemanticColors.current.link else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(entry.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (selected) LocalSemanticColors.current.link else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        if (entry.type != "dir") Text(bytesLabel(entry.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun CodePane(content: String, path: String, ref: String, prefs: Preferences, model: CodeBrowserModel, modifier: Modifier, wrap: Boolean, finding: Boolean, closeFind: () -> Unit, fullName: String, privateHint: Boolean?, anchor: String) {
    val colors = MaterialTheme.colorScheme; val semantic = LocalSemanticColors.current
    val pages = remember(content) { codePages(content) }
    val identity = "$ref:$path"
    var page by rememberSaveable(identity) { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var scroll by remember { mutableStateOf<ScrollView?>(null) }
    var code by remember { mutableStateOf<CodeTextView?>(null) }
    var search by remember { mutableStateOf("") }
    var line by remember { mutableStateOf("") }
    var match by remember { mutableIntStateOf(-1) }
    var pendingLine by remember(identity) { mutableStateOf(anchor.removePrefix("L").substringBefore('-').toIntOrNull()) }
    val current = pages[page.coerceIn(pages.indices)]
    LaunchedEffect(pendingLine) { pendingLine?.let { target -> page = pages.indexOfLast { it.firstLine <= target }.coerceAtLeast(0) } }
    Column(modifier) {
        if (pages.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { page--; match = -1 }, enabled = page > 0) { Text("上一段") }; Text("${current.firstLine}–${current.lastLine} 行", style = MaterialTheme.typography.labelMedium); TextButton(onClick = { page++; match = -1 }, enabled = page < pages.lastIndex) { Text("下一段") }
        }
        key(identity, page, wrap) { AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
            val view = CodeTextView(context).apply { translateSelection = { selected = it }; firstLine = current.firstLine; setHorizontallyScrolling(!wrap) }
            code = view
            val vertical = ScrollView(context).apply { isFillViewport = true; isNestedScrollingEnabled = true }
            scroll = vertical
            if (wrap) vertical.addView(view, android.view.ViewGroup.LayoutParams(-1, -2))
            else { val horizontal = HorizontalScrollView(context).apply { isFillViewport = true; isNestedScrollingEnabled = true; addView(view, android.view.ViewGroup.LayoutParams(-2, -2)) }; vertical.addView(horizontal, android.view.ViewGroup.LayoutParams(-1, -2)) }
            val position = model.positions["$identity:$page"] ?: (0 to 0)
            vertical.post { vertical.scrollTo(0, position.first); (vertical.getChildAt(0) as? HorizontalScrollView)?.scrollTo(position.second, 0); pendingLine?.let { vertical.scrollTo(0, view.lineY(it)); pendingLine = null } }
            vertical.setOnScrollChangeListener { _, _, y, _, _ -> model.positions["$identity:$page"] = y to ((vertical.getChildAt(0) as? HorizontalScrollView)?.scrollX ?: 0) }
            vertical
        }, update = {
            code?.apply { textSize = 13f * prefs.textScale; gutterColor = colors.onSurfaceVariant.toArgb(); bind(current.text, path.substringAfterLast('.'), colors.onSurface.toArgb(), colors.onSurfaceVariant.toArgb(), semantic.success.toArgb(), semantic.merged.toArgb(), semantic.warning.toArgb()); setBackgroundColor(colors.background.toArgb()) }
        }) }
    }
    if (finding) AlertDialog(onDismissRequest = closeFind, title = { Text("查找与跳行") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(search, { search = it; match = -1 }, label = { Text("查找全文") }, singleLine = true)
        TextButton(onClick = {
            val next = content.indexOf(search, (match + 1).coerceAtLeast(0), true).takeIf { it >= 0 } ?: content.indexOf(search, 0, true)
            if (next >= 0 && search.isNotEmpty()) { match = next; val targetLine = content.take(next).count { it == '\n' } + 1; pendingLine = targetLine; closeFind() }
        }) { Text(if (match < 0) "查找" else "下一个") }
        OutlinedTextField(line, { line = it.filter(Char::isDigit) }, label = { Text("行号（1–${pages.last().lastLine}）") }, singleLine = true)
    } }, confirmButton = { TextButton(onClick = { line.toIntOrNull()?.coerceIn(1, pages.last().lastLine)?.let { pendingLine = it }; closeFind() }) { Text("跳转") } }, dismissButton = { TextButton(onClick = closeFind) { Text("关闭") } })
    LaunchedEffect(match, page) { if (match >= 0) { val offset = pages.take(page).sumOf { it.text.length }; code?.post { code?.markMatch(match - offset, search.length, semantic.warning.toArgb()) } } }
    LaunchedEffect(pendingLine, page) { pendingLine?.takeIf { it in current.firstLine..current.lastLine }?.let { target -> scroll?.post { code?.let { view -> scroll?.smoothScrollTo(0, view.lineY(target)); pendingLine = null } } } }
    selected?.let { TranslationSheet(it, fullName, privateHint, onDismiss = { selected = null }) }
}

data class CodePage(val text: String, val firstLine: Int, val lastLine: Int)
fun codePages(text: String): List<CodePage> {
    val pages = mutableListOf<CodePage>(); var start = 0; var first = 1; var line = 1
    for (i in text.indices) { if (text[i] == '\n') line++; if ((line - first >= 1000 || i - start >= 100_000) && text[i] == '\n') { pages += CodePage(text.substring(start, i + 1), first, line - 1); start = i + 1; first = line } }
    pages += CodePage(text.substring(start), first, line); return pages
}

private fun copyText(context: android.content.Context, label: String, text: String) {
    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
}
