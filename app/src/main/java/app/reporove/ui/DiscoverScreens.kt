package app.reporove.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reporove.core.model.*
import app.reporove.data.RecommendationRanking
import app.reporove.core.network.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DiscoverScreen(app: AppModel, prefs: Preferences, nav: AppNavigation) {
    var mode by rememberSaveable { mutableStateOf("为你") }
    var topic by rememberSaveable { mutableStateOf("全部") }
    var sort by rememberSaveable { mutableStateOf("stars") }
    var dismissRepo by remember { mutableStateOf<Recommendation?>(null) }
    var choosingTopic by remember { mutableStateOf(false) }
    val model = screenModel("discover") { PagedModel<Recommendation> { it.repository.id } }
    val state by model.state.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    val revision by app.container.repository.revision.collectAsStateWithLifecycle()
    val interestKey = "${account?.id}:${prefs.interests}:${prefs.inferStarInterests}:${prefs.mutedTopics}:${prefs.mutedRepositories}:$revision:${RecommendationRanking.VERSION}"
    LaunchedEffect(prefs.interests, prefs.mutedTopics) { if (topic != "全部" && (topic !in prefs.interests || topic in prefs.mutedTopics)) topic = "全部" }
    LaunchedEffect(mode, topic, sort, interestKey) {
        if (mode == "探索") return@LaunchedEffect
        val actualTopic = topic.takeIf { it != "全部" && it in prefs.interests && it !in prefs.mutedTopics }
        model.configure("$mode:$actualTopic:$sort:$interestKey") { page, refresh ->
            if (mode == "为你") app.container.repository.recommendations(prefs, actualTopic, page, refresh)
            else app.container.repository.discover(actualTopic, sort, page, refresh).let { Loaded(Page(it.data.items.map { repo -> Recommendation(repo, emptyList()) }, page * 30 < minOf(it.data.totalCount, 1000), it.data.incomplete), it.cachedAt, it.offline) }
        }
    }
    Column {
        ChoiceRow(listOf("为你", "探索", "热门"), mode, { it }, { mode = it; topic = "全部" })
        if (mode == "探索") ExploreBrowser(app, nav)
        else LazyColumn(Modifier.fillMaxSize()) {
            if (mode == "为你") {
                item { SectionTitle("你的兴趣", "调整兴趣", nav::interests) }
                if (prefs.interests.isEmpty()) item { Note(if (prefs.inferStarInterests) "根据 Star 的主题发现项目；也可以添加兴趣。" else "先看看活跃的热门项目，添加兴趣后会更贴近你的方向。") }
                else item { ChoiceRow(listOf("全部") + prefs.interests.filterNot { it in prefs.mutedTopics }, topic, { it }, { topic = it }) }
                item { Note(if (prefs.inferStarInterests) "兴趣优先，并参考 Star；以活跃且有一定关注度的项目为主。" else "兴趣优先，兼顾关注度与维护情况，少量探索较新的项目。") }
            } else {
                item { ChoiceRow(listOf("stars", "updated", "forks"), sort, { when (it) { "stars" -> "Star 最多"; "updated" -> "最近更新"; else -> "Fork 最多" } }, { sort = it }) }
                item { Note("公开仓库按所选指标排序。") }
            }
            item { SectionTitle("项目", "刷新", model::refresh) }
            items(state.items, key = { it.repository.id }) { item ->
                if (mode == "为你") Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.reasons.joinToString(" · "), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = LocalSemanticColors.current.link)
                    IconButton(onClick = { dismissRepo = item; choosingTopic = false }) { Icon(Icons.Outlined.MoreHoriz, "推荐反馈") }
                }
                ProjectRow(app, nav, prefs, item.repository, collections)
            }
            listFeedback(state, model::refresh, model::more, "暂时没有匹配的项目")
        }
    }
    dismissRepo?.let { recommendation ->
        val repo = recommendation.repository
        ModalBottomSheet(onDismissRequest = { dismissRepo = null }) {
            SectionTitle(if (choosingTopic) "不再推荐某类项目" else "推荐反馈")
            Text(repo.fullName, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium)
            if (!choosingTopic) {
                ListItem(headlineContent = { Text("不再推荐这个项目") }, supportingContent = { Text("仅从为你排除，保留你的 Star 和收藏") }, modifier = Modifier.clickable {
                    dismissRepo = null
                    app.action("exclude:${repo.id}") {
                        app.container.local.updatePreferences { it.copy(mutedRepositories = (it.mutedRepositories + repo.id).distinct(), mutedRepositoryNames = it.mutedRepositoryNames + (repo.id to repo.fullName)) }
                        app.message("已从为你排除此项目") { app.container.local.updatePreferences { it.copy(mutedRepositories = it.mutedRepositories - repo.id, mutedRepositoryNames = it.mutedRepositoryNames - repo.id) } }
                    }
                })
                ListItem(headlineContent = { Text("调整我的兴趣") }, modifier = Modifier.clickable { dismissRepo = null; nav.interests() })
                if (recommendation.relatedTopics.isNotEmpty()) ListItem(headlineContent = { Text("不再推荐某类项目") }, supportingContent = { Text("选择与本次推荐有关的主题") }, modifier = Modifier.clickable { choosingTopic = true })
            } else {
                Note("排除主题会影响为你中的多个项目，已选兴趣会保留；可以在推荐排除中恢复。")
                LazyColumn(Modifier.heightIn(max = 340.dp)) { items(recommendation.relatedTopics.distinct()) { topic ->
                    val name = app.container.catalogs.explore.topics.firstOrNull { it.slug == topic }?.name ?: topic
                    ListItem(headlineContent = { Text("不再推荐 $name 主题") }, modifier = Modifier.clickable {
                        dismissRepo = null
                        app.action("exclude-topic:$topic") {
                            app.container.local.updatePreferences { it.copy(mutedTopics = (it.mutedTopics + topic).distinct()) }
                            app.message("已从为你排除 $name 主题") { app.container.local.updatePreferences { it.copy(mutedTopics = it.mutedTopics - topic) } }
                        }
                    })
                } }
            }
            TextButton(onClick = { dismissRepo = null; nav.settingsSection("feedback") }, Modifier.padding(20.dp)) { Text("查看与恢复已排除内容") }
        }
    }
}

@Composable private fun ExploreBrowser(app: AppModel, nav: AppNavigation) {
    var section by rememberSaveable { mutableStateOf("主题") }
    var text by rememberSaveable { mutableStateOf("") }
    val catalog = remember(app) { app.container.catalogs.explore }
    val entries = remember(section, text) { (if (section == "主题") catalog.topics else catalog.collections).filter { it.name.contains(text, true) || it.slug.contains(text, true) || it.description.contains(text, true) } }
    LazyColumn {
        item { ChoiceRow(listOf("主题", "合集"), section, { it }, { section = it }) }
        item { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(20.dp), singleLine = true, label = { Text("查找${section}") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }) }
        item { Note("来自 GitHub Explore 的公开精选目录 · ${entries.size} 项") }
        items(entries, key = ExploreEntry::slug) { entry -> ActionRow(entry.name, entry.description.lineSequence().firstOrNull()?.take(200)) { if (section == "主题") nav.topic(entry.slug) else nav.collection(entry.slug) } }
        item { Note("来源：github/explore · CC BY 4.0 · 目录版本 ${catalog.revision.take(8)}") }
    }
}

@Composable fun InterestsScreen(app: AppModel, prefs: Preferences, nav: AppNavigation) {
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(prefs.interests) }
    var inferred by rememberSaveable { mutableStateOf(prefs.inferStarInterests) }
    val catalog = remember(app) { app.container.catalogs.explore }
    var inputError by remember { mutableStateOf<String?>(null) }
    val pending = InterestTopics.normalize(text)
    val topics = remember(text, selected) { catalog.topics.filter { it.name.contains(text, true) || it.slug.contains(text, true) || it.aliases.any { alias -> alias.contains(text, true) } || it.description.contains(text, true) }.sortedBy { it.slug !in selected } }
    fun toggle(topic: String) {
        if (topic in selected) selected = selected - topic
        else if (selected.size >= 20) app.message("最多选择 20 个主题")
        else { selected = selected + topic }
        text = ""; inputError = null
    }
    fun addPending(): Boolean {
        if (text.isBlank()) return true
        val topic = InterestTopics.normalize(text)
        if (topic == null) { inputError = "主题使用英文小写字母、数字或连字符，最长 50 个字符。"; return false }
        if (topic !in selected && selected.size >= 20) { inputError = "最多选择 20 个主题，请先移除一个。"; return false }
        selected = (selected + topic).distinct(); text = ""; inputError = null; focus.clearFocus()
        return true
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).testTag("interests-list")) {
            item { Note("输入主题名或勾选建议，保存时会加入正在输入的主题。可以留空。") }
            item { Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Switch(inferred, { inferred = it }); Text("同时参考我的 Star", Modifier.padding(start = 12.dp)) } }
            item { SectionTitle("已选 ${selected.size} 个", "清空", { selected = emptyList(); text = ""; inputError = null }) }
            items(selected, key = { "selected:$it" }) { topic -> ActionRow(catalog.topics.firstOrNull { it.slug == topic }?.name ?: topic, if (topic in prefs.mutedTopics && topic in prefs.interests) "$topic · 已从推荐排除 · 点击移除兴趣" else "$topic · 点击移除") { toggle(topic) } }
            item { OutlinedTextField(text, { text = it; inputError = null }, Modifier.fillMaxWidth().padding(20.dp), singleLine = true, label = { Text("输入或搜索兴趣主题") }, placeholder = { Text("例如 python，也可输入目录外的主题") }, isError = inputError != null, supportingText = inputError?.let { error -> { Text(error) } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { addPending() })) }
            if (pending != null) item { ActionRow(if (pending in selected) "已选择 $pending" else "添加主题 $pending", if (pending in selected) "已在上方的兴趣中" else "加入后会显示在已选兴趣中") { addPending() } }
            item { Note("下方是 GitHub Explore 的主题建议；目录之外的主题也能添加。") }
            items(topics, key = { "topic:${it.slug}" }) { topic -> Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(topic.slug in selected, { toggle(topic.slug) }, Modifier.semantics { contentDescription = "兴趣${topic.slug}" }); Column(Modifier.weight(1f).padding(vertical = 8.dp)) { Text(topic.name, style = MaterialTheme.typography.titleSmall); Text(topic.description.take(180), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
            item { ActionRow("推荐排除", "查看与恢复不再推荐的项目和主题") { nav.settingsSection("feedback") } }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = nav::back, Modifier.weight(1f)) { Text("取消") }
            Button(onClick = { if (addPending()) { val saved = selected; app.action("save-interests", "兴趣已更新") { app.container.local.updatePreferences { it.copy(interests = saved, inferStarInterests = inferred, mutedTopics = it.mutedTopics - (saved.toSet() - prefs.interests.toSet())) }; nav.back() } } }, Modifier.weight(1f)) { Text("保存兴趣") }
        }
    }
}

@Composable fun TopicScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, name: String) {
    val topic = remember(name) { app.container.catalogs.explore.topics.firstOrNull { it.slug == name } }
    val model = screenModel("topic:$name") { PagedModel<Repository> { it.id } }
    val state by model.state.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    LaunchedEffect(name) { model.configure(name) { page, refresh -> app.container.repository.discover(name, "", page, refresh).let { Loaded(Page(it.data.items, page * 30 < minOf(it.data.totalCount, 1000), it.data.incomplete), it.cachedAt, it.offline) } } }
    LazyColumn {
        item { SectionTitle(topic?.name ?: name, if (name in prefs.interests) "移除兴趣" else "加入兴趣", {
            if (name !in prefs.interests && prefs.interests.size >= 20) app.message("最多选择 20 个主题，请先移除一个")
            else app.updatePreferences { it.copy(interests = if (name in it.interests) it.interests - name else it.interests + name, mutedTopics = it.mutedTopics - name) }
        }) }
        topic?.body?.takeIf(String::isNotBlank)?.let { item { MarkdownBody(it, prefs, fullName = "github/explore", path = "topics/$name/index.md") } }
        item { SectionTitle("相关项目", "刷新", model::refresh) }
        items(state.items, key = Repository::id) { ProjectRow(app, nav, prefs, it, collections) }
        listFeedback(state, model::refresh, model::more)
    }
}

private data class CollectionItem(val source: String, val repository: Repository? = null, val unavailable: String? = null)
@Composable fun CollectionScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, name: String) {
    val entry = remember(name) { app.container.catalogs.explore.collections.firstOrNull { it.slug == name } }
    val model = screenModel("collection:$name") { PagedModel<CollectionItem> { it.source } }
    val state by model.state.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(name) { model.configure(name) { page, refresh ->
        val sources = entry?.items.orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.drop((page - 1) * 8).take(8)
        val items = coroutineScope { sources.map { source -> async {
            if (source.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
                try { CollectionItem(source, app.container.repository.repository(source, refresh).data) }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { CollectionItem(source, unavailable = userMessage(error)) }
            } else CollectionItem(source)
        } }.awaitAll() }
        Loaded(Page(items, page * 8 < (entry?.items?.size ?: 0)), System.currentTimeMillis())
    } }
    LazyColumn {
        item { SectionTitle(entry?.name ?: name) }
        entry?.body?.takeIf(String::isNotBlank)?.let { item { MarkdownBody(it, prefs, fullName = "github/explore", path = "collections/$name/index.md") } }
        items(state.items, key = CollectionItem::source) { item ->
            if (item.repository != null) ProjectRow(app, nav, prefs, item.repository, collections)
            else ActionRow(item.source, item.unavailable ?: "合集内容") { if (item.unavailable != null) nav.repo(item.source) else if (item.source.startsWith("http")) nav.open(item.source, app) { openUrl(context, it) } else nav.profile(item.source) }
        }
        listFeedback(state, model::refresh, model::more)
        item { Note("精选内容来自 github/explore · CC BY 4.0；仓库数据来自 GitHub 实时接口。") }
    }
}

@Composable fun RecommendationExclusionsScreen(app: AppModel, prefs: Preferences) {
    LazyColumn {
        item { Note("仅影响为你；可随时恢复单项。") }
        if (prefs.mutedTopics.isEmpty() && prefs.mutedRepositories.isEmpty()) item { EmptyState("没有排除内容") }
        if (prefs.mutedRepositories.isNotEmpty()) {
            item { SectionTitle("项目") }
            items(prefs.mutedRepositories, key = { "repo:$it" }) { id -> ListItem(headlineContent = { Text(prefs.mutedRepositoryNames[id] ?: "项目 ID $id") }, trailingContent = { TextButton(onClick = { app.updatePreferences { it.copy(mutedRepositories = it.mutedRepositories - id, mutedRepositoryNames = it.mutedRepositoryNames - id) } }) { Text("恢复") } }) }
        }
        if (prefs.mutedTopics.isNotEmpty()) {
            item { SectionTitle("主题") }
            items(prefs.mutedTopics, key = { "topic:$it" }) { topic -> ListItem(headlineContent = { Text(app.container.catalogs.explore.topics.firstOrNull { it.slug == topic }?.name ?: topic) }, trailingContent = { TextButton(onClick = { app.updatePreferences { it.copy(mutedTopics = it.mutedTopics - topic) } }) { Text("恢复") } }) }
        }
        if (prefs.mutedTopics.isNotEmpty() || prefs.mutedRepositories.isNotEmpty()) item { TextButton(onClick = { app.updatePreferences { it.copy(mutedTopics = emptyList(), mutedRepositories = emptyList(), mutedRepositoryNames = emptyMap()) } }, Modifier.padding(20.dp)) { Text("恢复全部推荐") } }
    }
}
