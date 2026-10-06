package app.reporove.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reporove.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable fun LoginRequired(nav: AppNavigation) { EmptyState("登录后查看", "连接 GitHub 账号以同步你的内容。", "登录 GitHub", { nav.root(MainTab.Profile) }) }

@Composable fun LibraryScreen(app: AppModel, prefs: Preferences, nav: AppNavigation) {
    var section by rememberSaveable { mutableStateOf("稍后看") }
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    val model = screenModel("library") { PagedModel<Repository> { it.id } }
    val state by model.state.collectAsStateWithLifecycle()
    val revision by app.container.repository.revision.collectAsStateWithLifecycle()
    LaunchedEffect(section) { if (section !in listOf("稍后看", "我的仓库", "Star")) section = "稍后看" }
    LaunchedEffect(section, collections, revision, account?.id) {
        if ((section == "Star" || section == "我的仓库") && account == null) return@LaunchedEffect
        model.configure("$section:$revision:${account?.id}:${if (section == "稍后看") collections.later else ""}") { page, refresh ->
            when (section) {
                "Star" -> app.container.repository.starred(page, refresh).let { Loaded(Page(it.data, it.data.size == 30), it.cachedAt, it.offline) }
                "我的仓库" -> app.container.repository.myRepositories(page, refresh).let { Loaded(Page(it.data, it.data.size == 30), it.cachedAt, it.offline) }
                else -> app.container.repository.collections.first().let { Loaded(Page(it.later, false), System.currentTimeMillis()) }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle("保存与管理项目", "下载", nav::downloads) }
        item { ChoiceRow(listOf("稍后看", "我的仓库", "Star"), section, { it }, { section = it }) }
        if (section == "稍后看") item { Note("保存准备阅读的项目；持续跟踪与管理在动态页。") }
        if ((section == "Star" || section == "我的仓库") && account == null) item { LoginRequired(nav) }
        else {
            item { SectionTitle(section, "刷新", model::refresh) }
            items(state.items, key = Repository::id) { repo -> RepoRow(repo, prefs, collections.later.any { it.id == repo.id }, { nav.repo(repo.fullName) }, { app.action("later:${repo.id}") { app.container.local.toggleLater(repo) } }) }
            listFeedback(state, model::refresh, model::more, "这里还没有项目")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FeedScreen(app: AppModel, nav: AppNavigation) {
    var source by rememberSaveable { mutableStateOf("跟踪仓库") }
    var managing by rememberSaveable { mutableStateOf(false) }
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    val model = screenModel("feed") { PagedModel<Event> { it.id } }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(source, collections.following, account?.id) {
        if (source == "关注动态" && account == null) return@LaunchedEffect
        model.configure("$source:${collections.following.map { it.id }}") { page, refresh -> app.container.repository.feed(source == "关注动态", page, refresh).let { Loaded(Page(it.data, source == "关注动态" && it.data.size >= 30), it.cachedAt, it.offline) } }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { ChoiceRow(listOf("跟踪仓库", "关注动态"), source, { it }, { source = it }) }
        if (source == "跟踪仓库") item { SectionTitle("已跟踪 ${collections.following.size} 个仓库", "管理跟踪", { managing = true }) }
        item { SectionTitle("最近活动", "刷新", model::refresh) }
        item { Note(if (source == "跟踪仓库") "近期公开活动 · 聚合所有在 App 内跟踪的仓库" else "GitHub 公开活动 · 数据可能有延迟") }
        if (source == "关注动态" && account == null) item { LoginRequired(nav) }
        else {
            items(state.items, key = Event::id) { event ->
                val verb = when (event.type) { "ReleaseEvent" -> "发布版本"; "PushEvent" -> "推送代码"; "PullRequestEvent" -> "更新 PR"; "IssuesEvent" -> "更新 Issue"; "IssueCommentEvent" -> "参与讨论"; "WatchEvent" -> "Star 项目"; "ForkEvent" -> "Fork 项目"; "CreateEvent" -> "创建分支或标签"; else -> "项目活动" }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) { AuthorLink(event.actor) }
                ActionRow(verb, "${event.repo.name} · ${dateLabel(event.createdAt)}") {
                    nav.target(ActivityLinks.event(event), app)
                }
            }
            listFeedback(state, model::refresh, model::more, "跟踪感兴趣的仓库后，这里会显示它们的活动")
        }
    }
    if (managing) ModalBottomSheet(onDismissRequest = { managing = false }) {
        SectionTitle("跟踪的仓库")
        Note("持续汇总项目活动，停止跟踪不会移除稍后看或 Star。")
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(collections.following, key = Repository::id) { repo ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { managing = false; nav.repo(repo.fullName) }, Modifier.weight(1f)) { Text(repo.fullName) }
                    TextButton(onClick = { managing = false; app.action("unfollow:${repo.id}") {
                        app.container.local.toggleFollowing(repo)
                        app.message("已停止跟踪 ${repo.name}") { if (app.container.local.collections.first().following.none { it.id == repo.id }) app.container.local.toggleFollowing(repo) }
                    } }) { Text("停止跟踪") }
                }
            }
            if (collections.following.isEmpty()) item { EmptyState("还没有跟踪仓库", "在仓库页面的更多操作中选择 App 内跟踪。", "发现项目", { managing = false; nav.root(MainTab.Discover) }) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable fun InboxScreen(app: AppModel, nav: AppNavigation) {
    var all by rememberSaveable { mutableStateOf(false) }
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    val busy by app.busy.collectAsStateWithLifecycle()
    val model = screenModel("inbox") { PagedModel<Notification> { it.id } }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(all) {
        if (account != null) model.configure("inbox:$all") { page, refresh -> app.container.repository.notifications(all, page, refresh).let { Loaded(Page(it.data, it.data.size == 50), it.cachedAt, it.offline) } }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { ChoiceRow(listOf(false, true), all, { if (it) "全部" else "未读" }, { all = it }) }
        if (account == null) item { LoginRequired(nav) }
        else {
            item { SectionTitle("GitHub 通知", "刷新", model::refresh) }
            items(state.items, key = Notification::id) { notification ->
                Column {
                    ActionRow(notification.subject.title, "${notification.repository.fullName} · ${notification.subject.type} · ${dateLabel(notification.updatedAt)}") {
                        nav.target(ActivityLinks.notification(notification), app)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                        if (notification.unread) TextButton(enabled = "read:${notification.id}" !in busy, onClick = { app.action("read:${notification.id}", "已标记为已读") { app.container.repository.markRead(notification.id); model.refresh() } }) { Text("标为已读") }
                        TextButton(enabled = "done:${notification.id}" !in busy, onClick = { app.action("done:${notification.id}", "通知已完成") { app.container.repository.complete(notification.id); model.remove { it.id == notification.id } } }) { Text("完成") }
                    }
                }
            }
            listFeedback(state, model::refresh, model::more, "暂时没有通知")
        }
    }
}
