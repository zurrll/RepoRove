package app.reporove.core.model

import java.net.URI
import java.net.URLDecoder

sealed interface GitHubTarget {
    data class Profile(val login: String, val tab: String? = null) : GitHubTarget
    data class Repo(val fullName: String, val tab: RepoTab = RepoTab.Overview) : GitHubTarget
    data class Thread(val fullName: String, val number: Int, val pull: Boolean, val anchor: String = "") : GitHubTarget
    data class File(val fullName: String, val refAndPath: String, val fragment: String?) : GitHubTarget
    data class Ref(val fullName: String, val ref: String) : GitHubTarget
    data class Commit(val fullName: String, val sha: String) : GitHubTarget
    data class Wiki(val fullName: String, val slug: String) : GitHubTarget
    data class Run(val fullName: String, val id: Long, val job: Long = 0) : GitHubTarget
    data class Release(val fullName: String, val id: Long) : GitHubTarget
    data class ReleaseTag(val fullName: String, val tag: String) : GitHubTarget
    data class Check(val fullName: String, val id: Long, val suite: Boolean) : GitHubTarget
    data class Comment(val fullName: String, val id: Long, val kind: String) : GitHubTarget
    data class Compare(val fullName: String, val range: String) : GitHubTarget
    data class Context(val fullName: String, val title: String, val kind: String, val url: String? = null) : GitHubTarget
    data class Discussion(val fullName: String, val number: Int) : GitHubTarget
    data class Topic(val name: String) : GitHubTarget
    data class Collection(val name: String) : GitHubTarget
}

object GitHubLinks {
    private val reserved = setOf("settings", "login", "logout", "signup", "features", "marketplace", "pricing", "explore", "trending", "search", "notifications", "orgs", "sponsors", "about", "security", "apps", "new")
    fun parse(url: String): GitHubTarget? = runCatching {
        val uri = URI(url)
        if (uri.scheme !in listOf("https", "http") || uri.host?.lowercase() !in listOf("github.com", "www.github.com", "api.github.com")) return null
        val p = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty).map { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
        if (uri.host.equals("api.github.com", true)) {
            if (p.size < 5 || p[0] != "repos") return null
            val repo = "${p[1]}/${p[2]}"
            val id = p.getOrNull(4)?.toLongOrNull()
            return when (p[3]) {
                "issues", "pulls" -> if (p[4] == "comments") p.getOrNull(5)?.toLongOrNull()?.let { GitHubTarget.Comment(repo, it, p[3]) }
                    else p[4].toIntOrNull()?.let { number -> GitHubTarget.Thread(repo, number, p[3] == "pulls", if (p[3] == "pulls" && p.getOrNull(5) == "reviews") p.getOrNull(6)?.toLongOrNull()?.let { "pullrequestreview-$it" }.orEmpty() else "") }
                "comments" -> id?.let { GitHubTarget.Comment(repo, it, "comments") }
                "check-suites", "check-runs" -> id?.let { GitHubTarget.Check(repo, it, p[3] == "check-suites") }
                "releases" -> if (p[4] == "tags") GitHubTarget.ReleaseTag(repo, p.drop(5).joinToString("/")) else id?.let { GitHubTarget.Release(repo, it) }
                "commits" -> GitHubTarget.Commit(repo, p[4])
                "discussions" -> p[4].toIntOrNull()?.let { GitHubTarget.Discussion(repo, it) }
                "actions" -> if (p[4] == "runs") p.getOrNull(5)?.toLongOrNull()?.let { GitHubTarget.Run(repo, it) } else null
                "compare" -> GitHubTarget.Compare(repo, p.drop(4).joinToString("/"))
                else -> null
            }
        }
        if (p.size == 2 && p[0] == "topics") return GitHubTarget.Topic(p[1])
        if (p.size == 2 && p[0] == "collections") return GitHubTarget.Collection(p[1])
        if (p.isEmpty() || p[0] in reserved) return null
        if (p.size == 1) return GitHubTarget.Profile(p[0], uri.query?.split('&')?.firstOrNull { it.startsWith("tab=") }?.substringAfter('='))
        val repo = "${p[0]}/${p[1]}"
        if (p.size == 2) return GitHubTarget.Repo(repo)
        val fragment = uri.fragment.orEmpty()
        val commentKind = when { fragment.startsWith("issuecomment-") -> "issues"; fragment.startsWith("discussion_r") -> "pulls"; fragment.startsWith("commitcomment-") -> "comments"; else -> null }
        if (commentKind != null) fragment.removePrefix("issuecomment-").removePrefix("discussion_r").removePrefix("commitcomment-").toLongOrNull()?.let { return GitHubTarget.Comment(repo, it, commentKind) }
        when (p[2]) {
            "issues", "pull" -> p.getOrNull(3)?.toIntOrNull()?.let { GitHubTarget.Thread(repo, it, p[2] == "pull", fragment) } ?: GitHubTarget.Repo(repo, if (p[2] == "pull") RepoTab.Pulls else RepoTab.Issues)
            "pulls" -> GitHubTarget.Repo(repo, RepoTab.Pulls)
            "blob", "tree" -> GitHubTarget.File(repo, p.drop(3).joinToString("/"), uri.fragment)
            "commit" -> p.getOrNull(3)?.let { GitHubTarget.Commit(repo, it) }
            "discussions" -> p.getOrNull(3)?.toIntOrNull()?.let { GitHubTarget.Discussion(repo, it) }
            "releases" -> if (p.getOrNull(3) == "tag") GitHubTarget.ReleaseTag(repo, p.drop(4).joinToString("/")) else GitHubTarget.Repo(repo, RepoTab.Releases)
            "compare" -> GitHubTarget.Compare(repo, p.drop(3).joinToString("/"))
            "runs" -> p.getOrNull(3)?.toLongOrNull()?.let { GitHubTarget.Check(repo, it, false) }
            "checks" -> uri.query?.split('&')?.firstOrNull { it.startsWith("check_run_id=") }?.substringAfter('=')?.toLongOrNull()?.let { GitHubTarget.Check(repo, it, false) }
            "wiki" -> GitHubTarget.Wiki(repo, p.drop(3).joinToString("/"))
            "actions" -> if (p.getOrNull(3) == "runs") p.getOrNull(4)?.toLongOrNull()?.let { GitHubTarget.Run(repo, it, p.getOrNull(6)?.toLongOrNull() ?: 0) } else GitHubTarget.Repo(repo, RepoTab.Actions)
            else -> null
        }
    }.getOrNull()

    /** Match the longest branch first, since branch names can contain slashes. */
    fun fileParts(refAndPath: String, branches: List<String>, defaultBranch: String): Pair<String, String> {
        val branch = branches.filter { refAndPath == it || refAndPath.startsWith("$it/") }.maxByOrNull(String::length)
            ?: refAndPath.substringBefore('/').takeIf { it.matches(Regex("[a-fA-F0-9]{7,40}")) }
            ?: defaultBranch.takeIf { refAndPath == it || refAndPath.startsWith("$it/") }
            ?: refAndPath.substringBefore('/')
        return branch to refAndPath.removePrefix(branch).removePrefix("/")
    }
}
