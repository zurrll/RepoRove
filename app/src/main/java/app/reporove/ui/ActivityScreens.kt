package app.reporove.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reporove.core.model.*

@Composable fun OrganizationsScreen(app: AppModel, nav: AppNavigation) {
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    if (account == null) { LoginRequired(nav); return }
    val model = screenModel("organizations:${account?.id}") { PagedModel<OrganizationMembership> { it.organization.id } }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(account?.id) {
        model.configure("organizations:${account?.id}") { page, refresh ->
            app.container.repository.organizations(page, refresh).let { Loaded(Page(it.data, it.data.size == 30), it.cachedAt, it.offline) }
        }
    }
    LazyColumn {
        item { SectionTitle("所属组织", "刷新", model::refresh) }
        items(state.items, key = { it.organization.id }) { membership ->
            Column {
                PersonRow(membership.organization) { nav.profile(membership.organization.login) }
                Text(if (membership.state == "pending") "等待接受邀请" else if (membership.role == "admin") "组织所有者" else "组织成员", Modifier.padding(start = 68.dp, bottom = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.error != null) item { Note("请确认凭据允许读取组织成员关系（classic PAT 的 read:org），并已获得该组织授权。") }
        listFeedback(state, model::refresh, model::more, "当前账号没有可见的组织成员关系")
    }
}

@Composable fun CheckScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, fullName: String, id: Long, suite: Boolean) {
    val context = LocalContext.current
    if (!suite) {
        val model = screenModel("check:$fullName:$id") { ResourceModel<CheckRun>() }
        val state by model.state.collectAsStateWithLifecycle()
        LaunchedEffect(fullName, id) { model.configure("$fullName:$id") { app.container.repository.checkRun(fullName, id, it) } }
        val check = (state as? LoadState.Ready)?.value
        val run = check?.detailsUrl?.let(GitHubLinks::parse) as? GitHubTarget.Run
        if (run != null && run.fullName.equals(fullName, true)) { WorkflowScreen(app, fullName, run.id, run.job); return }
        LazyColumn {
            item { Resource(state, model::refresh) { value ->
                SectionTitle(value.name, "刷新", model::refresh)
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusBadge(value.conclusion ?: value.status)
                    value.output?.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    MarkdownBody(listOfNotNull(value.output?.summary, value.output?.text).joinToString("\n\n").ifBlank { "此检查没有提供详细说明。" }, prefs, fullName = fullName)
                    value.detailsUrl?.takeIf { it.startsWith("https://") }?.let { url -> TextButton(onClick = { openUrl(context, url) }) { Text("在检查服务中查看") } }
                }
            } }
        }
        return
    }
    val runs = screenModel("suite-runs:$fullName:$id") { ResourceModel<WorkflowResponse>() }
    val runState by runs.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName, id) { runs.configure("$fullName:$id") { app.container.repository.suiteRuns(fullName, id, it) } }
    val matched = (runState as? LoadState.Ready)?.value?.runs
    if (matched?.size == 1) { WorkflowScreen(app, fullName, matched.single().id); return }
    val summary = screenModel("check-suite:$fullName:$id") { ResourceModel<CheckSuite>() }
    val summaryState by summary.state.collectAsStateWithLifecycle()
    val checks = screenModel("check-suite-items:$fullName:$id") { PagedModel<CheckRun> { it.id } }
    val checkState by checks.state.collectAsStateWithLifecycle()
    LaunchedEffect(runState) {
        if (runState is LoadState.Loading) return@LaunchedEffect
        summary.configure("$fullName:$id") { app.container.repository.checkSuite(fullName, id, it) }
        checks.configure("$fullName:$id") { page, refresh -> app.container.repository.checkRuns(fullName, id, page, refresh).let { Loaded(Page(it.data.runs, it.data.runs.size == 30), it.cachedAt, it.offline) } }
    }
    LazyColumn {
        item { if (runState is LoadState.Loading) LoadingIndicator() else Resource(summaryState, summary::refresh) { value -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(value.app?.name ?: "CI 检查", style = MaterialTheme.typography.titleLarge); StatusBadge(value.conclusion ?: value.status); Text(value.headSha.take(12), style = MaterialTheme.typography.bodyMedium) } } }
        if (runState is LoadState.Failed) item { Note("未能读取关联的 Actions 运行，可重试或查看检查详情。") ; TextButton(onClick = runs::refresh) { Text("重试关联运行") } }
        if (!matched.isNullOrEmpty()) {
            item { SectionTitle("关联运行") }
            items(matched, key = WorkflowRun::id) { run -> ActionRow(run.title.ifBlank { run.name ?: "运行 #${run.id}" }, "#${run.id} · ${run.conclusion ?: run.status}") { nav.run(fullName, run.id) } }
        }
        item { SectionTitle("检查项目") }
        items(checkState.items, key = CheckRun::id) { check -> ActionRow(check.name, check.conclusion ?: check.status) { nav.target(GitHubTarget.Check(fullName, check.id, false), app) } }
        listFeedback(checkState, checks::refresh, checks::more, "此检查套件没有可见的检查项目")
    }
}

@Composable fun TargetCommentScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, fullName: String, kind: String, id: Long) {
    val model = screenModel("comment:$fullName:$kind:$id") { ResourceModel<TargetComment>() }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName, kind, id) { model.configure("$fullName:$kind:$id") { app.container.repository.targetComment(fullName, kind, id, it) } }
    LazyColumn {
        item { Resource(state, model::refresh) { comment -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            comment.user?.let { AuthorLink(it) }
            comment.path?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            comment.diffHunk?.let { MarkdownBody("```diff\n$it\n```", prefs, fullName = fullName) }
            MarkdownBody(comment.body, prefs, fullName = fullName)
            TextButton(onClick = { nav.open(comment.htmlUrl.substringBefore('#'), app) {} }) { Text("查看完整讨论") }
        } } }
    }
}

@Composable fun TargetReview(app: AppModel, prefs: Preferences, fullName: String, number: Int, id: Long) {
    val model = screenModel("review:$fullName:$number:$id") { ResourceModel<TargetComment>() }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName, number, id) { model.configure("$fullName:$number:$id") { app.container.repository.pullReview(fullName, number, id, it) } }
    SectionTitle("此次评审")
    Resource(state, model::refresh) { review -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        review.user?.let { AuthorLink(it) }
        Text(when (review.state) { "APPROVED" -> "已批准"; "CHANGES_REQUESTED" -> "请求修改"; "COMMENTED" -> "已评论"; "DISMISSED" -> "已撤销"; else -> review.state.orEmpty() }, style = MaterialTheme.typography.labelLarge)
        MarkdownBody(review.body.ifBlank { "此评审没有附加说明。" }, prefs, fullName = fullName)
    } }
    HorizontalDivider()
}

@Composable fun ComparisonScreen(app: AppModel, nav: AppNavigation, fullName: String, range: String) {
    val model = screenModel("compare:$fullName:$range") { ResourceModel<Comparison>() }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(fullName, range) { model.configure("$fullName:$range") { app.container.repository.comparison(fullName, range, it) } }
    LazyColumn {
        item { Resource(state, model::refresh) { comparison ->
            SectionTitle("${comparison.totalCommits} 个提交", "刷新", model::refresh)
            if (comparison.commits.size < comparison.totalCommits) Note("当前显示 ${comparison.commits.size} 个提交。")
            comparison.commits.forEach { commit -> ActionRow(commit.commit.message.lineSequence().firstOrNull().orEmpty(), commit.sha.take(12)) { nav.commit(fullName, commit.sha) } }
            SectionTitle("文件变更")
            if (comparison.files.size >= 300) Note("GitHub 此接口最多返回 300 个文件；更大范围请在 GitHub 查看。")
            comparison.files.forEach { file -> Column(Modifier.padding(20.dp)) {
                Text(file.filename, style = MaterialTheme.typography.titleMedium)
                Text("+${file.additions} −${file.deletions}", color = LocalSemanticColors.current.success)
                file.patch?.let { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) } ?: Note("此文件没有可见的文本差异。")
            } }
        } }
    }
}

@Composable fun ActivityContextScreen(app: AppModel, nav: AppNavigation, fullName: String, title: String, kind: String, url: String) {
    val context = LocalContext.current
    LazyColumn {
        item { SectionTitle(title.ifBlank { "项目活动" }) }
        item { Note("$fullName · $kind") }
        item { Note(if (kind == "DeleteEvent") "此活动对应的分支或标签已删除。" else "这条活动暂时无法定位到具体对象。可从下列入口继续查看。") }
        if (kind.contains("Check", true) || kind.contains("Workflow", true)) item { ActionRow("查看运行记录") { nav.repo(fullName, RepoTab.Actions) } }
        if (kind.contains("Release", true)) item { ActionRow("查看发布版本") { nav.repo(fullName, RepoTab.Releases) } }
        if (kind.contains("Discussion", true)) item { ActionRow("查看讨论") { nav.discussions(fullName) } }
        item { ActionRow("查看仓库") { nav.repo(fullName) } }
        if (url.startsWith("https://github.com/")) item { ActionRow("在 GitHub 查看原始位置") { openUrl(context, url) } }
    }
}
