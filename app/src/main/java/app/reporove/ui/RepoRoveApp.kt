package app.reporove.ui

import android.net.Uri
import android.view.Window
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import app.reporove.AppContainer
import app.reporove.core.model.*
import kotlinx.coroutines.flow.map

val LocalAppNavigation = staticCompositionLocalOf<AppNavigation> { error("Navigation is not provided") }
val LocalAppModel = staticCompositionLocalOf<AppModel> { error("App is not provided") }

class AppNavigation(private val controller: androidx.navigation.NavHostController) {
    fun root(tab: MainTab) {
        controller.navigate(tab.name) {
            popUpTo(controller.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun repo(fullName: String, tab: RepoTab = RepoTab.Overview) { controller.navigate("repo/$fullName?tab=${tab.name}") }
    fun readme(fullName: String) { controller.navigate("readme/$fullName") }
    fun code(fullName: String, ref: String, path: String = "", anchor: String = "") { controller.navigate("code/$fullName?ref=${Uri.encode(ref)}&path=${Uri.encode(path)}&anchor=${Uri.encode(anchor)}") }
    fun thread(fullName: String, number: Int, pull: Boolean, anchor: String = "") { controller.navigate("thread/$fullName/$number?pull=$pull&anchor=${Uri.encode(anchor)}") }
    fun release(fullName: String, id: Long) { controller.navigate("release/$fullName/$id") }
    fun profile(login: String) { controller.navigate("person/${Uri.encode(login)}") { launchSingleTop = true } }
    fun people(login: String, relation: String) { controller.navigate("people/${Uri.encode(login)}/$relation") }
    fun userRepos(login: String, stars: Boolean = false) { controller.navigate("user-repos/${Uri.encode(login)}?stars=$stars") }
    fun contributors(fullName: String) { controller.navigate("contributors/$fullName") }
    fun commits(fullName: String, ref: String) { controller.navigate("commits/$fullName?ref=${Uri.encode(ref)}") }
    fun pullFiles(fullName: String, number: Int) { controller.navigate("pull-files/$fullName/$number") }
    fun run(fullName: String, id: Long, job: Long = 0) { controller.navigate("run/$fullName/$id?job=$job") }
    fun organizations() { controller.navigate("organizations") }
    fun settingsSection(section: String) { controller.navigate("settings/$section") }
    fun wiki(fullName: String, slug: String = "") { controller.navigate("wiki/$fullName?slug=${Uri.encode(slug)}") }
    fun repoFeature(fullName: String, feature: String) { controller.navigate("feature/$fullName/$feature") }
    fun interests() { controller.navigate("interests") { launchSingleTop = true } }
    fun topic(name: String) { controller.navigate("topic/${Uri.encode(name)}") }
    fun collection(name: String) { controller.navigate("collection/${Uri.encode(name)}") }
    fun commit(fullName: String, sha: String) { controller.navigate("commit/$fullName/${Uri.encode(sha)}") }
    fun discussions(fullName: String, number: Int = 0) { controller.navigate("discussion/$fullName/$number") }
    fun search(scope: String) { controller.navigate("scoped-search?scope=${Uri.encode(scope)}") }
    fun open(url: String, app: AppModel, external: (String) -> Unit) {
        val target = GitHubLinks.parse(url)
        if (target == null) external(url) else this.target(target, app)
    }
    fun target(target: GitHubTarget, app: AppModel) {
        when (target) {
            is GitHubTarget.Profile -> when (target.tab) { "stars" -> userRepos(target.login, true); "repositories" -> userRepos(target.login); "followers", "following" -> people(target.login, target.tab); else -> profile(target.login) }
            is GitHubTarget.Repo -> repo(target.fullName, target.tab)
            is GitHubTarget.Thread -> thread(target.fullName, target.number, target.pull, target.anchor)
            is GitHubTarget.Ref -> code(target.fullName, target.ref)
            is GitHubTarget.Topic -> topic(target.name)
            is GitHubTarget.Collection -> collection(target.name)
            is GitHubTarget.Commit -> commit(target.fullName, target.sha)
            is GitHubTarget.Wiki -> wiki(target.fullName, target.slug)
            is GitHubTarget.Run -> run(target.fullName, target.id, target.job)
            is GitHubTarget.Release -> release(target.fullName, target.id)
            is GitHubTarget.ReleaseTag -> app.action("release-link:${target.fullName}:${target.tag}") { release(target.fullName, app.container.repository.releaseTag(target.fullName, target.tag).data.id) }
            is GitHubTarget.Check -> controller.navigate("check/${target.fullName}/${target.id}?suite=${target.suite}")
            is GitHubTarget.Comment -> controller.navigate("comment/${target.fullName}/${target.kind}/${target.id}")
            is GitHubTarget.Compare -> controller.navigate("compare/${target.fullName}?range=${Uri.encode(target.range)}")
            is GitHubTarget.Context -> controller.navigate("context/${target.fullName}?title=${Uri.encode(target.title)}&kind=${Uri.encode(target.kind)}&url=${Uri.encode(target.url.orEmpty())}")
            is GitHubTarget.Discussion -> discussions(target.fullName, target.number)
            is GitHubTarget.File -> app.action("file-link:${target.fullName}:${target.refAndPath}") {
                val repository = app.container.repository.repository(target.fullName).data
                val branches = try { app.container.repository.branches(target.fullName).data.map { it.name } } catch (error: kotlinx.coroutines.CancellationException) { throw error } catch (_: Exception) { emptyList() }
                val (ref, path) = GitHubLinks.fileParts(target.refAndPath, branches, repository.defaultBranch)
                code(target.fullName, ref, path, target.fragment.orEmpty())
            }
        }
    }
    fun offlineLibrary() { controller.navigate("offline") { launchSingleTop = true } }
    fun offlineRepo(id: String) { controller.navigate("offline-repo/$id") }
    fun offlineCode(id: String, path: String, anchor: String = "") { controller.navigate("offline-code/$id?path=${Uri.encode(path)}&anchor=${Uri.encode(anchor)}") }
    fun settings() { controller.navigate("settings") { launchSingleTop = true } }
    fun downloads() { controller.navigate("downloads") { launchSingleTop = true } }
    fun back() { controller.popBackStack() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RepoRoveApp(container: AppContainer, window: Window? = null) {
    val preferencesFlow = remember(container) { container.local.preferences.map { it as Preferences? } }
    val preferences by preferencesFlow.collectAsStateWithLifecycle(initialValue = null)
    val account by container.repository.account.collectAsStateWithLifecycle()
    val accountScope by container.repository.sessionScope.collectAsStateWithLifecycle()
    val app = screenModel("app") { AppModel(container) }
    val prefs = preferences
    if (prefs == null) { RepoRoveTheme(Preferences()) { LoadingIndicator() }; return }
    RepoRoveTheme(prefs) {
        val light = MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect { window?.let { WindowCompat.getInsetsController(it, it.decorView).apply { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light } } }
        key(accountScope) {
            val controller = rememberNavController()
            val navigation = remember(controller) { AppNavigation(controller) }
            val entry by controller.currentBackStackEntryAsState()
            val route = entry?.destination?.route ?: prefs.home.name
            val start = remember { prefs.home.name }
            val snackbar = remember { SnackbarHostState() }
            val title = MainTab.entries.find { it.name == route }?.label ?: when {
                route.startsWith("repo/") -> entry?.arguments?.getString("name") ?: "仓库"
                route.startsWith("readme/") -> "README"
                route.startsWith("code/") -> "代码"
                route.startsWith("thread/") -> if (entry?.arguments?.getBoolean("pull") == true) "Pull Request" else "Issue"
                route.startsWith("release/") -> "版本详情"
                route == "settings" -> "设置"
                route.startsWith("settings/") -> when (entry?.arguments?.getString("section")) { "history" -> "版本更新记录"; "licenses" -> "开源许可与致谢"; "appearance" -> "外观与阅读"; "navigation" -> "导航与启动"; "repository" -> "仓库布局"; "feedback" -> "推荐排除"; else -> "关于" }
                route == "organizations" -> "我的组织"
                route.startsWith("check/") -> "检查详情"
                route.startsWith("comment/") -> "评论"
                route.startsWith("compare/") -> "推送变更"
                route.startsWith("context/") -> "活动详情"
                route == "interests" -> "调整兴趣"
                route.startsWith("person/") -> "主页"
                route.startsWith("people/") -> "用户"
                route.startsWith("user-repos/") -> "项目"
                route.startsWith("contributors/") -> "贡献者"
                route.startsWith("topic/") -> "主题"
                route.startsWith("collection/") -> "精选合集"
                route.startsWith("commit/") -> "提交"
                route.startsWith("discussion/") -> "讨论"
                route.startsWith("wiki/") -> "Wiki"
                route.startsWith("commits/") -> "提交记录"
                route.startsWith("pull-files/") -> "文件差异"
                route.startsWith("run/") -> "工作流详情"
                route.startsWith("feature/") -> when (entry?.arguments?.getString("feature")) { "projects" -> "Projects"; "security" -> "安全公告"; "stats" -> "仓库概况"; else -> "Wiki" }
                route.startsWith("scoped-search") -> "搜索"
                route == "offline" -> "离线与最近阅读"
                route.startsWith("offline-") -> "本机仓库"
                route == "downloads" -> "下载"
                else -> "RepoRove"
            }
            ClipboardLinkPrompt(prefs, navigation, app)
            val main = MainTab.entries.any { it.name == route }
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(route, account?.id) { focus.clearFocus(); keyboard?.hide() }
            LaunchedEffect(app) { app.events.collect { event ->
                val result = snackbar.showSnackbar(event.text, actionLabel = if (event.undo != null) "撤销" else null, withDismissAction = event.undo != null)
                if (result == SnackbarResult.ActionPerformed) event.undo?.let { undo -> app.action("undo-feedback", block = undo) }
            } }
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
                        if (!main) IconButton(onClick = navigation::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    }, actions = {
                        if (main && route != MainTab.Search.name && (route != MainTab.Profile.name || account == null)) IconButton(onClick = { navigation.root(MainTab.Search) }) { Icon(Icons.Outlined.Search, "搜索") }
                        if (main && route != MainTab.Profile.name) IconButton(onClick = { navigation.root(MainTab.Profile) }) { UserAvatar(account, Modifier.size(28.dp), "我的") }
                        if (route == MainTab.Profile.name) IconButton(onClick = navigation::settings) { Icon(Icons.Outlined.Settings, "设置") }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
                },
                bottomBar = {
                    if (main) NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                        prefs.tabs.forEach { tab -> NavigationBarItem(selected = route == tab.name, onClick = { navigation.root(tab) }, icon = {
                            Icon(when (tab) { MainTab.Discover -> Icons.Outlined.Explore; MainTab.Feed -> Icons.Outlined.Timeline; MainTab.Inbox -> Icons.Outlined.Inbox; MainTab.Library -> Icons.Outlined.Bookmarks; MainTab.Search -> Icons.Outlined.Search; MainTab.Profile -> Icons.Outlined.PersonOutline }, tab.label)
                        }, label = { Text(tab.label) }) }
                    }
                },
            ) { padding ->
                CompositionLocalProvider(LocalAppNavigation provides navigation, LocalAppModel provides app) {
                NavHost(controller, startDestination = start, modifier = Modifier.fillMaxSize().padding(padding)) {
                    composable(MainTab.Discover.name) { DiscoverScreen(app, prefs, navigation) }
                    composable(MainTab.Search.name) { SearchScreen(app, prefs, navigation) }
                    composable(MainTab.Library.name) { LibraryScreen(app, prefs, navigation) }
                    composable(MainTab.Feed.name) { FeedScreen(app, navigation) }
                    composable(MainTab.Inbox.name) { InboxScreen(app, navigation) }
                    composable(MainTab.Profile.name) { ProfileScreen(app, navigation) }
                    composable("settings") { SettingsScreen(app, prefs, navigation) }
                    composable("settings/{section}") { SettingsDetailScreen(app, prefs, navigation, it.arguments?.getString("section").orEmpty()) }
                    composable("organizations") { OrganizationsScreen(app, navigation) }
                    composable("check/{owner}/{name}/{id}?suite={suite}", arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("suite") { type = NavType.BoolType; defaultValue = false })) { CheckScreen(app, prefs, navigation, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getLong("id") ?: 0, it.arguments?.getBoolean("suite") == true) }
                    composable("comment/{owner}/{name}/{kind}/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { TargetCommentScreen(app, prefs, navigation, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getString("kind").orEmpty(), it.arguments?.getLong("id") ?: 0) }
                    composable("compare/{owner}/{name}?range={range}", arguments = listOf(navArgument("range") { defaultValue = "" })) { ComparisonScreen(app, navigation, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getString("range").orEmpty()) }
                    composable("context/{owner}/{name}?title={title}&kind={kind}&url={url}", arguments = listOf(navArgument("title") { defaultValue = "" }, navArgument("kind") { defaultValue = "" }, navArgument("url") { defaultValue = "" })) { ActivityContextScreen(app, navigation, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getString("title").orEmpty(), it.arguments?.getString("kind").orEmpty(), it.arguments?.getString("url").orEmpty()) }
                    composable("interests") { InterestsScreen(app, prefs, navigation) }
                    composable("person/{login}") { UserProfileScreen(app, prefs, navigation, it.arguments?.getString("login").orEmpty()) }
                    composable("people/{login}/{relation}") { PeopleScreen(app, navigation, it.arguments?.getString("login").orEmpty(), it.arguments?.getString("relation").orEmpty()) }
                    composable("user-repos/{login}?stars={stars}", arguments = listOf(navArgument("stars") { type = NavType.BoolType; defaultValue = false })) { UserRepositoriesScreen(app, prefs, navigation, it.arguments?.getString("login").orEmpty(), it.arguments?.getBoolean("stars") == true) }
                    composable("contributors/{owner}/{name}") { PeopleScreen(app, navigation, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", "contributors") }
                    composable("topic/{name}") { TopicScreen(app, prefs, navigation, it.arguments?.getString("name").orEmpty()) }
                    composable("collection/{name}") { CollectionScreen(app, prefs, navigation, it.arguments?.getString("name").orEmpty()) }
                    composable("commits/{owner}/{name}?ref={ref}", arguments = listOf(navArgument("ref") { defaultValue = "main" })) { CommitsScreen(app, navigation, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getString("ref").orEmpty()) }
                    composable("pull-files/{owner}/{name}/{number}", arguments = listOf(navArgument("number") { type = NavType.IntType })) { PullFilesScreen(app, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getInt("number") ?: 0) }
                    composable("run/{owner}/{name}/{id}?job={job}", arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("job") { type = NavType.LongType; defaultValue = 0L })) { WorkflowScreen(app, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getLong("id") ?: 0L, it.arguments?.getLong("job") ?: 0L) }
                    composable("wiki/{owner}/{name}?slug={slug}", arguments = listOf(navArgument("slug") { defaultValue = "" })) { WikiScreen(app, prefs, navigation, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getString("slug").orEmpty()) }
                    composable("feature/{owner}/{name}/{feature}") { RepoFeatureScreen(app, prefs, navigation, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getString("feature").orEmpty()) }
                    composable("commit/{owner}/{name}/{sha}") { CommitScreen(app, prefs, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getString("sha").orEmpty()) }
                    composable("discussion/{owner}/{name}/{number}", arguments = listOf(navArgument("number") { type = NavType.IntType })) { DiscussionScreen(app, prefs, navigation, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getInt("number") ?: 0) }
                    composable("scoped-search?scope={scope}", arguments = listOf(navArgument("scope") { defaultValue = "" })) { SearchScreen(app, prefs, navigation, it.arguments?.getString("scope").orEmpty()) }
                    composable("offline") { OfflineLibrary(app, prefs, navigation) }
                    composable("offline-repo/{id}") { OfflineRepositoryScreen(app, prefs, navigation, it.arguments?.getString("id").orEmpty()) }
                    composable("offline-code/{id}?path={path}&anchor={anchor}", arguments = listOf(navArgument("path") { defaultValue = "" }, navArgument("anchor") { defaultValue = "" })) { OfflineRepositoryScreen(app, prefs, navigation, it.arguments?.getString("id").orEmpty(), it.arguments?.getString("path").orEmpty(), it.arguments?.getString("anchor").orEmpty()) }
                    composable("downloads") { DownloadsScreen(app) }
                    composable("repo/{owner}/{name}?tab={tab}", arguments = listOf(navArgument("tab") { defaultValue = RepoTab.Overview.name })) { backStack ->
                        val fullName = "${backStack.arguments?.getString("owner")}/${backStack.arguments?.getString("name")}"
                        val tab = runCatching { RepoTab.valueOf(backStack.arguments?.getString("tab").orEmpty()) }.getOrDefault(RepoTab.Overview)
                        RepositoryScreen(app, prefs, navigation, fullName, tab)
                    }
                    composable("readme/{owner}/{name}") { ReadmeScreen(app, prefs, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}") }
                    composable("code/{owner}/{name}?ref={ref}&path={path}&anchor={anchor}", arguments = listOf(navArgument("ref") { defaultValue = "main" }, navArgument("path") { defaultValue = "" }, navArgument("anchor") { defaultValue = "" })) {
                        CodeScreen(app, prefs, navigation, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getString("ref") ?: "main", it.arguments?.getString("path").orEmpty(), it.arguments?.getString("anchor").orEmpty())
                    }
                    composable("thread/{owner}/{name}/{number}?pull={pull}&anchor={anchor}", arguments = listOf(navArgument("number") { type = NavType.IntType }, navArgument("pull") { type = NavType.BoolType; defaultValue = false }, navArgument("anchor") { defaultValue = "" })) {
                        ThreadScreen(app, prefs, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getInt("number") ?: 0, it.arguments?.getBoolean("pull") == true, it.arguments?.getString("anchor").orEmpty())
                    }
                    composable("release/{owner}/{name}/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ReleaseScreen(app, prefs, "${it.arguments?.getString("owner")}/${it.arguments?.getString("name")}", it.arguments?.getLong("id") ?: 0L)
                    }
                }
                }
            }
        }
    }
}
