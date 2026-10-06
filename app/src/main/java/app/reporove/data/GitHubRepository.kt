package app.reporove.data

import app.reporove.core.model.*
import app.reporove.core.network.*
import app.reporove.core.storage.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import retrofit2.HttpException
import java.io.IOException
import java.net.URLEncoder
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest
import java.util.Base64

class GitHubRepository(
    private val credentials: Credentials,
    val local: LocalStore,
    private val cache: ResponseCache,
    private val apiFactory: (String?) -> GitHubApi = { createApi(it) },
) {
    private data class Session(val api: GitHubApi, val scope: String, val user: User?)
    private val stored = credentials.read()
    @Volatile private var session = newSession(stored)
    private val accountState = MutableStateFlow(stored?.user)
    val account = accountState.asStateFlow()
    private val revisionState = MutableStateFlow(0L)
    val revision = revisionState.asStateFlow()
    val collections = local.collections.combine(account) { value, user ->
        if (user != null) value else value.copy(later = value.later.filterNot(Repository::isPrivate), following = value.following.filterNot(Repository::isPrivate))
    }
    private val authMutex = Mutex()

    suspend fun login(rawToken: String) = authMutex.withLock {
        val token = rawToken.trim()
        require(token.length in 10..512 && token.none(Char::isWhitespace)) { "请输入有效的 GitHub 访问令牌。" }
        val user = apiFactory(token).me()
        val account = StoredAccount(token, user)
        withContext(Dispatchers.IO) { credentials.save(account) }
        local.removePrivateCollections()
        session = newSession(account)
        accountState.value = user
    }

    suspend fun logout() = authMutex.withLock {
        withContext(Dispatchers.IO) { credentials.clear() }
        session = newSession(null)
        local.removePrivateCollections()
        cache.clear()
        accountState.value = null
    }

    suspend fun discover(topic: String?, sort: String, page: Int, refresh: Boolean = false): Loaded<SearchResponse<Repository>> = read("discover:$topic:$sort:$page", refresh) { it.searchRepositories(SearchQueries.discovery(topic), sort.takeIf(String::isNotBlank), page) }

    suspend fun recommendations(prefs: Preferences, topic: String?, page: Int, refresh: Boolean = false): Loaded<Page<Recommendation>> {
        // Preferences are captured by the caller, so an in-flight response cannot pick up a newer draft.
        val stars = if (account.value != null) allStars(refresh) else emptyList()
        val inferred = if (prefs.inferStarInterests) stars.flatMap(Repository::topics).groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }.take(8) else emptyList()
        val explicit = prefs.interests.filterNot { it in prefs.mutedTopics }
        val seeds = (explicit + inferred).distinct().filterNot { it in prefs.mutedTopics }
        val selected = topic?.takeIf { it in explicit }
        val active = selected?.let(::listOf) ?: seeds
        val requests = RecommendationCandidates.requests(active, page, System.currentTimeMillis())
        val limiter = kotlinx.coroutines.sync.Semaphore(3)
        val responses = coroutineScope { requests.map { request -> async {
            limiter.acquire()
            try { request to read("recommendations:v4:${request.query}:${request.sort}:${request.page}", refresh) { it.searchRepositories(request.query, request.sort, request.page) } }
            finally { limiter.release() }
        } }.awaitAll() }
        val starIds = stars.map(Repository::id).toSet()
        val repos = responses.flatMap { it.second.data.items }.distinctBy(Repository::id)
            .filterNot { it.stars <= 0 || it.archived || it.fork || it.isPrivate || it.id in starIds || it.id in prefs.mutedRepositories || it.topics.any { t -> t in prefs.mutedTopics } }
        val mainIds = responses.filterNot { it.first.discovery }.flatMap { it.second.data.items }.map(Repository::id).toSet()
        val discoveryIds = responses.filter { it.first.discovery }.flatMap { it.second.data.items }.map(Repository::id).toSet() - mainIds
        val ranked = RecommendationRanking.rank(repos, explicit, inferred, System.currentTimeMillis(), discoveryIds)
        return Loaded(Page(ranked, RecommendationCandidates.hasUnvisitedTopics(active, page) || responses.any { it.first.page * 30 < minOf(it.second.data.totalCount, 1000) }, responses.any { it.second.data.incomplete }), responses.minOf { it.second.cachedAt }, responses.any { it.second.offline })
    }

    private suspend fun allStars(refresh: Boolean): List<Repository> {
        val result = mutableListOf<Repository>()
        var page = 1
        while (true) {
            val currentPage = page++
            val batch = read("all-stars:$currentPage", refresh) { it.starred(currentPage, 100) }.data
            result += batch
            if (batch.size < 100) return result
        }
    }

    suspend fun profile(login: String, refresh: Boolean = false): Loaded<User> = read("profile:$login", refresh) { api ->
        val user = api.user(login)
        if (user.type == "Organization") api.organization(login).copy(type = "Organization") else user
    }
    suspend fun organizations(page: Int, refresh: Boolean = false): Loaded<List<OrganizationMembership>> { requireLogin(); return read("organizations:$page", refresh) { it.organizations(page) } }
    suspend fun organizationRepositoryCount(login: String, refresh: Boolean = false): Loaded<Int> {
        requireLogin()
        val query = "query(\$login:String!){ organization(login:\$login){ repositories(first:1,ownerAffiliations:[OWNER]){ totalCount } } }"
        val result = read("org-repository-count:$login", refresh) { it.graph(GraphRequest(query, buildJsonObject { put("login", login) })) }
        val organization = GraphResults.requireData(result.data)["organization"]?.takeUnless { it is JsonNull }?.jsonObject
            ?: throw IllegalArgumentException("组织不可访问。")
        val count = organization["repositories"]!!.jsonObject["totalCount"]!!.jsonPrimitive.int
        return Loaded(count, result.cachedAt, result.offline)
    }
    suspend fun languages(fullName: String, refresh: Boolean = false) = read("languages:$fullName", refresh) { api -> parts(fullName).let { api.languages(it.first, it.second) } }
    suspend fun comparison(fullName: String, range: String, refresh: Boolean = false) = read("compare:$fullName:$range", refresh) { api -> parts(fullName).let { api.comparison(it.first, it.second, range) } }
    suspend fun checkSuite(fullName: String, id: Long, refresh: Boolean = false) = read("suite:$fullName:$id", refresh) { api -> parts(fullName).let { api.checkSuite(it.first, it.second, id) } }
    suspend fun checkRuns(fullName: String, id: Long, page: Int, refresh: Boolean = false) = read("checks:$fullName:$id:$page", refresh) { api -> parts(fullName).let { api.checkRuns(it.first, it.second, id, page) } }
    suspend fun checkRun(fullName: String, id: Long, refresh: Boolean = false) = read("check:$fullName:$id", refresh) { api -> parts(fullName).let { api.checkRun(it.first, it.second, id) } }
    suspend fun suiteRuns(fullName: String, id: Long, refresh: Boolean = false) = read("suite-runs:$fullName:$id", refresh) { api -> parts(fullName).let { api.suiteRuns(it.first, it.second, id) } }
    suspend fun targetComment(fullName: String, kind: String, id: Long, refresh: Boolean = false): Loaded<TargetComment> {
        require(kind in listOf("issues", "pulls", "comments"))
        return read("target-comment:$fullName:$kind:$id", refresh) { api -> parts(fullName).let { if (kind == "comments") api.commitComment(it.first, it.second, id) else api.targetComment(it.first, it.second, kind, id) } }
    }
    suspend fun releaseTag(fullName: String, tag: String) = read("release-tag:$fullName:$tag") { api -> parts(fullName).let { api.releaseTag(it.first, it.second, tag) } }
    suspend fun pullReview(fullName: String, number: Int, id: Long, refresh: Boolean = false) = read("pull-review:$fullName:$number:$id", refresh) { api -> parts(fullName).let { api.pullReview(it.first, it.second, number, id) } }
    suspend fun profileRepositories(login: String, organization: Boolean, page: Int, refresh: Boolean = false) = read("profile-repos:$login:$organization:$page", refresh) { if (organization) it.organizationRepositories(login, page) else it.userRepositories(login, page) }
    suspend fun profileStars(login: String, page: Int, refresh: Boolean = false): Loaded<List<Repository>> =
        if (login.equals(account.value?.login, true)) starred(page, refresh) else read("public-stars:$login:$page", refresh) { it.userStars(login, page) }
    suspend fun people(login: String, relation: String, page: Int, refresh: Boolean = false): Loaded<List<User>> = read("people:$login:$relation:$page", refresh) {
        require(relation in listOf("followers", "following", "members"))
        if (relation == "members") it.members(login, page) else it.userRelations(login, relation, page)
    }
    suspend fun contributors(fullName: String, page: Int, refresh: Boolean = false) = read("contributors:$fullName:$page", refresh) { api -> parts(fullName).let { api.contributors(it.first, it.second, page) } }
    suspend fun commit(fullName: String, sha: String, refresh: Boolean = false) = read("commit:$fullName:$sha", refresh) { api -> parts(fullName).let { api.commit(it.first, it.second, sha) } }
    suspend fun commits(fullName: String, ref: String, page: Int, refresh: Boolean = false) = read("commits:$fullName:$ref:$page", refresh) { api -> parts(fullName).let { api.commits(it.first, it.second, ref, page) } }
    suspend fun pullFiles(fullName: String, number: Int, page: Int, refresh: Boolean = false) = read("pull-files:$fullName:$number:$page", refresh) { api -> parts(fullName).let { api.pullFiles(it.first, it.second, number, page) } }
    suspend fun run(fullName: String, id: Long, refresh: Boolean = false) = read("run:$fullName:$id", refresh) { api -> parts(fullName).let { api.run(it.first, it.second, id) } }
    suspend fun jobs(fullName: String, id: Long, page: Int, refresh: Boolean = false) = read("jobs:$fullName:$id:$page", refresh) { api -> parts(fullName).let { api.jobs(it.first, it.second, id, page) } }
    suspend fun advisories(fullName: String, page: Int, refresh: Boolean = false): Loaded<Page<SecurityAdvisory>> {
        val key = "${session.scope}:security:$fullName"
        val cursor = if (page == 1) null else graphCursors["$key:$page"] ?: throw IllegalArgumentException("请刷新安全公告。")
        val result = read("security:$fullName:$page:$cursor", refresh) { api ->
            val response = parts(fullName).let { api.advisories(it.first, it.second, cursor) }
            if (!response.isSuccessful) throw HttpException(response)
            val nextUrl = response.headers()["Link"]?.split(',')?.firstOrNull { it.contains("rel=\"next\"") }?.substringAfter('<')?.substringBefore('>')
            val next = nextUrl?.let { it.toHttpUrl().queryParameter("after") }
            CursorPage(response.body().orEmpty(), next)
        }
        result.data.next?.let { graphCursors["$key:${page + 1}"] = it }
        return Loaded(Page(result.data.items, result.data.next != null), result.cachedAt, result.offline)
    }
    /** Private images are fetched through the Contents API; credentials never go to raw/image hosts. */
    suspend fun privateImage(fullName: String, ref: String, path: String): ByteArray {
        val current = session
        require(current.user != null)
        val bytes = parts(fullName).let { current.api.image(it.first, it.second, encodePath(path), ref).use { response ->
            require(response.contentLength() <= 5 * 1024 * 1024) { "图片过大。" }
            response.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 5 * 1024 * 1024) { "图片过大。" }; output.write(buffer, 0, count) }
                output.toByteArray()
            }
        } }
        require(session === current) { "账号已切换。" }
        return bytes
    }

    suspend fun wiki(fullName: String, slug: String, refresh: Boolean = false): Loaded<WikiDocument> {
        parts(fullName)
        require(!repository(fullName).data.isPrivate) { "私有 Wiki 读取仍需接入 Git 仓库认证，暂未支持。" }
        return read("wiki:$fullName:$slug", refresh) {
            withContext(Dispatchers.IO) {
                val url = "https://github.com/$fullName/wiki" + if (slug.isBlank()) "" else "/${encodePath(slug)}"
                gitHubClient(null).newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                    require(response.isSuccessful) { "暂时无法读取 Wiki（${response.code}）。" }
                    val doc = org.jsoup.Jsoup.parse(response.body!!.string(), url)
                    val body = doc.selectFirst("#wiki-body .markdown-body") ?: throw IllegalArgumentException("GitHub Wiki 页面结构发生变化，暂时无法读取。")
                    body.select("a[href],img[src]").forEach { node -> val attr = if (node.tagName() == "img") "src" else "href"; if (!node.attr(attr).startsWith('#')) node.attr(attr, node.absUrl(attr)) }
                    val pages = doc.select("#wiki-pages-box a[href]").mapNotNull { link ->
                        val uri = java.net.URI(link.absUrl("href")); val prefix = "/$fullName/wiki/"
                        if (uri.host == "github.com" && uri.path.startsWith(prefix)) WikiPage(link.text(), uri.path.removePrefix(prefix)) else null
                    }.distinctBy(WikiPage::slug)
                    WikiDocument(doc.selectFirst(".gh-header-title")?.text() ?: "Wiki", body.html(), pages)
                }
            }
        }
    }

    suspend fun projects(fullName: String, refresh: Boolean = false): Loaded<JsonArray> {
        requireLogin()
        val (owner, repo) = parts(fullName)
        val query = "query(\$owner:String!,\$repo:String!){ repository(owner:\$owner,name:\$repo){ projectsV2(first:50){ nodes{ id title shortDescription readme url closed } } } }"
        val result = read("projects:$fullName", refresh) { it.graph(GraphRequest(query, buildJsonObject { put("owner", owner); put("repo", repo) })) }
        val data = GraphResults.requireData(result.data)["repository"]!!.jsonObject["projectsV2"]!!.jsonObject["nodes"]!!.jsonArray
        return Loaded(data, result.cachedAt, result.offline)
    }

    suspend fun markdown(text: String, fullName: String?): Loaded<String> {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        return read("markdown:gfm:$fullName:$digest") { it.markdown(MarkdownRequest(text, context = fullName)).use { body -> body.string() } }
    }
    suspend fun profilePins(login: String, organization: Boolean, refresh: Boolean = false): Loaded<List<Repository>> {
        requireLogin()
        val field = if (organization) "organization" else "user"
        val query = "query(\$login:String!){ $field(login:\$login){ pinnedItems(first:6,types:[REPOSITORY]){ nodes{ ... on Repository { nameWithOwner } } } } }"
        val result = read("pins:$login", refresh) { it.graph(GraphRequest(query, buildJsonObject { put("login", login) })) }
        val nodes = GraphResults.requireData(result.data)[field]?.jsonObject?.get("pinnedItems")?.jsonObject?.get("nodes")?.jsonArray.orEmpty()
        val repos = coroutineScope { nodes.map { async { repository(it.jsonObject["nameWithOwner"]!!.jsonPrimitive.content, refresh).data } }.awaitAll() }
        return Loaded(repos, result.cachedAt, result.offline)
    }

    private val graphCursors = java.util.concurrent.ConcurrentHashMap<String, String>()
    suspend fun searchDiscussions(query: String, page: Int, refresh: Boolean = false): Loaded<Page<Discussion>> {
        requireLogin()
        val key = "${session.scope}:discussion-search:$query"
        val cursor = if (page == 1) null else graphCursors["$key:$page"] ?: throw IllegalArgumentException("请从第一页重新搜索。")
        val request = GraphRequest("query(\$q:String!,\$cursor:String){ search(query:\$q,type:DISCUSSION,first:30,after:\$cursor){ nodes { ... on Discussion { ${GraphResults.DISCUSSION_FIELDS} } } pageInfo { hasNextPage endCursor } } }", buildJsonObject { put("q", query); put("cursor", cursor?.let(::JsonPrimitive) ?: JsonNull) })
        val result = read("$key:$page", refresh) { it.graph(request) }
        val data = GraphResults.requireData(result.data)["search"]!!.jsonObject
        data["pageInfo"]?.jsonObject?.get("endCursor")?.jsonPrimitive?.contentOrNull?.let { graphCursors["$key:${page + 1}"] = it }
        return Loaded(Page(data["nodes"]!!.jsonArray.filterNot { it is JsonNull }.map { GraphResults.discussion(it.jsonObject) }, data["pageInfo"]!!.jsonObject["hasNextPage"]!!.jsonPrimitive.boolean), result.cachedAt, result.offline)
    }
    suspend fun discussion(fullName: String, number: Int, refresh: Boolean = false): Loaded<Discussion> {
        requireLogin()
        val (owner, repo) = parts(fullName)
        val request = GraphRequest("query(\$owner:String!,\$repo:String!,\$number:Int!){ repository(owner:\$owner,name:\$repo){ discussion(number:\$number){ ${GraphResults.DISCUSSION_FIELDS} } } }", buildJsonObject { put("owner", owner); put("repo", repo); put("number", number) })
        val result = read("discussion:$fullName:$number", refresh) { it.graph(request) }
        val data = GraphResults.requireData(result.data)["repository"]!!.jsonObject["discussion"]
        require(data != null && data !is JsonNull) { "讨论不存在，或当前凭据没有访问权限。" }
        return Loaded(GraphResults.discussion(data.jsonObject), result.cachedAt, result.offline)
    }

    suspend fun searchTopics(query: String, page: Int) = read("search:topics:$query:$page", true) { it.searchTopics(query, page) }
    suspend fun searchCommits(query: String, page: Int, sort: String?, order: String) = read("search:commits:$query:$page:$sort:$order", true) { it.searchCommits(query, page, sort, order) }

    suspend fun searchRepositories(query: String, sort: String?, page: Int, order: String = "desc") = read("search:repos:$query:$sort:$page:$order", true) { it.searchRepositories(query, sort, page, order = order) }
    suspend fun searchIssues(query: String, page: Int, sort: String? = null, order: String = "desc") = read("search:issues:$query:$page:$sort:$order", true) { it.searchIssues(query, page, sort, order) }
    suspend fun searchUsers(query: String, page: Int, sort: String? = null, order: String = "desc") = read("search:users:$query:$page:$sort:$order", true) { it.searchUsers(query, page, sort, order) }
    suspend fun searchCode(query: String, page: Int) = read("search:code:$query:$page", true) { it.searchCode(query, page) }
    suspend fun repository(fullName: String, refresh: Boolean = false) = read("repo:$fullName", refresh) { api -> parts(fullName).let { api.repository(it.first, it.second) } }
    suspend fun readme(fullName: String, refresh: Boolean = false) = read("readme:$fullName", refresh) { api -> parts(fullName).let { api.readme(it.first, it.second) } }
    suspend fun branches(fullName: String) = read("branches:$fullName") { api -> parts(fullName).let { api.branches(it.first, it.second) } }
    suspend fun contents(fullName: String, path: String, ref: String, refresh: Boolean = false): Loaded<List<Content>> = read("contents:$fullName:$ref:$path", refresh) { api ->
        val (owner, repo) = parts(fullName)
        val result = api.contents(owner, repo, encodePath(path), ref)
        if (result is JsonArray) AppJson.decodeFromJsonElement<List<Content>>(result) else listOf(AppJson.decodeFromJsonElement<Content>(result))
    }
    suspend fun issues(fullName: String, pulls: Boolean, state: String, page: Int, refresh: Boolean = false) = read("issues:$fullName:$pulls:$state:$page", refresh) { api -> parts(fullName).let { if (pulls) api.pulls(it.first, it.second, state, page) else api.issues(it.first, it.second, state, page) } }
    suspend fun thread(fullName: String, number: Int, pull: Boolean, refresh: Boolean = false) = read("thread:$fullName:$number:$pull", refresh) { api -> parts(fullName).let { if (pull) api.pull(it.first, it.second, number) else api.issue(it.first, it.second, number) } }
    suspend fun comments(fullName: String, number: Int, page: Int, refresh: Boolean = false) = read("comments:$fullName:$number:$page", refresh) { api -> parts(fullName).let { api.comments(it.first, it.second, number, page) } }
    suspend fun releases(fullName: String, page: Int = 1, refresh: Boolean = false) = read("releases:$fullName:$page", refresh) { api -> parts(fullName).let { api.releases(it.first, it.second, page) } }
    suspend fun release(fullName: String, id: Long, refresh: Boolean = false) = read("release:$fullName:$id", refresh) { api -> parts(fullName).let { api.release(it.first, it.second, id) } }
    suspend fun workflows(fullName: String, page: Int, refresh: Boolean = false) = read("runs:$fullName:$page", refresh) { api -> parts(fullName).let { api.workflows(it.first, it.second, page) } }
    suspend fun starred(page: Int, refresh: Boolean = false): Loaded<List<Repository>> { requireLogin(); return read("stars:$page", refresh) { it.starred(page) } }
    suspend fun myRepositories(page: Int, refresh: Boolean = false): Loaded<List<Repository>> { requireLogin(); return read("mine:$page", refresh) { it.myRepositories(page = page) } }

    suspend fun isStarred(fullName: String): Boolean {
        if (account.value == null) return false
        val (owner, repo) = parts(fullName)
        val result = session.api.isStarred(owner, repo)
        return when (result.code()) { 204 -> true; 404 -> false; else -> throw HttpException(result) }
    }

    suspend fun setStar(fullName: String, value: Boolean) {
        requireLogin()
        val (owner, repo) = parts(fullName)
        (if (value) session.api.star(owner, repo) else session.api.unstar(owner, repo)).requireSuccess()
        cache.clear()
        revisionState.value++
    }

    suspend fun notifications(all: Boolean, page: Int, refresh: Boolean = false): Loaded<List<Notification>> { requireLogin(); return read("inbox:$all:$page", refresh) { it.notifications(all, page) } }
    suspend fun markRead(id: String) { requireLogin(); session.api.readNotification(id).requireSuccess(); cache.clear() }
    suspend fun complete(id: String) { requireLogin(); session.api.completeNotification(id).requireSuccess(); cache.clear() }

    suspend fun feed(followingUsers: Boolean, page: Int = 1, refresh: Boolean = false): Loaded<List<Event>> {
        if (followingUsers) {
            val user = requireLogin()
            return read("feed:github:$page", refresh) { it.receivedEvents(user.login, page) }
        }
        val followed = collections.first().following
        if (followed.isEmpty()) return Loaded(emptyList(), System.currentTimeMillis())
        val limiter = kotlinx.coroutines.sync.Semaphore(3)
        val results = coroutineScope { followed.map { repo -> async { limiter.acquire(); try { read("events:${repo.fullName}:$page", refresh) { api -> parts(repo.fullName).let { api.events(it.first, it.second, page) } } } finally { limiter.release() } } }.awaitAll() }
        return Loaded(results.flatMap { it.data }.distinctBy(Event::id).sortedByDescending(Event::createdAt), results.minOf { it.cachedAt }, results.any { it.offline })
    }

    private fun requireLogin(): User = account.value ?: throw IllegalArgumentException("登录 GitHub 后即可使用此功能。")

    private suspend inline fun <reified T> read(key: String, refresh: Boolean = false, fetch: (GitHubApi) -> T): Loaded<T> {
        val current = session
        val serializer = serializer<T>()
        val cached = cache.read(current.scope, key, serializer)
        if (!refresh && cached != null && System.currentTimeMillis() - cached.cachedAt < 5 * 60 * 1000) return cached
        return try {
            val result = fetch(current.api)
            cache.write(current.scope, key, serializer, result) { session === current }
            Loaded(result, System.currentTimeMillis())
        } catch (e: IOException) {
            cached?.copy(offline = true) ?: throw e
        } catch (e: HttpException) {
            if (e.code() >= 500 && cached != null) cached.copy(offline = true) else throw e
        }
    }

    private fun newSession(account: StoredAccount?): Session {
        val scope = account?.let { "${it.user.id}:" + MessageDigest.getInstance("SHA-256").digest(it.token.toByteArray()).joinToString("") { byte -> "%02x".format(byte) } } ?: "public"
        return Session(apiFactory(account?.token), scope, account?.user)
    }

    companion object {
        fun parts(fullName: String): Pair<String, String> {
            val items = fullName.split('/')
            require(items.size == 2 && items.all { it.matches(Regex("[A-Za-z0-9_.-]+")) && it != "." && it != ".." }) { "仓库地址无效。" }
            return items[0] to items[1]
        }
        fun encodePath(path: String): String {
            require(path.split('/').none { it == "." || it == ".." }) { "文件路径无效。" }
            return path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        }
        fun text(content: Content): String {
            require(content.size <= 1024 * 1024) { "文件较大，请在 GitHub 中查看。" }
            require(content.encoding == "base64" && content.content != null) { "此文件暂不支持直接预览，请在 GitHub 中查看。" }
            val bytes = Base64.getMimeDecoder().decode(content.content)
            require(bytes.none { it == 0.toByte() }) { "这是二进制文件，请下载或在 GitHub 中查看。" }
            return String(bytes, Charsets.UTF_8)
        }
    }
}
