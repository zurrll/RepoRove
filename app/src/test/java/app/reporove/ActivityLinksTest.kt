package app.reporove

import app.reporove.core.model.*
import app.reporove.core.network.AppJson
import org.junit.Assert.*
import org.junit.Test

class ActivityLinksTest {
    private val repo = Repository(1, "project", "owner/project", User(login = "owner"), htmlUrl = "https://github.com/owner/project")
    @Test fun apiLinksKeepCheckRunSuiteAndWorkflowIdentifiersDistinct() {
        assertEquals(GitHubTarget.Check(repo.fullName, 101, true), GitHubLinks.parse("https://api.github.com/repos/owner/project/check-suites/101"))
        assertEquals(GitHubTarget.Check(repo.fullName, 202, false), GitHubLinks.parse("https://api.github.com/repos/owner/project/check-runs/202"))
        assertEquals(GitHubTarget.Run(repo.fullName, 303), GitHubLinks.parse("https://api.github.com/repos/owner/project/actions/runs/303"))
        assertEquals(GitHubTarget.Run(repo.fullName, 303, 404), GitHubLinks.parse("https://github.com/owner/project/actions/runs/303/job/404"))
        assertNull(GitHubLinks.parse("https://api.github.com.evil.test/repos/owner/project/check-runs/202"))
    }
    @Test fun notificationsLocateCommentsAndPreserveMissingTargetContext() {
        val notification = Notification("1", true, "ci_activity", repo, Subject("Build failed", "CheckSuite", "https://api.github.com/repos/owner/project/check-suites/101"), "")
        assertEquals(GitHubTarget.Check(repo.fullName, 101, true), ActivityLinks.notification(notification))
        val comment = notification.copy(subject = Subject("Discussion", "PullRequest", "https://api.github.com/repos/owner/project/issues/5", "https://api.github.com/repos/owner/project/issues/comments/77"))
        assertEquals(GitHubTarget.Comment(repo.fullName, 77, "issues"), ActivityLinks.notification(comment))
        assertEquals(GitHubTarget.Thread(repo.fullName, 5, true), ActivityLinks.notification(comment.copy(subject = comment.subject.copy(latestCommentUrl = null))))
        assertTrue(ActivityLinks.notification(notification.copy(subject = notification.subject.copy(url = null))) is GitHubTarget.Context)
    }
    @Test fun eventsLocatePushForkReleaseAndPrCommentTargets() {
        fun event(type: String, payload: EventPayload) = Event("1", type, repo.owner, EventRepo(repo.fullName), "", payload)
        val before = "a".repeat(40); val head = "b".repeat(40)
        assertEquals(GitHubTarget.Compare(repo.fullName, "$before...$head"), ActivityLinks.event(event("PushEvent", EventPayload(head = head, before = before))))
        assertEquals(GitHubTarget.Commit(repo.fullName, head), ActivityLinks.event(event("PushEvent", EventPayload(head = head, before = "0".repeat(40)))))
        assertEquals(GitHubTarget.Repo("fork/project"), ActivityLinks.event(event("ForkEvent", EventPayload(forkee = EventFork("fork/project")))))
        assertEquals(GitHubTarget.Thread(repo.fullName, 5, true), ActivityLinks.event(event("IssueCommentEvent", EventPayload(issue = EventIssue(5, pullRequest = PullLink())))))
        assertEquals(GitHubTarget.Comment(repo.fullName, 88, "pulls"), GitHubLinks.parse("https://github.com/owner/project/pull/5#discussion_r88"))
        assertEquals(GitHubTarget.Thread(repo.fullName, 5, true, "pullrequestreview-99"), GitHubLinks.parse("https://api.github.com/repos/owner/project/pulls/5/reviews/99"))
        assertEquals(GitHubTarget.Thread(repo.fullName, 5, true, "pullrequestreview-99"), ActivityLinks.event(event("PullRequestReviewEvent", EventPayload(review = EventComment(99, "https://github.com/owner/project/pull/5#pullrequestreview-99")))))
        assertEquals(GitHubTarget.Ref(repo.fullName, "feature/reading"), ActivityLinks.event(event("CreateEvent", EventPayload(ref = "feature/reading"))))
        assertTrue(ActivityLinks.event(event("DeleteEvent", EventPayload(ref = "gone"))) is GitHubTarget.Context)
    }
    @Test fun releaseArchiveLinksAndExplicitCustomLayoutSurviveDecoding() {
        val release = AppJson.decodeFromString<Release>("""{"id":7,"tag_name":"v1","html_url":"https://github.com/owner/project/releases/tag/v1","zipball_url":"https://api.github.com/repos/owner/project/zipball/v1","assets":[]}""")
        assertNotNull(release.zipballUrl)
        assertEquals(GitHubTarget.ReleaseTag(repo.fullName, "feature/v1"), GitHubLinks.parse("https://github.com/owner/project/releases/tag/feature%2Fv1"))
        val legacy = listOf(RepoModule.Status, RepoModule.Readme, RepoModule.Release, RepoModule.Activity)
        assertEquals(Preferences().modules, Preferences(schemaVersion = 2, modules = legacy).normalized().modules)
        assertEquals(listOf(RepoModule.Status), Preferences(schemaVersion = 2, modules = listOf(RepoModule.Status)).normalized().modules)
    }
}
