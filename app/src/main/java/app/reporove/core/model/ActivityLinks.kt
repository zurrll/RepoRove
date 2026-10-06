package app.reporove.core.model

/** API identifiers retain their object type; unknown targets retain context instead of opening the repo. */
object ActivityLinks {
    fun notification(value: Notification): GitHubTarget {
        val subject = value.subject
        val latest = subject.latestCommentUrl?.let(GitHubLinks::parse)
        if (latest is GitHubTarget.Comment) return latest
        subject.url?.let(GitHubLinks::parse)?.let {
            return if (it is GitHubTarget.Thread) it.copy(pull = subject.type == "PullRequest" || it.pull) else it
        }
        return GitHubTarget.Context(value.repository.fullName, subject.title, subject.type, subject.url)
    }

    fun event(value: Event): GitHubTarget {
        val p = value.payload
        val repo = value.repo.name
        (p.comment?.htmlUrl ?: p.review?.htmlUrl ?: p.comment?.url)?.let(GitHubLinks::parse)?.let { return it }
        p.pullRequest?.takeIf { it.number > 0 }?.let { return GitHubTarget.Thread(repo, it.number, true) }
        p.issue?.takeIf { it.number > 0 }?.let { return GitHubTarget.Thread(repo, it.number, it.pullRequest != null) }
        p.release?.let { return GitHubTarget.Release(repo, it.id) }
        return when (value.type) {
            "PushEvent" -> {
                val head = p.head?.takeIf { it.matches(Regex("[a-fA-F0-9]{7,40}")) && it.any { c -> c != '0' } }
                val before = p.before?.takeIf { it.matches(Regex("[a-fA-F0-9]{7,40}")) && it.any { c -> c != '0' } }
                if (head != null && before != null) GitHubTarget.Compare(repo, "$before...$head")
                else if (head != null) GitHubTarget.Commit(repo, head)
                else context(value)
            }
            "ForkEvent" -> p.forkee?.let { GitHubTarget.Repo(it.fullName) } ?: context(value)
            "CreateEvent" -> p.ref?.let { GitHubTarget.Ref(repo, it) } ?: GitHubTarget.Repo(repo)
            "WatchEvent", "PublicEvent" -> GitHubTarget.Repo(repo)
            else -> context(value)
        }
    }
    private fun context(value: Event) = GitHubTarget.Context(value.repo.name, value.payload.action ?: "项目活动", value.type)
}
