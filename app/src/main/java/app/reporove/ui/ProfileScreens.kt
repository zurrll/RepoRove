package app.reporove.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reporove.core.model.*
import app.reporove.data.GitHubRepository
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

@Composable fun UserProfileScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, login: String, own: Boolean = false) {
    val context = LocalContext.current
    val model = screenModel("profile:$login") { ResourceModel<User>() }
    val profile by model.state.collectAsStateWithLifecycle()
    val readme = screenModel("profile-readme:$login") { ResourceModel<Pair<Content, Repository>?>() }
    val readmeState by readme.state.collectAsStateWithLifecycle()
    val pins = screenModel("pins:$login") { ResourceModel<List<Repository>>() }
    val pinState by pins.state.collectAsStateWithLifecycle()
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    val isOwn = own || account?.login.equals(login, ignoreCase = true)
    var pinsExpanded by rememberSaveable(login) { mutableStateOf(false) }
    val repoCount = screenModel("org-count:$login") { ResourceModel<Int>() }
    val repoCountState by repoCount.state.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    LaunchedEffect(login) {
        model.configure(login) { app.container.repository.profile(login, it) }
    }
    val user = (profile as? LoadState.Ready)?.value
    LaunchedEffect(user?.type) {
        if (user != null) readme.configure("$login:${user.type}") { refresh ->
            try {
                val fullName = if (user.type == "Organization") "$login/.github" else "$login/$login"
                val repo = app.container.repository.repository(fullName, refresh).data
                val content = if (user.type == "Organization") app.container.repository.contents(fullName, "profile/README.md", repo.defaultBranch, refresh).let { Loaded(it.data.first(), it.cachedAt, it.offline) }
                    else app.container.repository.readme(fullName, refresh)
                Loaded(content.data to repo, content.cachedAt, content.offline)
            } catch (e: HttpException) { if (e.code() == 404) Loaded(null, System.currentTimeMillis()) else throw e }
        }
    }
    LaunchedEffect(user?.type, account?.id) { if (user != null && account != null) pins.configure("$login:${account?.id}:${user.type}") { app.container.repository.profilePins(login, user.type == "Organization", it) } }
    LaunchedEffect(user?.type, account?.id) { if (user?.type == "Organization" && account != null) repoCount.configure("$login:${account?.id}") { app.container.repository.organizationRepositoryCount(login, it) } }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Resource(profile, model::refresh) { person ->
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                    UserAvatar(person, Modifier.size(72.dp))
                    IconButton(onClick = { nav.search(if (person.type == "Organization") "org:$login" else "user:$login") }) { Icon(Icons.Outlined.Search, "搜索此主页的内容") }
                }
                Text(person.name ?: person.login, style = MaterialTheme.typography.headlineSmall)
                Text("@${person.login}" + if (person.type == "Organization") " · 组织" else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                person.bio?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                listOfNotNull(person.location, person.company, person.createdAt?.let { "加入于 ${dateLabel(it)}" }).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                person.blog?.takeIf(String::isNotBlank)?.let { blog -> TextButton(onClick = { nav.open(if (blog.startsWith("http")) blog else "https://$blog", app) { openUrl(context, it) } }) { Text(blog) } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { nav.userRepos(login) }) { Text(if (person.type == "Organization" && account != null) (repoCountState as? LoadState.Ready)?.value?.let { "$it 可访问仓库" } ?: "仓库" else "${person.publicRepos?.toString() ?: "—"} 公开仓库") }
                    if (person.type != "Organization") {
                        TextButton(onClick = { nav.people(login, "followers") }) { Text("${person.followers?.toString() ?: "—"} 关注者") }
                        TextButton(onClick = { nav.people(login, "following") }) { Text("${person.following?.toString() ?: "—"} 关注") }
                    }
                }
                if (isOwn) TextButton(onClick = nav::organizations) { Icon(Icons.Outlined.Groups, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("我的组织") }
            }
        } }
        if (account != null && user != null) {
            item { SectionTitle("置顶项目" + ((pinState as? LoadState.Ready)?.value?.size?.let { " · $it" } ?: ""), if (pinsExpanded) "收起" else "展开", { pinsExpanded = !pinsExpanded }) }
            if (pinsExpanded) when (val value = pinState) {
                is LoadState.Ready -> { items(value.value, key = Repository::id) { repo -> ProjectRow(app, nav, prefs, repo, collections) }; if (value.value.isEmpty()) item { Note("没有公开置顶的仓库。") } }
                is LoadState.Failed -> item { Note("暂时无法读取置顶项目：${value.message}"); TextButton(onClick = pins::refresh, Modifier.padding(horizontal = 20.dp)) { Text("重试置顶项目") } }
                LoadState.Loading -> item { LoadingIndicator() }
            }
        } else if (user != null) item { SectionTitle("置顶项目", if (pinsExpanded) "收起" else "展开", { pinsExpanded = !pinsExpanded }); if (pinsExpanded) Note("登录后可读取此主页的置顶项目。") }
        if (readmeState is LoadState.Ready && (readmeState as LoadState.Ready).value != null) item {
            val (content, repo) = (readmeState as LoadState.Ready).value!!
            SectionTitle(if (user?.type == "Organization") "组织介绍" else "个人介绍")
            val text = remember(content) { runCatching { GitHubRepository.text(content) }.getOrDefault("") }
            MarkdownBody(text, prefs, Modifier.fillMaxWidth(), repo.fullName, repo.defaultBranch, content.path)
        }
        if (user?.type == "Organization") item { ActionRow("公开成员") { nav.people(login, "members") } }
        else if (!isOwn && user != null) item { ActionRow("Star 的项目", "公开的 Star") { nav.userRepos(login, true) } }
        item { ActionRow("在 GitHub 打开") { openUrl(context, user?.htmlUrl?.takeIf(String::isNotBlank) ?: "https://github.com/$login") } }
        if (isOwn) item { LogoutRow(app) }
    }
}

@Composable fun LogoutRow(app: AppModel) {
    var show by remember { mutableStateOf(false) }
    ActionRow("退出账号") { show = true }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text("退出 GitHub？") }, text = { Text("清除凭据、缓存和收藏中的私有仓库。保留公开收藏和设置。") }, confirmButton = { TextButton(onClick = { show = false; app.action("logout") { app.container.repository.logout() } }) { Text("退出") } }, dismissButton = { TextButton(onClick = { show = false }) { Text("取消") } })
}

@Composable fun ProjectRow(app: AppModel, nav: AppNavigation, prefs: Preferences, repo: Repository, collections: CollectionState) {
    RepoRow(repo, prefs, collections.later.any { it.id == repo.id }, { nav.repo(repo.fullName) }, { app.action("later:${repo.id}") { app.container.local.toggleLater(repo) } })
}

@Composable fun UserRepositoriesScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, login: String, stars: Boolean) {
    val model = screenModel("user-repos:$login:$stars") { PagedModel<Repository> { it.id } }
    val state by model.state.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    val revision by app.container.repository.revision.collectAsStateWithLifecycle()
    LaunchedEffect(login, stars, revision) { model.configure("$login:$stars:$revision") { page, refresh ->
        val result = if (stars) app.container.repository.profileStars(login, page, refresh) else {
            val user = app.container.repository.profile(login).data
            app.container.repository.profileRepositories(login, user.type == "Organization", page, refresh)
        }
        Loaded(Page(result.data, result.data.size == 30), result.cachedAt, result.offline)
    } }
    LazyColumn {
        item { SectionTitle(if (stars) "$login 的 Star" else "$login 的仓库", "刷新", model::refresh) }
        if (!stars) item { Note("显示 GitHub 允许当前账号访问的仓库；组织列表包含已获授权的私有仓库。") }
        items(state.items, key = Repository::id) { ProjectRow(app, nav, prefs, it, collections) }
        listFeedback(state, model::refresh, model::more, "暂无可访问项目")
    }
}

@Composable fun PeopleScreen(app: AppModel, nav: AppNavigation, login: String, relation: String) {
    val model = screenModel("people:$login:$relation") { PagedModel<User> { it.login } }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(login, relation) { model.configure("$login:$relation") { page, refresh ->
        val result = if (relation == "contributors") app.container.repository.contributors(login, page, refresh) else app.container.repository.people(login, relation, page, refresh)
        Loaded(Page(result.data, result.data.size == 30), result.cachedAt, result.offline)
    } }
    LazyColumn {
        item { SectionTitle(when (relation) { "followers" -> "关注者"; "following" -> "正在关注"; "members" -> "公开成员"; else -> "贡献者" }, "刷新", model::refresh) }
        items(state.items, key = User::login) { PersonRow(it) { nav.profile(it.login) } }
        listFeedback(state, model::refresh, model::more, "暂无公开成员")
    }
}
