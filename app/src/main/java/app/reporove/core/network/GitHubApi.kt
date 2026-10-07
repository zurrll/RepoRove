package app.reporove.core.network

import app.reporove.core.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit

val AppJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

interface GitHubApi {
    @retrofit2.http.Streaming @GET("repos/{owner}/{repo}/zipball/{ref}")
    suspend fun archive(@Path("owner") owner: String, @Path("repo") repo: String, @Path("ref") ref: String): okhttp3.ResponseBody
    @GET("user") suspend fun me(): User
    @GET("user/memberships/orgs") suspend fun organizations(@Query("page") page: Int): List<OrganizationMembership>
    @GET("repos/{owner}/{repo}/languages") suspend fun languages(@Path("owner") owner: String, @Path("repo") repo: String): Map<String, Long>
    @GET("repos/{owner}/{repo}/compare/{range}") suspend fun comparison(@Path("owner") owner: String, @Path("repo") repo: String, @Path("range") range: String): Comparison
    @GET("repos/{owner}/{repo}/check-suites/{id}") suspend fun checkSuite(@Path("owner") owner: String, @Path("repo") repo: String, @Path("id") id: Long): CheckSuite
    @GET("repos/{owner}/{repo}/check-suites/{id}/check-runs") suspend fun checkRuns(@Path("owner") owner: String, @Path("repo") repo: String, @Path("id") id: Long, @Query("page") page: Int): CheckRuns
    @GET("repos/{owner}/{repo}/check-runs/{id}") suspend fun checkRun(@Path("owner") owner: String, @Path("repo") repo: String, @Path("id") id: Long): CheckRun
    @GET("repos/{owner}/{repo}/{kind}/comments/{id}") suspend fun targetComment(@Path("owner") owner: String, @Path("repo") repo: String, @Path("kind") kind: String, @Path("id") id: Long): TargetComment
    @GET("repos/{owner}/{repo}/comments/{id}") suspend fun commitComment(@Path("owner") owner: String, @Path("repo") repo: String, @Path("id") id: Long): TargetComment
    @GET("repos/{owner}/{repo}/pulls/{number}/reviews/{id}") suspend fun pullReview(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int, @Path("id") id: Long): TargetComment
    @GET("repos/{owner}/{repo}/actions/runs") suspend fun suiteRuns(@Path("owner") owner: String, @Path("repo") repo: String, @Query("check_suite_id") id: Long): WorkflowResponse
    @GET("repos/{owner}/{repo}/releases/tags/{tag}") suspend fun releaseTag(@Path("owner") owner: String, @Path("repo") repo: String, @Path("tag") tag: String): Release
    @GET("users/{login}") suspend fun user(@Path("login") login: String): User
    @GET("orgs/{login}") suspend fun organization(@Path("login") login: String): User
    @GET("users/{login}/repos") suspend fun userRepositories(@Path("login") login: String, @Query("page") page: Int, @Query("sort") sort: String = "updated"): List<Repository>
    @GET("orgs/{login}/repos") suspend fun organizationRepositories(@Path("login") login: String, @Query("page") page: Int, @Query("sort") sort: String = "updated"): List<Repository>
    @GET("users/{login}/starred") suspend fun userStars(@Path("login") login: String, @Query("page") page: Int): List<Repository>
    @GET("users/{login}/{relation}") suspend fun userRelations(@Path("login") login: String, @Path("relation") relation: String, @Query("page") page: Int): List<User>
    @GET("orgs/{login}/public_members") suspend fun members(@Path("login") login: String, @Query("page") page: Int): List<User>
    @GET("repos/{owner}/{repo}/contributors") suspend fun contributors(@Path("owner") owner: String, @Path("repo") repo: String, @Query("page") page: Int): List<User>
    @GET("repos/{owner}/{repo}/commits/{sha}") suspend fun commit(@Path("owner") owner: String, @Path("repo") repo: String, @Path("sha") sha: String): CommitDetail
    @GET("repos/{owner}/{repo}/commits") suspend fun commits(@Path("owner") owner: String, @Path("repo") repo: String, @Query("sha") ref: String, @Query("page") page: Int): List<ShortCommit>
    @GET("repos/{owner}/{repo}/pulls/{number}/files") suspend fun pullFiles(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int, @Query("page") page: Int): List<CommitFile>
    @GET("repos/{owner}/{repo}/actions/runs/{id}") suspend fun run(@Path("owner") owner: String, @Path("repo") repo: String, @Path("id") id: Long): WorkflowRun
    @GET("repos/{owner}/{repo}/actions/runs/{id}/jobs") suspend fun jobs(@Path("owner") owner: String, @Path("repo") repo: String, @Path("id") id: Long, @Query("page") page: Int): WorkflowJobs
    @GET("repos/{owner}/{repo}/security-advisories") suspend fun advisories(@Path("owner") owner: String, @Path("repo") repo: String, @Query("after") after: String? = null, @Query("state") state: String = "published"): Response<List<SecurityAdvisory>>
    @POST("markdown") suspend fun markdown(@Body request: MarkdownRequest): okhttp3.ResponseBody
    @POST("graphql") suspend fun graph(@Body request: GraphRequest): kotlinx.serialization.json.JsonObject
    @GET("search/topics") suspend fun searchTopics(@Query("q") query: String, @Query("page") page: Int): SearchResponse<Topic>
    @GET("search/commits") suspend fun searchCommits(@Query("q") query: String, @Query("page") page: Int, @Query("sort") sort: String? = null, @Query("order") order: String = "desc"): SearchResponse<CommitResult>
    @GET("search/repositories") suspend fun searchRepositories(@Query("q") query: String, @Query("sort") sort: String? = null, @Query("page") page: Int = 1, @Query("per_page") size: Int = 30, @Query("order") order: String = "desc"): SearchResponse<Repository>
    @GET("search/issues") suspend fun searchIssues(@Query("q") query: String, @Query("page") page: Int = 1, @Query("sort") sort: String? = null, @Query("order") order: String = "desc"): SearchResponse<Issue>
    @GET("search/users") suspend fun searchUsers(@Query("q") query: String, @Query("page") page: Int = 1, @Query("sort") sort: String? = null, @Query("order") order: String = "desc"): SearchResponse<User>
    @GET("search/code") suspend fun searchCode(@Query("q") query: String, @Query("page") page: Int = 1): SearchResponse<CodeResult>
    @GET("repos/{owner}/{repo}") suspend fun repository(@Path("owner") owner: String, @Path("repo") repo: String): Repository
    @GET("repos/{owner}/{repo}/readme") suspend fun readme(@Path("owner") owner: String, @Path("repo") repo: String, @Query("ref") ref: String? = null): Content
    @GET("repos/{owner}/{repo}/contents/{path}") suspend fun contents(@Path("owner") owner: String, @Path("repo") repo: String, @Path(value = "path", encoded = true) path: String, @Query("ref") ref: String): JsonElement
    @Headers("Accept: application/vnd.github.raw+json")
    @GET("repos/{owner}/{repo}/contents/{path}") suspend fun image(@Path("owner") owner: String, @Path("repo") repo: String, @Path(value = "path", encoded = true) path: String, @Query("ref") ref: String): okhttp3.ResponseBody
    @GET("repos/{owner}/{repo}/branches") suspend fun branches(@Path("owner") owner: String, @Path("repo") repo: String, @Query("per_page") size: Int = 100): List<Branch>
    @GET("repos/{owner}/{repo}/issues") suspend fun issues(@Path("owner") owner: String, @Path("repo") repo: String, @Query("state") state: String = "open", @Query("page") page: Int = 1): List<Issue>
    @GET("repos/{owner}/{repo}/pulls") suspend fun pulls(@Path("owner") owner: String, @Path("repo") repo: String, @Query("state") state: String = "open", @Query("page") page: Int = 1): List<Issue>
    @GET("repos/{owner}/{repo}/issues/{number}") suspend fun issue(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int): Issue
    @GET("repos/{owner}/{repo}/pulls/{number}") suspend fun pull(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int): Issue
    @GET("repos/{owner}/{repo}/issues/{number}/comments") suspend fun comments(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int, @Query("page") page: Int = 1): List<Comment>
    @GET("repos/{owner}/{repo}/releases") suspend fun releases(@Path("owner") owner: String, @Path("repo") repo: String, @Query("page") page: Int = 1): List<Release>
    @GET("repos/{owner}/{repo}/releases/{id}") suspend fun release(@Path("owner") owner: String, @Path("repo") repo: String, @Path("id") id: Long): Release
    @GET("repos/{owner}/{repo}/actions/runs") suspend fun workflows(@Path("owner") owner: String, @Path("repo") repo: String, @Query("page") page: Int = 1): WorkflowResponse
    @GET("user/starred") suspend fun starred(@Query("page") page: Int = 1, @Query("per_page") size: Int = 30): List<Repository>
    @GET("user/repos") suspend fun myRepositories(@Query("sort") sort: String = "updated", @Query("page") page: Int = 1): List<Repository>
    @GET("user/starred/{owner}/{repo}") suspend fun isStarred(@Path("owner") owner: String, @Path("repo") repo: String): Response<Unit>
    @PUT("user/starred/{owner}/{repo}") suspend fun star(@Path("owner") owner: String, @Path("repo") repo: String): Response<Unit>
    @DELETE("user/starred/{owner}/{repo}") suspend fun unstar(@Path("owner") owner: String, @Path("repo") repo: String): Response<Unit>
    @GET("notifications") suspend fun notifications(@Query("all") all: Boolean = false, @Query("page") page: Int = 1): List<Notification>
    @PATCH("notifications/threads/{id}") suspend fun readNotification(@Path("id") id: String): Response<Unit>
    @DELETE("notifications/threads/{id}") suspend fun completeNotification(@Path("id") id: String): Response<Unit>
    @GET("repos/{owner}/{repo}/events") suspend fun events(@Path("owner") owner: String, @Path("repo") repo: String, @Query("page") page: Int = 1): List<Event>
    @GET("users/{login}/received_events") suspend fun receivedEvents(@Path("login") login: String, @Query("page") page: Int = 1): List<Event>
}

/** A fixed session token is never forwarded to another host, including redirects. */
class GitHubHeaders(private val token: String?, private val apiHost: String = "api.github.com") : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val request = original.newBuilder().apply {
            removeHeader("Authorization")
            if (original.url.host == apiHost) {
                if (original.header("Accept") == null) header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header("User-Agent", "RepoRove-Android")
                if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
            }
        }.build()
        return chain.proceed(request)
    }
}

fun gitHubClient(token: String?): OkHttpClient = OkHttpClient.Builder()
    .addNetworkInterceptor(GitHubHeaders(token))
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(45, TimeUnit.SECONDS)
    .build()

fun createApi(token: String?, baseUrl: String = "https://api.github.com/", client: OkHttpClient = gitHubClient(token)): GitHubApi = Retrofit.Builder()
    .baseUrl(baseUrl).client(client)
    .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
    .build().create(GitHubApi::class.java)

fun userMessage(error: Throwable): String = when (error) {
    is retrofit2.HttpException -> when (error.code()) {
        401 -> "登录已失效，请重新登录。"
        403 -> if (error.response()?.headers()?.get("X-RateLimit-Remaining") == "0") "GitHub 请求额度已用完，请稍后重试。" else "当前账号没有此操作的权限，或凭据类型不支持此接口。"
        404 -> "内容不存在，或当前账号没有访问权限。"
        422 -> "请求条件不受支持，请调整关键词或筛选条件。"
        429 -> "请求过于频繁，请稍后重试。"
        in 500..599 -> "GitHub 服务暂时不可用，请稍后重试。"
        else -> "请求失败（${error.code()}），请重试。"
    }
    is kotlinx.serialization.SerializationException -> "GitHub 返回的内容格式暂时无法读取，请刷新重试。"
    is IOException -> "网络连接失败，请检查网络后重试。"
    is IllegalArgumentException -> error.message ?: "内容无法显示。"
    else -> "暂时无法加载，请重试。"
}

fun Response<Unit>.requireSuccess() {
    if (!isSuccessful) throw retrofit2.HttpException(this)
}
