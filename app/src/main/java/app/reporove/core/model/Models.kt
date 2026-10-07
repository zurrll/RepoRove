package app.reporove.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long = 0,
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = "",
    val name: String? = null,
    val bio: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("public_repos") val publicRepos: Int? = null,
    val followers: Int? = null,
    val following: Int? = null,
    val type: String = "User",
    val location: String? = null,
    val company: String? = null,
    val blog: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Repository(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: User,
    val description: String? = null,
    val language: String? = null,
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("forks_count") val forks: Int = 0,
    @SerialName("open_issues_count") val openItems: Int = 0,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("pushed_at") val pushedAt: String? = null,
    val topics: List<String> = emptyList(),
    @SerialName("private") val isPrivate: Boolean = false,
    val archived: Boolean = false,
    val fork: Boolean = false,
    val license: License? = null,
    @SerialName("has_issues") val hasIssues: Boolean = true,
    @SerialName("has_wiki") val hasWiki: Boolean = false,
    @SerialName("has_discussions") val hasDiscussions: Boolean = false,
)

@Serializable data class License(@SerialName("spdx_id") val spdxId: String? = null)
@Serializable data class SearchResponse<T>(
    val items: List<T>,
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("incomplete_results") val incomplete: Boolean = false,
)
@Serializable data class Content(
    val name: String,
    val path: String,
    val type: String,
    val size: Long = 0,
    val content: String? = null,
    val encoding: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
)
@Serializable data class Branch(val name: String)
@Serializable data class Issue(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val body: String? = null,
    val user: User = User(login = "ghost"),
    val comments: Int = 0,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("pull_request") val pullRequest: PullLink? = null,
    @SerialName("merged_at") val mergedAt: String? = null,
    val draft: Boolean = false,
    val labels: List<Label> = emptyList(),
) {
    val status: String get() = when {
        mergedAt != null || pullRequest?.mergedAt != null -> "已合并"
        draft -> "草稿"
        state == "open" -> "开放"
        else -> "已关闭"
    }
}
@Serializable data class PullLink(val url: String = "", @SerialName("merged_at") val mergedAt: String? = null)
@Serializable data class Label(val name: String, val color: String = "")
@Serializable data class Comment(val id: Long, val user: User = User(login = "ghost"), val body: String = "", @SerialName("created_at") val createdAt: String)
@Serializable data class Release(
    val id: Long,
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<Asset> = emptyList(),
    val author: User? = null,
    @SerialName("zipball_url") val zipballUrl: String? = null,
    @SerialName("tarball_url") val tarballUrl: String? = null,
)
@Serializable data class Asset(
    val id: Long,
    val name: String,
    val size: Long,
    @SerialName("content_type") val contentType: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)
@Serializable data class WorkflowResponse(@SerialName("workflow_runs") val runs: List<WorkflowRun>)
@Serializable data class WorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("display_title") val title: String = "",
    val status: String,
    val conclusion: String? = null,
    @SerialName("head_branch") val branch: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("updated_at") val updatedAt: String,
)
@Serializable data class Notification(
    val id: String,
    val unread: Boolean,
    val reason: String,
    val repository: Repository,
    val subject: Subject,
    @SerialName("updated_at") val updatedAt: String,
)
@Serializable data class Subject(val title: String, val type: String, val url: String? = null, @SerialName("latest_comment_url") val latestCommentUrl: String? = null)
@Serializable data class Event(
    val id: String,
    val type: String,
    val actor: User,
    val repo: EventRepo,
    @SerialName("created_at") val createdAt: String,
    val payload: EventPayload = EventPayload(),
)
@Serializable data class EventRepo(val name: String)
@Serializable data class EventPayload(
    val release: Release? = null, val action: String? = null, val issue: EventIssue? = null,
    @SerialName("pull_request") val pullRequest: EventIssue? = null,
    val comment: EventComment? = null, val review: EventComment? = null,
    val head: String? = null, val before: String? = null, val ref: String? = null,
    @SerialName("ref_type") val refType: String? = null, val forkee: EventFork? = null,
)
@Serializable data class EventIssue(val number: Int = 0, val title: String = "", @SerialName("html_url") val htmlUrl: String? = null, @SerialName("pull_request") val pullRequest: PullLink? = null)
@Serializable data class EventComment(val id: Long = 0, @SerialName("html_url") val htmlUrl: String? = null, val url: String? = null)
@Serializable data class EventFork(@SerialName("full_name") val fullName: String)
@Serializable data class OrganizationMembership(val organization: User, val state: String, val role: String? = null)
@Serializable data class Comparison(val commits: List<ShortCommit> = emptyList(), val files: List<CommitFile> = emptyList(), @SerialName("total_commits") val totalCommits: Int = 0)
@Serializable data class CheckSuite(val id: Long, val status: String, val conclusion: String? = null, @SerialName("head_sha") val headSha: String = "", val app: CheckApp? = null)
@Serializable data class CheckApp(val name: String)
@Serializable data class CheckRuns(@SerialName("check_runs") val runs: List<CheckRun> = emptyList())
@Serializable data class CheckRun(val id: Long, val name: String, val status: String, val conclusion: String? = null, @SerialName("details_url") val detailsUrl: String? = null, val output: CheckOutput? = null)
@Serializable data class CheckOutput(val title: String? = null, val summary: String? = null, val text: String? = null)
@Serializable data class TargetComment(val id: Long, val user: User? = null, val body: String = "", @SerialName("html_url") val htmlUrl: String, val path: String? = null, @SerialName("diff_hunk") val diffHunk: String? = null, val state: String? = null)
@Serializable data class CodeResult(val name: String, val path: String, val repository: Repository, @SerialName("html_url") val htmlUrl: String, val sha: String = "", val url: String = "")
@Serializable data class Topic(val name: String, @SerialName("display_name") val displayName: String? = null, @SerialName("short_description") val description: String? = null)
@Serializable data class CommitResult(val sha: String, @SerialName("html_url") val htmlUrl: String, val repository: Repository, val commit: CommitMessage, val author: User? = null)
@Serializable data class CommitMessage(val message: String, val author: CommitAuthor? = null)
@Serializable data class CommitAuthor(val name: String = "", val date: String = "")
@Serializable data class CommitDetail(val sha: String, val commit: CommitMessage, val author: User? = null, val files: List<CommitFile> = emptyList())
@Serializable data class CommitFile(val filename: String, val status: String, val additions: Int = 0, val deletions: Int = 0, val patch: String? = null)
@Serializable data class ShortCommit(val sha: String, val commit: CommitMessage, val author: User? = null)
@Serializable data class WorkflowJobs(val jobs: List<WorkflowJob>)
@Serializable data class WorkflowJob(val id: Long, val name: String, val status: String, val conclusion: String? = null, val steps: List<WorkflowStep> = emptyList())
@Serializable data class WorkflowStep(val number: Int, val name: String, val status: String, val conclusion: String? = null)
@Serializable data class SecurityAdvisory(@SerialName("ghsa_id") val id: String, val summary: String, val description: String? = null, val severity: String? = null, @SerialName("html_url") val htmlUrl: String)
@Serializable data class MarkdownRequest(val text: String, val mode: String = "gfm", val context: String? = null)
@Serializable data class GraphRequest(val query: String, val variables: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()))
@Serializable data class Discussion(val id: String, val number: Int, val title: String, val body: String = "", val url: String, val author: User? = null, val repository: String = "", val comments: List<Comment> = emptyList(), val category: String = "", val answered: Boolean = false)

@Serializable data class CursorPage<T>(val items: List<T>, val next: String? = null)

data class Page<T>(val items: List<T>, val hasMore: Boolean, val incomplete: Boolean = false)
data class Loaded<T>(val data: T, val cachedAt: Long, val offline: Boolean = false)

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T, val offline: Boolean = false, val cachedAt: Long = 0) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}
