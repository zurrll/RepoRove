package app.reporove.ui

import android.content.ClipData

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import app.reporove.core.model.*
import app.reporove.data.GitHubRepository

@Composable fun RepositoryScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, fullName: String, initialTab: RepoTab) {
    val context = LocalContext.current
    var saveMenu by remember { mutableStateOf(false) }
    var selected by rememberSaveable(fullName) {
        mutableStateOf(if (initialTab == RepoTab.Overview && initialTab !in prefs.repoTabs) prefs.repoTabs.firstOrNull()?.name ?: "more" else initialTab.name)
    }
    val model = screenModel("repo:$fullName") { ResourceModel<Repository>() }
    val state by model.state.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    val star = screenModel("star:$fullName") { ResourceModel<Boolean>() }
    val starState by star.state.collectAsStateWithLifecycle()
    val busy by app.busy.collectAsStateWithLifecycle()
    LaunchedEffect(fullName) {
        model.configure(fullName) { app.container.repository.repository(fullName, it) }
        star.configure("$fullName:${account?.id}") { Loaded(app.container.repository.isStarred(fullName), System.currentTimeMillis()) }
    }
    Column(Modifier.fillMaxSize()) {
        Resource(state, { model.refresh() }) { repo ->
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AuthorLink(repo.owner)
                        Text(repo.name, style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = { nav.search("repo:${repo.fullName}") }) { Icon(Icons.Outlined.Search, "搜索此仓库") }
                    IconButton(onClick = { openUrl(context, repo.htmlUrl) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "在 GitHub 中打开") }
                }
                repo.description?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 3) }
                RepositoryMetadata(app, repo)
                if (repo.archived) Note("这个仓库已归档。")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isStarred = (starState as? LoadState.Ready)?.value == true
                    TextButton(enabled = "star:${repo.id}" !in busy && starState !is LoadState.Loading, onClick = {
                        if (account == null) nav.root(MainTab.Profile)
                        else if (starState is LoadState.Failed) star.refresh()
                        else app.action("star:${repo.id}", if (isStarred) "已取消 Star" else "已 Star") { app.container.repository.setStar(repo.fullName, !isStarred); star.refresh(); model.refresh() }
                    }) { Icon(if (isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder, null, Modifier.size(18.dp), tint = LocalSemanticColors.current.warning); Spacer(Modifier.width(6.dp)); Text(if (isStarred) "已 Star" else if (starState is LoadState.Failed) "重试 Star" else "Star") }
                    VerticalDivider(Modifier.height(20.dp).padding(horizontal = 8.dp))
                    Box {
                        TextButton(onClick = { saveMenu = true }) { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("保存到 App"); Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) }
                        DropdownMenu(saveMenu, { saveMenu = false }) {
                            val later = collections.later.any { it.id == repo.id }
                            val followed = collections.following.any { it.id == repo.id }
                            DropdownMenuItem(text = { Text("稍后看") }, leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null) }, trailingIcon = { if (later) Icon(Icons.Outlined.Check, "已保存") }, onClick = { saveMenu = false; app.action("later:${repo.id}", if (later) "已移出稍后看" else "已保存") { app.container.local.toggleLater(repo) } })
                            DropdownMenuItem(text = { Column { Text("App 内跟踪"); Text("聚合此仓库的公开活动", style = MaterialTheme.typography.bodySmall) } }, leadingIcon = { Icon(Icons.Outlined.Timeline, null) }, trailingIcon = { if (followed) Icon(Icons.Outlined.Check, "已跟踪") }, onClick = { saveMenu = false; app.action("follow:${repo.id}", if (followed) "已停止跟踪" else "已在 App 内跟踪") { app.container.local.toggleFollowing(repo) } })
                        }
                    }
                }
            }
            ChoiceRow(prefs.repoTabs.map { it.name } + "more", selected.takeIf { it in prefs.repoTabs.map(RepoTab::name) } ?: "more", { if (it == "more") "更多" else RepoTab.valueOf(it).label }, { selected = it })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f)) {
                when (selected) {
                    RepoTab.Overview.name -> OverviewScreen(app, prefs, nav, repo)
                    RepoTab.Code.name -> CodeScreen(app, prefs, nav, repo.fullName, repo.defaultBranch, "")
                    RepoTab.Issues.name -> if (repo.hasIssues) IssuesScreen(app, nav, repo.fullName, false) else EmptyState("这个仓库未启用 Issue")
                    RepoTab.Pulls.name -> IssuesScreen(app, nav, repo.fullName, true)
                    RepoTab.Releases.name -> ReleasesScreen(app, nav, repo.fullName)
                    RepoTab.Actions.name -> ActionsScreen(app, repo.fullName)
                    RepoTab.Commits.name -> CommitsScreen(app, nav, repo.fullName, repo.defaultBranch)
                    RepoTab.Discussions.name -> if (repo.hasDiscussions) DiscussionScreen(app, prefs, nav, repo.fullName, 0) else EmptyState("这个仓库未启用 Discussions")
                    RepoTab.Projects.name -> RepoFeatureScreen(app, prefs, nav, repo.fullName, "projects")
                    RepoTab.Wiki.name -> if (repo.hasWiki) WikiScreen(app, prefs, nav, repo.fullName) else EmptyState("这个仓库未启用 Wiki")
                    RepoTab.Contributors.name -> PeopleScreen(app, nav, repo.fullName, "contributors")
                    RepoTab.Security.name -> RepoFeatureScreen(app, prefs, nav, repo.fullName, "security")
                    else -> LazyColumn {
                        item { SectionTitle("更多栏目") }
                        items(RepoTab.entries.filterNot { it in prefs.repoTabs }) { tab -> ActionRow(tab.label) { selected = tab.name } }
                        item { ActionRow("定制仓库栏目", "所有仓库共用布局") { nav.settingsSection("repository") } }
                        item { SectionTitle("补充信息") }
                        item { ActionRow("统计", "仓库概况") { nav.repoFeature(repo.fullName, "stats") } }
                    }
                }
            }
        }
    }
}

@Composable private fun OverviewScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, repo: Repository) {
    val readme = screenModel("overview-readme:${repo.id}") { ResourceModel<Content>() }
    val release = screenModel("overview-release:${repo.id}") { ResourceModel<List<Release>>() }
    val readmeState by readme.state.collectAsStateWithLifecycle()
    val releaseState by release.state.collectAsStateWithLifecycle()
    LaunchedEffect(prefs.modules) {
        if (RepoModule.Readme in prefs.modules) readme.configure(repo.fullName) { app.container.repository.readme(repo.fullName, it) }
        if (RepoModule.Release in prefs.modules) release.configure(repo.fullName) { app.container.repository.releases(repo.fullName, refresh = it) }
    }
    LazyColumn(Modifier.testTag("overview-list")) {
        items(prefs.modules, key = { it.name }) { module -> when (module) {
            RepoModule.Status -> Column {
                SectionTitle("近况一览")
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("开放事项", style = MaterialTheme.typography.bodySmall); Text(repo.openItems.toString(), style = MaterialTheme.typography.headlineSmall); Text("Issue + PR", style = MaterialTheme.typography.bodySmall) }
                    Column { Text("最近推送", style = MaterialTheme.typography.bodySmall); Text(dateLabel(repo.pushedAt), style = MaterialTheme.typography.titleMedium); Text(repo.defaultBranch, style = MaterialTheme.typography.bodySmall) }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            RepoModule.Readme -> Column {
                SectionTitle("README", "阅读全文", { nav.readme(repo.fullName) })
                Resource(readmeState, { readme.refresh() }) { content ->
                    val text = remember(content) { runCatching { GitHubRepository.text(content) }.getOrDefault("请打开全文查看。") }
                    MarkdownBody(text, prefs, Modifier.fillMaxWidth(), repo.fullName, repo.defaultBranch, content.path, preview = true)
                    TextButton(onClick = { nav.readme(repo.fullName) }, Modifier.padding(horizontal = 20.dp)) { Text("阅读全文") }
                }
            }
            RepoModule.Release -> Column {
                SectionTitle("最新版本", "全部版本", { nav.repo(repo.fullName, RepoTab.Releases) })
                Resource(releaseState, { release.refresh() }) { releases ->
                    releases.firstOrNull()?.let { ActionRow(it.name ?: it.tag, "${it.tag} · ${dateLabel(it.publishedAt)}${if (it.prerelease) " · 预发布" else ""}") { nav.release(repo.fullName, it.id) } } ?: Note("这个仓库还没有发布版本。")
                }
            }
            RepoModule.Activity -> Column { SectionTitle("最近活动"); ActionRow("查看代码与提交", "主分支 ${repo.defaultBranch}") { nav.code(repo.fullName, repo.defaultBranch) }; ActionRow("查看构建状态", onClick = { nav.repo(repo.fullName, RepoTab.Actions) }) }
        } }
        if (prefs.modules.isEmpty()) item { EmptyState("仓库首页由你决定", action = "选择概览模块", onAction = { nav.settingsSection("repository") }) }
    }
}

@Composable fun ReadmeScreen(app: AppModel, prefs: Preferences, fullName: String) {
    val model = screenModel("readme:$fullName") { ResourceModel<Pair<Content, Repository>>() }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName) { model.configure(fullName) { refresh -> val content = app.container.repository.readme(fullName, refresh); val repo = app.container.repository.repository(fullName).data; Loaded(content.data to repo, content.cachedAt, content.offline) } }
    Column(Modifier.fillMaxSize()) {
        SectionTitle(fullName, "刷新", model::refresh)
        Resource(state, model::refresh) { (content, repo) ->
            val text = remember(content) { runCatching { GitHubRepository.text(content) } }
            text.getOrNull()?.let { MarkdownBody(it, prefs, Modifier.fillMaxSize(), fullName, repo.defaultBranch, content.path, fill = true) } ?: EmptyState("无法直接预览", text.exceptionOrNull()?.message)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun CodeScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, fullName: String, ref: String, path: String, anchor: String = "") {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    var rendered by rememberSaveable(path) { mutableStateOf(path.substringAfterLast('.').lowercase() in listOf("md", "markdown", "mdown")) }
    var wrap by rememberSaveable { mutableStateOf(false) }
    var branchMenu by remember { mutableStateOf(false) }
    var fileMenu by remember { mutableStateOf(false) }
    val model = screenModel("code:$fullName:$ref:$path") { ResourceModel<List<Content>>() }
    val branches = screenModel("branches:$fullName") { ResourceModel<List<Branch>>() }
    val state by model.state.collectAsStateWithLifecycle()
    val branchState by branches.state.collectAsStateWithLifecycle()
    val contents = (state as? LoadState.Ready)?.value
    val file = contents?.singleOrNull()?.takeIf { it.type != "dir" && it.path == path }
    LaunchedEffect(fullName, ref, path) { model.configure("$fullName:$ref:$path") { app.container.repository.contents(fullName, path, ref, it) }; branches.configure(fullName) { app.container.repository.branches(fullName) } }
    if (file != null && rendered) {
        val text = remember(file) { runCatching { GitHubRepository.text(file) } }
        Column(Modifier.fillMaxSize()) {
            SectionTitle(file.name, "查看源码", { rendered = false })
            text.getOrNull()?.let { MarkdownBody(it, prefs, Modifier.fillMaxWidth().weight(1f), fullName, ref, path, fill = true, initialAnchor = anchor) }
                ?: EmptyState("无法直接预览", text.exceptionOrNull()?.message)
        }
        return
    }
    LazyColumn {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box { TextButton(onClick = { branchMenu = true }) { Text(ref); Icon(Icons.Outlined.ArrowDropDown, "选择分支") }; DropdownMenu(branchMenu, { branchMenu = false }) {
                    (branchState as? LoadState.Ready)?.value?.forEach { branch -> DropdownMenuItem(text = { Text(branch.name) }, onClick = { branchMenu = false; nav.code(fullName, branch.name) }) }
                    if (branchState is LoadState.Failed) DropdownMenuItem(text = { Text("重试加载分支") }, onClick = { branches.refresh() })
                } }
                Spacer(Modifier.weight(1f)); IconButton(onClick = { model.refresh() }) { Icon(Icons.Outlined.Refresh, "刷新代码") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { nav.code(fullName, ref) }, enabled = path.isNotEmpty()) { Text("根目录") }
                path.split('/').filter(String::isNotBlank).forEachIndexed { index, part -> Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = { nav.code(fullName, ref, path.split('/').take(index + 1).joinToString("/")) }) { Text(part) } }
                if (path.isNotEmpty()) TextButton(onClick = { nav.code(fullName, ref, path.substringBeforeLast('/', "")) }) { Text("上一级") }
            }
        }
        if (contents != null && file == null) {
            if ((state as? LoadState.Ready)?.offline == true) item { Note("当前展示缓存内容，联网后可刷新。") }
            items(contents.sortedWith(compareBy<Content> { it.type != "dir" }.thenBy { it.name.lowercase() }), key = Content::path) { entry ->
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { nav.code(fullName, ref, entry.path) }, onLongClick = { app.action("copy-path", "已复制路径") { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(entry.name, entry.path))) } }).heightIn(min = 48.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (entry.type == "dir") Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile, null, Modifier.size(20.dp), tint = if (entry.type == "dir") LocalSemanticColors.current.link else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(entry.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    if (entry.type != "dir") Text(bytesLabel(entry.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (contents.isEmpty()) item { Note("这个目录没有文件。") }
        } else item { Resource(state, { model.refresh() }) {
            if (file != null) {
                val text = remember(file) { runCatching { GitHubRepository.text(file) } }
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(file.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Box {
                            IconButton(onClick = { fileMenu = true }) { Icon(Icons.Outlined.MoreHoriz, "文件操作") }
                            DropdownMenu(fileMenu, { fileMenu = false }) {
                                if (path.substringAfterLast('.').lowercase() in listOf("md", "markdown", "mdown")) DropdownMenuItem(text = { Text("阅读模式") }, onClick = { rendered = true; fileMenu = false })
                                DropdownMenuItem(text = { Text(if (wrap) "关闭自动换行" else "自动换行") }, onClick = { wrap = !wrap; fileMenu = false })
                                DropdownMenuItem(text = { Text("复制内容") }, enabled = text.isSuccess, onClick = { fileMenu = false; app.action("copy-code", "已复制文件内容") { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(file.name, text.getOrDefault("")))) } })
                                DropdownMenuItem(text = { Text("在 GitHub 打开") }, onClick = { fileMenu = false; file.htmlUrl?.let { openUrl(context, it) } })
                            }
                        }
                    }
                    text.getOrNull()?.let { content ->
                        val preview = remember(content) { content.take(200_000).lineSequence().take(5000).mapIndexed { index, line -> "${index + 1}  $line" }.joinToString("\n") }
                        if (content.length > 200_000 || content.lineSequence().drop(5000).any()) Note("文件较长，显示前 5000 行或 20 万字符；复制按钮仍可复制完整内容。")
                        if (rendered) MarkdownBody(content, prefs, Modifier.fillMaxWidth(), fullName, ref, path)
                        else Text(preview, modifier = Modifier.fillMaxWidth().padding(20.dp).then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), softWrap = wrap)
                    } ?: EmptyState("无法直接预览", text.exceptionOrNull()?.message)
                }
            }
        } }
    }
}

@Composable private fun IssuesScreen(app: AppModel, nav: AppNavigation, fullName: String, pulls: Boolean) {
    var status by rememberSaveable { mutableStateOf("open") }
    val model = screenModel("issues:$fullName:$pulls") { PagedModel<Issue> { it.id } }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(status) { model.configure("$fullName:$pulls:$status") { page, refresh -> app.container.repository.issues(fullName, pulls, status, page, refresh).let { loaded -> Loaded(Page(loaded.data.filter { pulls || it.pullRequest == null }, loaded.data.size == 30), loaded.cachedAt, loaded.offline) } } }
    LazyColumn {
        item { ChoiceRow(listOf("open", "closed", "all"), status, { when (it) { "open" -> "开放"; "closed" -> "已关闭"; else -> "全部" } }, { status = it }) }
        item { SectionTitle(if (pulls) "Pull Requests" else "Issues", "刷新", model::refresh) }
        items(state.items, key = Issue::id) { issue -> IssueRow(issue, pulls) { nav.thread(fullName, issue.number, pulls) } }
        listFeedback(state, model::refresh, model::more, "没有符合条件的事项")
    }
}

@Composable fun ThreadScreen(app: AppModel, prefs: Preferences, fullName: String, number: Int, pull: Boolean, anchor: String = "") {
    val context = LocalContext.current
    val nav = LocalAppNavigation.current
    val model = screenModel("thread:$fullName:$number:$pull") { ResourceModel<Issue>() }
    val comments = screenModel("comments:$fullName:$number") { PagedModel<Comment> { it.id } }
    val state by model.state.collectAsStateWithLifecycle()
    val commentState by comments.state.collectAsStateWithLifecycle()
    val reviewId = anchor.takeIf { pull && it.startsWith("pullrequestreview-") }?.removePrefix("pullrequestreview-")?.toLongOrNull()
    LaunchedEffect(fullName, number, pull) { model.configure("$fullName:$number:$pull") { app.container.repository.thread(fullName, number, pull, it) }; comments.configure("$fullName:$number") { page, refresh -> app.container.repository.comments(fullName, number, page, refresh).let { Loaded(Page(it.data, it.data.size == 30), it.cachedAt, it.offline) } } }
    LazyColumn {
        if (reviewId != null) item { TargetReview(app, prefs, fullName, number, reviewId) }
        item { Resource(state, { model.refresh() }) { issue ->
            Column(Modifier.padding(20.dp)) {
                Text(issue.title, style = MaterialTheme.typography.titleLarge)
                StatusBadge(issue.status, pull)
                Text("$fullName #$number", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
                AuthorLink(issue.user)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { issue.labels.forEach { LabelBadge(it) } }
                MarkdownBody(issue.body ?: "未提供正文。", prefs, Modifier.fillMaxWidth(), fullName)
                if (pull) TextButton(onClick = { nav.pullFiles(fullName, number) }) { Text("查看变更") }
                TextButton(onClick = { openUrl(context, issue.htmlUrl) }) { Text("在 GitHub 打开") }
            }
        } }
        item { SectionTitle("讨论") }
        items(commentState.items, key = Comment::id) { comment -> Column(Modifier.padding(20.dp)) { AuthorLink(comment.user); Text(dateLabel(comment.createdAt), style = MaterialTheme.typography.bodySmall); MarkdownBody(comment.body, prefs, Modifier.fillMaxWidth().padding(top = 10.dp), fullName) }; HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        listFeedback(commentState, comments::refresh, comments::more, "还没有评论")
    }
}

@Composable private fun ReleasesScreen(app: AppModel, nav: AppNavigation, fullName: String) {
    val model = screenModel("releases:$fullName") { PagedModel<Release> { it.id } }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName) { model.configure(fullName) { page, refresh -> app.container.repository.releases(fullName, page, refresh).let { Loaded(Page(it.data, it.data.size == 30), it.cachedAt, it.offline) } } }
    LazyColumn {
        item { SectionTitle("发布版本", "刷新", model::refresh) }
        items(state.items, key = Release::id) { release ->
            Column {
                ActionRow(release.name ?: release.tag, "${release.tag} · ${dateLabel(release.publishedAt)}${if (release.prerelease) " · 预发布" else ""}") { nav.release(fullName, release.id) }
                TextButton(onClick = { nav.release(fullName, release.id) }, Modifier.padding(horizontal = 20.dp)) { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("下载文件"); Text(" · ${release.assets.size} 个发布附件", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        listFeedback(state, model::refresh, model::more, "这个仓库尚未发布版本")
    }
}

@Composable fun ReleaseScreen(app: AppModel, prefs: Preferences, fullName: String, id: Long) {
    val context = LocalContext.current
    val model = screenModel("release:$fullName:$id") { ResourceModel<Pair<Release, Boolean>>() }
    val state by model.state.collectAsStateWithLifecycle()
    val busy by app.busy.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ready = state as? LoadState.Ready
    LaunchedEffect(fullName, id) { model.configure("$fullName:$id") { val release = app.container.repository.release(fullName, id, it); val repo = app.container.repository.repository(fullName).data; Loaded(release.data to repo.isPrivate, release.cachedAt, release.offline) } }
    LazyColumn(state = list, modifier = Modifier.testTag("release-list")) {
        if (ready == null) item { Resource(state, model::refresh) {} }
        else {
            val (release, isPrivate) = ready.value
            item(key = "header") {
                if (ready.offline) Note("当前展示缓存内容，联网后可刷新。")
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(release.name ?: release.tag, style = MaterialTheme.typography.titleLarge)
                    Text("${release.tag} · ${dateLabel(release.publishedAt)}${if (release.prerelease) " · 预发布" else ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { scope.launch { list.animateScrollToItem(2) } }) { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("跳到下载") }
                }
            }
            item(key = "notes") {
                SectionTitle("版本说明", "刷新", model::refresh)
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    release.author?.let { AuthorLink(it) }
                    MarkdownBody(release.body?.takeIf(String::isNotBlank) ?: "未提供版本说明。", prefs, Modifier.fillMaxWidth(), fullName)
                }
            }
            item(key = "downloads") {
                SectionTitle("下载文件", "刷新", model::refresh)
                if (release.assets.isEmpty()) Note(if (release.zipballUrl != null || release.tarballUrl != null) "没有发布附件，下方可下载源码归档。" else "这个版本没有可下载的发布附件。")
                release.assets.forEach { asset ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { Text(asset.name, style = MaterialTheme.typography.bodyLarge); Text(bytesLabel(asset.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        FilledTonalIconButton(enabled = "download:${asset.id}" !in busy, onClick = {
                            if (isPrivate) openUrl(context, release.htmlUrl)
                            else app.action("download:${asset.id}", "已加入下载，可在项目库查看进度") { app.container.downloads.enqueue(fullName, asset) }
                        }) { Icon(Icons.Outlined.Download, if (isPrivate) "在 GitHub 下载 ${asset.name}" else "下载 ${asset.name}") }
                    }
                }
                if (release.zipballUrl != null || release.tarballUrl != null) {
                    Text("源码归档", Modifier.padding(start = 20.dp, top = 12.dp), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.padding(horizontal = 12.dp)) {
                        if (release.zipballUrl != null) TextButton(onClick = { if (isPrivate) openUrl(context, release.htmlUrl) else app.action("archive:$id:zip", "源码已加入下载") { app.container.downloads.enqueueArchive(fullName, release, false) } }) { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("源码 ZIP") }
                        if (release.tarballUrl != null) TextButton(onClick = { if (isPrivate) openUrl(context, release.htmlUrl) else app.action("archive:$id:tar", "源码已加入下载") { app.container.downloads.enqueueArchive(fullName, release, true) } }) { Text("源码 TAR") }
                    }
                }
                if (isPrivate) Note("私有文件需在 GitHub 完成下载。")
                TextButton(onClick = { openUrl(context, release.htmlUrl) }, Modifier.padding(horizontal = 20.dp)) { Text("在 GitHub 打开") }
            }
        }
    }
}

@Composable fun ActionsScreen(app: AppModel, fullName: String) {
    val context = LocalContext.current
    val nav = LocalAppNavigation.current
    val model = screenModel("actions:$fullName") { PagedModel<WorkflowRun> { it.id } }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName) { model.configure(fullName) { page, refresh -> app.container.repository.workflows(fullName, page, refresh).let { Loaded(Page(it.data.runs, it.data.runs.size == 30), it.cachedAt, it.offline) } } }
    LazyColumn {
        item { SectionTitle("工作流运行", "刷新", model::refresh) }
        items(state.items, key = WorkflowRun::id) { run -> Column { Row(Modifier.padding(start = 20.dp, top = 10.dp)) { StatusBadge(run.conclusion ?: run.status) }; ActionRow(run.title.ifBlank { run.name ?: "Workflow" }, "${run.branch.orEmpty()} · ${dateLabel(run.updatedAt)}") { nav.run(fullName, run.id) } } }
        listFeedback(state, model::refresh, model::more, "没有工作流运行记录")
    }
}
