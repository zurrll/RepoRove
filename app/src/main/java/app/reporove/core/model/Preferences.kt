package app.reporove.core.model

import kotlinx.serialization.Serializable

enum class MainTab(val label: String) {
    Discover("发现"), Feed("动态"), Inbox("收件箱"), Library("项目库"), Search("搜索"), Profile("我的")
}
enum class RepoTab(val label: String) {
    Overview("概览"), Code("代码"), Issues("Issue"), Pulls("PR"), Releases("发布"), Actions("Actions"), Commits("提交"), Discussions("讨论"), Projects("Projects"), Wiki("Wiki"), Contributors("贡献者"), Security("安全公告")
}
enum class RepoModule(val label: String) {
    Status("状态摘要"), Readme("README"), Release("最新版本"), Activity("最近活动")
}
enum class ThemeMode(val label: String) { System("跟随系统"), Light("浅色"), Dark("深色") }
enum class ThemeId(val label: String) { Clear("清晰"), Paper("纸感") }

@Serializable
data class Preferences(
    val schemaVersion: Int = 3,
    val tabs: List<MainTab> = listOf(MainTab.Discover, MainTab.Feed, MainTab.Inbox, MainTab.Library),
    val home: MainTab = MainTab.Discover,
    val repoTabs: List<RepoTab> = listOf(RepoTab.Overview, RepoTab.Code, RepoTab.Issues, RepoTab.Pulls, RepoTab.Releases),
    val modules: List<RepoModule> = listOf(RepoModule.Readme, RepoModule.Release),
    val compact: Boolean = false,
    val textScale: Float = 1f,
    val theme: ThemeMode = ThemeMode.Light,
    val themeId: ThemeId = ThemeId.Clear,
    val interests: List<String> = emptyList(),
    val inferStarInterests: Boolean = false,
    val mutedTopics: List<String> = emptyList(),
    val mutedRepositories: List<Long> = emptyList(),
    val mutedRepositoryNames: Map<Long, String> = emptyMap(),
) {
    fun normalized(): Preferences {
        val validTabs = tabs.distinct().take(5).ifEmpty { listOf(MainTab.Discover) }
        return copy(
            schemaVersion = 3,
            modules = if (schemaVersion < 3 && modules == listOf(RepoModule.Status, RepoModule.Readme, RepoModule.Release, RepoModule.Activity)) listOf(RepoModule.Readme, RepoModule.Release) else modules.distinct(),
            tabs = validTabs,
            home = home.takeIf { it in validTabs } ?: validTabs.first(),
            repoTabs = repoTabs.distinct(),
            textScale = textScale.takeIf { it.isFinite() }?.coerceIn(.9f, 1.3f) ?: 1f,
            interests = interests.map { it.trim().lowercase() }
                .filter { it.matches(Regex("[a-z0-9][a-z0-9-]{0,49}")) }.distinct().take(20),
            mutedTopics = mutedTopics.map { it.trim().lowercase() }.filter { it.matches(Regex("[a-z0-9][a-z0-9-]{0,49}")) }.distinct(),
            mutedRepositories = mutedRepositories.filter { it > 0 }.distinct(),
        )
    }
}

@Serializable data class CollectionState(
    val later: List<Repository> = emptyList(),
    val following: List<Repository> = emptyList(),
)
@Serializable data class DownloadRecord(val id: Long, val name: String, val repository: String, val createdAt: Long, val fileName: String? = null)

enum class SearchKind(val label: String) { Repositories("仓库"), Code("代码"), Issues("Issue"), Pulls("PR"), Users("用户 / 组织"), Topics("主题"), Commits("提交"), Discussions("讨论") }

object SearchQueries {
    fun build(kind: SearchKind, text: String, language: String?, state: String?): String {
        val query = text.trim()
        val qualifiers = when (kind) {
            SearchKind.Repositories, SearchKind.Code -> listOfNotNull(language?.takeIf { it.matches(Regex("[A-Za-z0-9+#.-]+")) }?.let { "language:$it" })
            SearchKind.Issues -> listOfNotNull("is:issue", state?.takeIf { it in listOf("open", "closed") }?.let { "is:$it" })
            SearchKind.Pulls -> listOfNotNull("is:pr", state?.takeIf { it in listOf("open", "closed", "merged") }?.let { "is:$it" })
            else -> emptyList()
        }
        return (listOf(query) + qualifiers).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun discovery(topic: String?): String = topic?.takeIf { it.matches(Regex("[a-z0-9][a-z0-9-]{0,49}")) }?.let { "topic:$it" } ?: "is:public"
}
