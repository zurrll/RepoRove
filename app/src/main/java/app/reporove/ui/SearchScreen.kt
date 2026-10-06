package app.reporove.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reporove.core.model.*

private sealed interface SearchHit {
    val key: String
    data class Repo(val value: Repository) : SearchHit { override val key = "repo:${value.id}" }
    data class Thread(val value: Issue, val pull: Boolean) : SearchHit { override val key = "thread:${value.id}" }
    data class Code(val value: CodeResult) : SearchHit { override val key = "code:${value.repository.id}:${value.path}:${value.sha}" }
    data class Person(val value: User) : SearchHit { override val key = "user:${value.login}" }
    data class TopicHit(val value: Topic) : SearchHit { override val key = "topic:${value.name}" }
    data class Commit(val value: CommitResult) : SearchHit { override val key = "commit:${value.sha}:${value.repository.fullName}" }
    data class DiscussionHit(val value: Discussion) : SearchHit { override val key = "discussion:${value.id}" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchScreen(app: AppModel, prefs: Preferences, nav: AppNavigation, initialScope: String = "") {
    var text by rememberSaveable(initialScope) { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(SearchKind.Repositories) }
    var scopeKind by rememberSaveable(initialScope) { mutableStateOf(initialScope.substringBefore(':', "all")) }
    var scopeValue by rememberSaveable(initialScope) { mutableStateOf(initialScope.substringAfter(':', "")) }
    var filters by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    var sort by rememberSaveable { mutableStateOf<String?>(null) }
    var order by rememberSaveable { mutableStateOf("desc") }
    var filterDialog by remember { mutableStateOf(false) }
    var typePicker by remember { mutableStateOf(false) }
    var sortPicker by remember { mutableStateOf(false) }
    var advancedFilters by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var submittedKind by rememberSaveable { mutableStateOf(SearchKind.Repositories) }
    var submittedSort by rememberSaveable { mutableStateOf<String?>(null) }
    var submittedOrder by rememberSaveable { mutableStateOf("desc") }
    val model = screenModel("search:$initialScope") { PagedModel<SearchHit> { it.key } }
    val state by model.state.collectAsStateWithLifecycle()
    val collections by app.container.repository.collections.collectAsStateWithLifecycle(CollectionState())
    val account by app.container.repository.account.collectAsStateWithLifecycle()
    val scope = if (SearchCapabilities.scoped(kind) && scopeKind != "all" && scopeValue.isNotBlank()) "$scopeKind:${scopeValue.trim()}" else ""
    val spec = SearchSpec(kind, text, scope, filters, sort, order)
    val draftQuery = spec.query()
    fun submit() {
        if (draftQuery.isBlank()) { app.message("请输入关键词，或选择筛选条件"); return }
        if ((kind == SearchKind.Code || kind == SearchKind.Discussions) && account == null) { app.message("此类搜索需要登录 GitHub"); return }
        if (submittedQuery == draftQuery && submittedKind == kind && submittedSort == sort && submittedOrder == order) model.refresh()
        else { submittedQuery = draftQuery; submittedKind = kind; submittedSort = sort; submittedOrder = order }
    }
    LaunchedEffect(submittedQuery, submittedKind, submittedSort, submittedOrder) {
        if (submittedQuery.isBlank()) return@LaunchedEffect
        val identity = "$submittedKind:$submittedQuery:$submittedSort:$submittedOrder"
        val actualKind = submittedKind; val query = submittedQuery; val actualSort = submittedSort; val actualOrder = submittedOrder
        model.configure(identity) { page, refresh ->
            val repo = app.container.repository
            fun <T> Loaded<SearchResponse<T>>.hits(map: (T) -> SearchHit): Loaded<Page<SearchHit>> = Loaded(Page(data.items.map(map), page * 30 < minOf(data.totalCount, 1000), data.incomplete), cachedAt, offline)
            when (actualKind) {
                SearchKind.Repositories -> repo.searchRepositories(query, actualSort, page, actualOrder).hits(SearchHit::Repo)
                SearchKind.Issues, SearchKind.Pulls -> repo.searchIssues(query, page, actualSort, actualOrder).hits { SearchHit.Thread(it, actualKind == SearchKind.Pulls) }
                SearchKind.Users -> repo.searchUsers(query, page, actualSort, actualOrder).hits(SearchHit::Person)
                SearchKind.Code -> repo.searchCode(query, page).hits(SearchHit::Code)
                SearchKind.Topics -> repo.searchTopics(query, page).hits(SearchHit::TopicHit)
                SearchKind.Commits -> repo.searchCommits(query, page, actualSort, actualOrder).hits(SearchHit::Commit)
                SearchKind.Discussions -> repo.searchDiscussions(query, page, refresh).let { Loaded(Page(it.data.items.map(SearchHit::DiscussionHit), it.data.hasMore), it.cachedAt, it.offline) }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), label = { Text("搜索 GitHub") }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }), trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Outlined.Close, "清除关键词") } }) }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { typePicker = true }) { Text(kind.label); Icon(Icons.Outlined.ArrowDropDown, "选择搜索类型") }
            TextButton(onClick = { filterDialog = true }) { Icon(Icons.Outlined.FilterList, null); Text("筛选${if (filters.isNotEmpty()) " (${filters.size})" else ""}") }
            Spacer(Modifier.weight(1f))
            Button(onClick = ::submit) { Text("搜索") }
        } }
        if (scope.isNotEmpty() || filters.isNotEmpty()) item { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (scope.isNotEmpty()) item { InputChip(selected = true, onClick = { filterDialog = true }, label = { Text("${when (scopeKind) { "repo" -> "仓库"; "org" -> "组织"; else -> "用户" }} · $scopeValue") }) }
            items(filters.toList(), key = { it.first }) { (key, value) -> InputChip(selected = true, onClick = { filters = filters - key }, label = { Text("${SearchCapabilities.fields(kind).firstOrNull { it.key == key }?.label ?: key}: $value") }, trailingIcon = { Icon(Icons.Outlined.Close, "移除$key", Modifier.size(16.dp)) }) }
        } }
        if (submittedQuery.isBlank()) item { EmptyState("查找项目与内容", "输入关键词即可搜索") }
        else {
            item { SectionTitle("${submittedKind.label}结果", "排序", { sortPicker = true }) }
            if (submittedQuery != draftQuery || submittedKind != kind) item { Note("当前显示上次搜索结果；点击搜索以应用新条件。") }
            items(state.items, key = SearchHit::key) { hit -> when (hit) {
                is SearchHit.Repo -> ProjectRow(app, nav, prefs, hit.value, collections)
                is SearchHit.Person -> PersonRow(hit.value) { nav.profile(hit.value.login) }
                is SearchHit.Thread -> IssueRow(hit.value, hit.pull) { val target = GitHubLinks.parse(hit.value.htmlUrl) as? GitHubTarget.Thread; target?.let { nav.thread(it.fullName, it.number, it.pull) } }
                is SearchHit.Code -> ActionRow(hit.value.path, hit.value.repository.fullName) { nav.open(hit.value.htmlUrl, app) {} }
                is SearchHit.TopicHit -> ActionRow(hit.value.displayName ?: hit.value.name, hit.value.description) { nav.topic(hit.value.name) }
                is SearchHit.Commit -> ActionRow(hit.value.commit.message.lineSequence().firstOrNull().orEmpty(), "${hit.value.repository.fullName} · ${hit.value.sha.take(7)}") { nav.commit(hit.value.repository.fullName, hit.value.sha) }
                is SearchHit.DiscussionHit -> ActionRow(hit.value.title, "${hit.value.repository} · ${hit.value.category}") { nav.discussions(hit.value.repository, hit.value.number) }
            } }
            listFeedback(state, model::refresh, model::more, "没有匹配的内容")
        }
    }
    if (typePicker) ModalBottomSheet(onDismissRequest = { typePicker = false }) {
        Text("搜索类型", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.heightIn(max = 440.dp)) { items(SearchKind.entries) { option -> ListItem(headlineContent = { Text(option.label) }, trailingContent = { if (kind == option) Icon(Icons.Outlined.Check, null) }, modifier = Modifier.clickable { kind = option; filters = emptyMap(); sort = null; order = "desc"; typePicker = false }) } }
    }
    if (sortPicker) ModalBottomSheet(onDismissRequest = { sortPicker = false }) {
        Text("结果排序", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        SearchCapabilities.sorts(kind).forEach { option -> ListItem(headlineContent = { Text(option.label) }, trailingContent = { if (sort == option.key) Icon(Icons.Outlined.Check, null) }, modifier = Modifier.clickable { sort = option.key }) }
        if (sort != null) ChoiceRow(listOf("desc", "asc"), order, { if (it == "desc") "从高到低 / 从新到旧" else "从低到高 / 从旧到新" }, { order = it })
        Button(onClick = { sortPicker = false; submit() }, Modifier.fillMaxWidth().padding(20.dp)) { Text("应用排序") }
    }
    if (filterDialog) ModalBottomSheet(onDismissRequest = { filterDialog = false }) {
        Column(Modifier.fillMaxHeight(.85f)) {
            SectionTitle("${kind.label}筛选", "重置", { filters = emptyMap(); scopeKind = "all"; scopeValue = "" })
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (SearchCapabilities.scoped(kind)) {
                    item { Text("搜索范围", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleSmall); ChoiceRow(listOf("all", "repo", "user", "org"), scopeKind, { when (it) { "repo" -> "仓库"; "user" -> "用户"; "org" -> "组织"; else -> "全部" } }, { scopeKind = it }) }
                    if (scopeKind != "all") item { OutlinedTextField(scopeValue, { scopeValue = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), singleLine = true, label = { Text(if (scopeKind == "repo") "owner/repository" else "登录名") }) }
                }
                val fields = SearchCapabilities.fields(kind)
                items(if (advancedFilters) fields else fields.take(3), key = SearchField::key) { field -> Column(Modifier.padding(horizontal = 20.dp)) {
                    if (field.key == "language") OutlinedButton(onClick = { filterDialog = false; languageDialog = true }, Modifier.fillMaxWidth()) { Text("语言：${filters[field.key] ?: "不限"}") }
                    else if (field.options.isNotEmpty()) {
                        Text(field.label, style = MaterialTheme.typography.titleSmall)
                        val choices = listOf("" to "不限") + field.options
                        ChoiceRow(choices, choices.firstOrNull { it.first == filters[field.key].orEmpty() } ?: choices.first(), { it.second }, { filters = if (it.first.isBlank()) filters - field.key else filters + (field.key to it.first) })
                    } else OutlinedTextField(filters[field.key].orEmpty(), { value -> filters = if (value.isBlank()) filters - field.key else filters + (field.key to value) }, Modifier.fillMaxWidth(), label = { Text(field.label) }, placeholder = { Text(field.hint) }, singleLine = true)
                } }
                if (fields.size > 3) item { TextButton(onClick = { advancedFilters = !advancedFilters }, Modifier.padding(horizontal = 20.dp)) { Text(if (advancedFilters) "收起更多条件" else "更多条件") } }
                item { TextButton(onClick = { advancedFilters = true }, Modifier.padding(horizontal = 20.dp)) { Text("支持直接输入 GitHub 高级查询") }; if (advancedFilters) Note(draftQuery.ifBlank { "尚未选择条件" }) }
                if (kind == SearchKind.Code) item { Note("代码搜索需要登录，使用 GitHub API 支持的语法。") }
            }
            Button(onClick = { filterDialog = false; submit() }, Modifier.fillMaxWidth().padding(20.dp)) { Text("应用并搜索") }
        }
    }
    if (languageDialog) LanguagePicker(app, filters["language"]) { language -> filters = if (language == null) filters - "language" else filters + ("language" to language); languageDialog = false; filterDialog = true }
}

@Composable private fun LanguagePicker(app: AppModel, selected: String?, onSelect: (String?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val languages = remember(app) { app.container.catalogs.languages.items }
    val matches = remember(text) { languages.filter { it.name.contains(text, true) || it.aliases.any { alias -> alias.contains(text, true) } } }
    AlertDialog(onDismissRequest = { onSelect(selected) }, title = { Text("选择语言") }, text = {
        Column { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("名称或别名") }); LazyColumn(Modifier.heightIn(max = 380.dp)) { items(matches, key = LanguageEntry::name) { language -> TextButton(onClick = { onSelect(language.name) }, Modifier.fillMaxWidth()) { Text(language.name, Modifier.weight(1f)) } } } }
    }, confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text("完成") } }, dismissButton = { TextButton(onClick = { onSelect(null) }) { Text("不限语言") } })
}
