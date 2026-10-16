package app.reporove.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reporove.core.model.*
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun trendingTime(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
private fun trendingCount(count: Long): String = NumberFormat.getIntegerInstance().format(count)

@Composable fun TrendingScreen(app: AppModel, prefs: Preferences, nav: AppNavigation) {
    val model = screenModel("trending") { ResourceModel<TrendingBoard>() }
    val state by model.state.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    val period = prefs.trendingPeriod
    val language = prefs.trendingLanguage
    var picker by remember { mutableStateOf(false) }
    LaunchedEffect(period, language) { model.configure("$period:$language") { app.container.trending.board(period, language, it) } }
    val ready = (state as? LoadState.Ready)?.takeIf { it.value.period == period && it.value.language == language }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().testTag("trending-list")) {
        item { ChoiceRow(TrendingPeriod.entries, period, { it.label }, { selected -> app.updatePreferences { it.copy(trendingPeriod = selected) } }) }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { picker = true }) { Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(prefs.trendingLanguageName) }
            TextButton(onClick = { nav.trendingDigest() }) { Text("今日精选") }
            IconButton(onClick = { nav.settingsSection("trending") }) { Icon(Icons.Outlined.NotificationsNone, "每日发现提醒") }
        } }
        item { SectionTitle("完整热榜", "刷新", model::refresh) }
        if (ready == null) item { Resource(if (state is LoadState.Ready) LoadState.Loading else state, model::refresh) {} }
        else {
            item { Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("GitHub Trending · ${period.label} · ${ready.value.entries.size} 个项目", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${if (ready.offline) "缓存" else "获取于"} ${trendingTime(ready.value.fetchedAt)}", style = MaterialTheme.typography.bodyMedium, color = if (ready.offline) LocalSemanticColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { openUrl(context, ready.value.sourceUrl) }) { Text("榜单来源") }
                }
                if (ready.offline) Text("暂时无法更新，保留上次完整榜单。", style = MaterialTheme.typography.bodyMedium)
            } }
            if (ready.value.entries.isEmpty()) item { EmptyState("这个范围暂无上榜项目", "可以换个时间范围或语言。") }
            items(ready.value.entries, key = TrendingEntry::fullName) { entry -> TrendingProjectRow(app, nav, entry, period, collections) }
            if (ready.value.entries.isNotEmpty()) item { Note("已显示源榜提供的全部 ${ready.value.entries.size} 个项目，保留原榜顺序。") }
        }
    }
    if (picker) TrendingLanguagePicker(ready?.value?.languages.orEmpty(), prefs.trendingLanguage, { picker = false }) { selected ->
        app.updatePreferences { it.copy(trendingLanguage = selected?.slug.orEmpty(), trendingLanguageName = selected?.name ?: "全部语言") }
        picker = false
    }
}

@Composable private fun TrendingLanguagePicker(languages: List<TrendingLanguage>, selected: String, close: () -> Unit, choose: (TrendingLanguage?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("选择榜单语言") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("查找语言") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item { TextButton(onClick = { choose(null) }, modifier = Modifier.fillMaxWidth()) { Text(if (selected.isEmpty()) "全部语言 ✓" else "全部语言") } }
                items(languages.filter { it.name.contains(query.trim(), true) || it.slug.contains(query.trim(), true) }, key = TrendingLanguage::slug) { language ->
                    TextButton(onClick = { choose(language) }, modifier = Modifier.fillMaxWidth().testTag("trending-language:${language.slug}")) { Text(language.name + if (language.slug == selected) " ✓" else "") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("关闭") } })
}

@Composable private fun TrendingProjectRow(app: AppModel, nav: AppNavigation, entry: TrendingEntry, period: TrendingPeriod, collections: CollectionState, reason: String? = null, dismiss: (() -> Unit)? = null) {
    val busy by app.busy.collectAsStateWithLifecycle()
    val saved = collections.later.any { it.fullName.equals(entry.fullName, true) }
    val owner = entry.fullName.substringBefore('/')
    val open = { app.action("trending-open:${entry.fullName}") { app.container.trending.opened(entry.fullName) }; nav.repo(entry.fullName) }
    Column(Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 20.dp, vertical = 14.dp).testTag("trending:${entry.fullName}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#${entry.rank}", color = LocalSemanticColors.current.link, style = MaterialTheme.typography.titleSmall)
            Text(owner, Modifier.weight(1f).clickable { nav.profile(owner) }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            IconToggleButton(checked = saved, enabled = "trending-later:${entry.fullName}" !in busy, onCheckedChange = {
                app.action("trending-later:${entry.fullName}") {
                    val repo = app.container.repository.repository(entry.fullName).data
                    require(!repo.isPrivate) { "项目已不再公开，请在仓库页面操作。" }
                    app.container.local.toggleLater(repo)
                }
            }, modifier = Modifier.size(36.dp)) { Icon(if (saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder, if (saved) "移出稍后看 ${entry.fullName}" else "稍后看 ${entry.fullName}") }
        }
        Text(entry.fullName.substringAfter('/'), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.growth?.let { Text("${period.growthLabel} +${trendingCount(it)}", color = LocalSemanticColors.current.success, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            entry.stars?.let { Icon(Icons.Outlined.StarBorder, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(trendingCount(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        entry.language?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalSemanticColors.current.link) }
        reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        dismiss?.let { TextButton(onClick = it, modifier = Modifier.heightIn(min = 36.dp)) { Text("不感兴趣") } }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable fun TrendingDigestScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, savedDay: String = "") {
    val model = screenModel("trending-digest:$savedDay") { ResourceModel<TrendingDigest>() }
    val state by model.state.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    val key = "$savedDay:${prefs.interests}:${prefs.mutedTopics}:${prefs.mutedRepositories}"
    LaunchedEffect(key) { model.configure(key) { refresh ->
        if (savedDay.isNotEmpty()) {
            val digest = app.container.trending.savedDigest(savedDay) ?: throw IllegalArgumentException("这份精选已不在本机，可以查看完整热榜。")
            Loaded(digest, digest.fetchedAt)
        } else app.container.trendingDigests.preview(refresh)
    } }
    LazyColumn(Modifier.fillMaxSize().testTag("trending-digest-list")) {
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { nav.root(MainTab.Trending) }) { Text("查看完整热榜") }
            TextButton(onClick = { nav.settingsSection("trending") }) { Text("每日提醒") }
        } }
        item { SectionTitle(if (savedDay.isEmpty()) "为你精选" else "$savedDay 的精选", if (savedDay.isEmpty()) "刷新" else null, model::refresh) }
        if (savedDay.isEmpty()) item { TextButton(onClick = nav::interests, modifier = Modifier.padding(horizontal = 12.dp)) { Text(if (prefs.interests.isEmpty()) "添加兴趣，让精选更贴近你" else "调整兴趣") } }
        when (val value = state) {
            is LoadState.Ready -> {
                item { Text("${value.value.day} · ${if (value.offline) "缓存于" else "榜单获取于"} ${trendingTime(value.value.fetchedAt)}", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (value.value.picks.isEmpty()) item { EmptyState("暂时没有新的精选项目", "完整热榜仍可随时浏览。") }
                items(value.value.picks, key = { it.entry.fullName }) { pick ->
                    TrendingProjectRow(app, nav, pick.entry, TrendingPeriod.Daily, collections, pick.reason, dismiss = if (savedDay.isNotEmpty()) null else {{
                        app.action("mute-trending:${pick.entry.fullName}") {
                            val id = pick.entry.repositoryId ?: app.container.repository.repository(pick.entry.fullName).data.id
                            app.container.local.updatePreferences { it.copy(mutedRepositories = it.mutedRepositories + id, mutedRepositoryNames = it.mutedRepositoryNames + (id to pick.entry.fullName)) }
                            app.message("已从精选中排除，可在推荐排除中恢复")
                        }
                    }})
                }
                item { Note("精选会跳过稍后看、近期打开与此前提醒过的项目；完整热榜不做这些排除。") }
            }
            else -> item { Resource(value, model::refresh) {} }
        }
    }
}

@Composable fun TrendingNotificationSettings(app: AppModel, prefs: Preferences, nav: AppNavigation) {
    val context = LocalContext.current
    val notifier = app.container.trendingNotifications
    var allowed by remember { mutableStateOf(notifier.allowed()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = notifier.allowed() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun enable(value: Boolean) { if (!value) notifier.stopImmediately(); app.action("trending-notifications") { notifier.setEnabled(value); allowed = notifier.allowed() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted) enable(true) else { enable(false); app.message("通知未开启，仍可随时查看完整热榜和精选") }
    }
    val busy by app.busy.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize()) {
        item { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("每日发现通知", style = MaterialTheme.typography.titleMedium); Text("有新内容时，每天最多一条精选摘要", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = prefs.trendingNotifications, enabled = "trending-notifications" !in busy, onCheckedChange = { value ->
                if (!value) enable(false)
                else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { asked = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                else if (notifier.allowed()) enable(true)
                else { asked = true; app.message("请在系统通知设置中开启每日发现") }
            }, modifier = Modifier.testTag("trending-notification-switch"))
        } }
        item { Note("提醒默认关闭。开启后在白天检查新精选；省电和后台限制可能使提醒延迟，不保证准点。关闭后不影响查看热榜。") }
        if ((asked || prefs.trendingNotifications) && !allowed) item { TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("打开系统通知设置") } }
        item { TextButton(enabled = prefs.trendingNotifications && allowed && "trending-check" !in busy, onClick = { app.action("trending-check") { app.message(notifier.check().message) } }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(if ("trending-check" in busy) "正在检查…" else "立即检查今日精选") } }
        item { ActionRow("查看完整热榜", "不受推荐和通知开关影响") { nav.root(MainTab.Trending) } }
        item { ActionRow("为你精选", "结合兴趣选择近期增长项目") { nav.trendingDigest() } }
    }
}
