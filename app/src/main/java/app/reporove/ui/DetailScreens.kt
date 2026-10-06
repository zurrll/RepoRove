package app.reporove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reporove.core.model.*
import kotlinx.serialization.json.*

@Composable fun CommitScreen(app: AppModel, prefs: Preferences, fullName: String, sha: String) {
    val model = screenModel("commit:$fullName:$sha") { ResourceModel<CommitDetail>() }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName, sha) { model.configure("$fullName:$sha") { app.container.repository.commit(fullName, sha, it) } }
    LazyColumn {
        item { Resource(state, model::refresh) { commit ->
            SectionTitle(commit.sha.take(7), "刷新", model::refresh)
            commit.author?.let { AuthorLink(it) }
            MarkdownBody(commit.commit.message, prefs, fullName = fullName)
            commit.files.forEach { DiffFile(it) }
        } }
    }
}

@Composable fun CommitsScreen(app: AppModel, nav: AppNavigation, fullName: String, ref: String) {
    val model = screenModel("commits:$fullName:$ref") { PagedModel<ShortCommit> { it.sha } }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName, ref) { model.configure("$fullName:$ref") { page, refresh -> app.container.repository.commits(fullName, ref, page, refresh).let { Loaded(Page(it.data, it.data.size == 30), it.cachedAt, it.offline) } } }
    LazyColumn {
        item { SectionTitle("提交 · $ref", "刷新", model::refresh) }
        items(state.items, key = ShortCommit::sha) { commit -> Column { commit.author?.let { AuthorLink(it) }; ActionRow(commit.commit.message.lineSequence().firstOrNull().orEmpty(), "${commit.sha.take(7)} · ${dateLabel(commit.commit.author?.date)}") { nav.commit(fullName, commit.sha) } } }
        listFeedback(state, model::refresh, model::more)
    }
}

@Composable fun DiffFile(file: CommitFile) {
    var expanded by rememberSaveable(file.filename) { mutableStateOf(false) }
    val semantic = LocalSemanticColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        ActionRow(file.filename, "${file.status} · +${file.additions} / −${file.deletions} · ${if (expanded) "收起" else "展开差异"}") { expanded = !expanded }
        if (expanded) {
            if (file.patch == null) Note("GitHub 未提供此文件的文本差异（可能为二进制文件或差异过大）。")
            else Column(Modifier.fillMaxWidth().background(semantic.code).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                file.patch.lineSequence().take(5000).forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = when { line.startsWith('+') -> semantic.success; line.startsWith('-') -> semantic.danger; line.startsWith("@@") -> semantic.link; else -> MaterialTheme.colorScheme.onSurface }, softWrap = false) }
                if (file.patch.lineSequence().drop(5000).any()) Note("差异较长，当前预览前 5000 行。")
            }
        }
    }
}

@Composable fun PullFilesScreen(app: AppModel, fullName: String, number: Int) {
    val model = screenModel("pull-files:$fullName:$number") { PagedModel<CommitFile> { it.filename } }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName, number) { model.configure("$fullName:$number") { page, refresh -> app.container.repository.pullFiles(fullName, number, page, refresh).let { Loaded(Page(it.data, it.data.size == 30), it.cachedAt, it.offline) } } }
    LazyColumn {
        item { SectionTitle("$fullName #$number · 变更", "刷新", model::refresh) }
        items(state.items, key = CommitFile::filename) { DiffFile(it) }
        listFeedback(state, model::refresh, model::more, "暂无文件变更")
    }
}

@Composable fun WorkflowScreen(app: AppModel, fullName: String, id: Long, focusJob: Long = 0) {
    val model = screenModel("run:$fullName:$id") { ResourceModel<WorkflowRun>() }
    val state by model.state.collectAsStateWithLifecycle()
    val jobs = screenModel("jobs:$fullName:$id") { PagedModel<WorkflowJob> { it.id } }
    val jobState by jobs.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName, id) {
        model.configure("$fullName:$id") { app.container.repository.run(fullName, id, it) }
        jobs.configure("$fullName:$id") { page, refresh -> app.container.repository.jobs(fullName, id, page, refresh).let { Loaded(Page(it.data.jobs, it.data.jobs.size == 30), it.cachedAt, it.offline) } }
    }
    val list = rememberLazyListState()
    var focused by remember(fullName, id, focusJob) { mutableStateOf(false) }
    LaunchedEffect(jobState, focusJob) {
        if (focusJob == 0L || focused || jobState.loading || jobState.error != null) return@LaunchedEffect
        val index = jobState.items.indexOfFirst { it.id == focusJob }
        if (index >= 0) { list.scrollToItem(index + 1); focused = true }
        else if (jobState.hasMore) jobs.more()
    }
    LazyColumn(state = list) {
        item { Resource(state, model::refresh) { run -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(run.title.ifBlank { run.name ?: "Workflow" }, style = MaterialTheme.typography.titleLarge); StatusBadge(run.conclusion ?: run.status); Text("${run.branch.orEmpty()} · ${dateLabel(run.updatedAt)}", style = MaterialTheme.typography.bodySmall) } } }
        if (focusJob > 0 && !jobState.loading && !jobState.hasMore && jobState.error == null && jobState.items.none { it.id == focusJob }) item { Note("此运行中已找不到原来指向的 Job，下面显示当前可见的 Job。") }
        items(jobState.items, key = WorkflowJob::id) { job -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(job.name, style = MaterialTheme.typography.titleMedium); StatusBadge(job.conclusion ?: job.status); job.steps.forEach { step -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusBadge(step.conclusion ?: step.status); Text(step.name, style = MaterialTheme.typography.bodyMedium) } } } }
        listFeedback(jobState, jobs::refresh, jobs::more, "暂无 Job")
    }
}

@Composable fun DiscussionScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, fullName: String, number: Int) {
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    if (account == null) { LoginRequired(nav); return }
    if (number == 0) {
        val model = screenModel("discussions:$fullName") { PagedModel<Discussion> { it.id } }
        val state by model.state.collectAsStateWithLifecycle()
        LaunchedEffect(fullName) { model.configure(fullName) { page, refresh -> app.container.repository.searchDiscussions("repo:$fullName", page, refresh) } }
        LazyColumn { item { SectionTitle("讨论", "刷新", model::refresh) }; items(state.items, key = Discussion::id) { discussion -> Column { discussion.author?.let { AuthorLink(it) }; ActionRow(discussion.title, discussion.category + if (discussion.answered) " · 已回答" else "") { nav.discussions(fullName, discussion.number) } } }; listFeedback(state, model::refresh, model::more) }
    } else {
        val model = screenModel("discussion:$fullName:$number") { ResourceModel<Discussion>() }
        val state by model.state.collectAsStateWithLifecycle()
        LaunchedEffect(fullName, number) { model.configure("$fullName:$number") { app.container.repository.discussion(fullName, number, it) } }
        LazyColumn { item { Resource(state, model::refresh) { discussion ->
            SectionTitle(discussion.title, "刷新", model::refresh)
            discussion.author?.let { AuthorLink(it) }
            Note("${discussion.category} · #${discussion.number}" + if (discussion.answered) " · 已回答" else "")
            MarkdownBody(discussion.body, prefs, fullName = fullName)
            SectionTitle("评论")
            discussion.comments.forEach { comment -> AuthorLink(comment.user); MarkdownBody(comment.body, prefs, fullName = fullName) }
            if (discussion.comments.size == 20) Note("当前显示前 20 条评论。")
        } } }
    }
}

@Composable fun RepoFeatureScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, fullName: String, feature: String) {
    if (feature == "stats") {
        val model = screenModel("statistics:$fullName") { ResourceModel<Repository>() }
        val state by model.state.collectAsStateWithLifecycle()
        LaunchedEffect(fullName) { model.configure(fullName) { app.container.repository.repository(fullName, it) } }
        LazyColumn { item { Resource(state, model::refresh) { repo ->
            SectionTitle("仓库概况", "刷新", model::refresh)
            Note("${repo.fullName}\nStar：${repo.stars}\nFork：${repo.forks}\n开放的 Issue / PR：${repo.openItems}\n最近推送：${dateLabel(repo.pushedAt)}\n默认分支：${repo.defaultBranch}")
            ActionRow("贡献者") { nav.contributors(fullName) }
            ActionRow("提交记录") { nav.commits(fullName, repo.defaultBranch) }
        } } }
        return
    }
    if (feature == "wiki") { WikiScreen(app, prefs, nav, fullName); return }
    if (feature == "security") {
        val model = screenModel("security:$fullName") { PagedModel<SecurityAdvisory> { it.id } }
        val state by model.state.collectAsStateWithLifecycle()
        LaunchedEffect(fullName) { model.configure(fullName) { page, refresh -> app.container.repository.advisories(fullName, page, refresh) } }
        LazyColumn { item { SectionTitle("公开安全公告", "刷新", model::refresh) }; items(state.items, key = SecurityAdvisory::id) { advisory -> SectionTitle(advisory.summary); Note("${advisory.id} · ${advisory.severity.orEmpty()}"); advisory.description?.let { MarkdownBody(it, prefs, fullName = fullName) } }; listFeedback(state, model::refresh, model::more, "暂无可见安全公告") }
    } else {
        val account by app.container.repository.account.collectAsStateWithLifecycle()
        if (account == null) { LoginRequired(nav); return }
        val model = screenModel("projects:$fullName") { ResourceModel<JsonArray>() }
        val state by model.state.collectAsStateWithLifecycle()
        LaunchedEffect(fullName) { model.configure(fullName) { app.container.repository.projects(fullName, it) } }
        LazyColumn { item { Resource(state, model::refresh) { projects ->
            SectionTitle("关联的项目", "刷新", model::refresh)
            if (projects.isEmpty()) Note("暂无当前凭据可见的项目。")
            projects.forEach { value -> val project = value.jsonObject
                SectionTitle(project["title"]?.jsonPrimitive?.content.orEmpty())
                project["shortDescription"]?.jsonPrimitive?.contentOrNull?.let { Note(it) }
                project["readme"]?.jsonPrimitive?.contentOrNull?.let { MarkdownBody(it, prefs, fullName = fullName) }
                Note(if (project["closed"]?.jsonPrimitive?.booleanOrNull == true) "已关闭" else "进行中")
            }
            if (projects.size == 50) Note("当前显示前 50 个关联项目。")
        } } }
    }
}

@Composable fun WikiScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, fullName: String, slug: String = "") {
    val model = screenModel("wiki:$fullName:$slug") { ResourceModel<WikiDocument>() }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName, slug) { model.configure("$fullName:$slug") { app.container.repository.wiki(fullName, slug, it) } }
    LazyColumn { item { Resource(state, model::refresh) { document ->
        SectionTitle(document.title, "刷新", model::refresh)
        MarkdownBody("", prefs, Modifier.fillMaxWidth(), renderedHtml = document.html)
        SectionTitle("Wiki 页面")
        document.pages.forEach { page -> ActionRow(page.title) { nav.wiki(fullName, page.slug) } }
    } } }
}
