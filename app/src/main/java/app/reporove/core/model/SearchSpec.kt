package app.reporove.core.model

/** Only qualifiers/sorts accepted by the selected public API are offered. */
data class SearchField(val key: String, val label: String, val hint: String = "", val options: List<Pair<String, String>> = emptyList())
data class SearchSort(val key: String?, val label: String)
data class SearchSpec(val kind: SearchKind, val text: String = "", val scope: String = "", val filters: Map<String, String> = emptyMap(), val sort: String? = null, val order: String = "desc") {
    fun query(): String {
        val selected = filters.filterValues(String::isNotBlank).toMutableMap()
        val scoped = scope.trim().takeIf { it.matches(Regex("(repo|user|org):[A-Za-z0-9_.\\-/]+")) }
        if (scoped != null) selected[scoped.substringBefore(':')] = scoped.substringAfter(':')
        val tokens = Regex("(?:[^\\s\"]|\"[^\"]*\")+").findAll(text.trim()).map { it.value }.filterNot { token ->
            val key = token.substringBefore(':', "")
            val value = token.substringAfter(':', "")
            val typedThread = kind in listOf(SearchKind.Issues, SearchKind.Pulls) && key in listOf("is", "type") && value in listOf("issue", "pr")
            val scopedConflict = scoped != null && key in listOf("repo", "user", "org")
            val selectedConflict = if (key == "is" && selected[key] != null) {
                val groups = listOf(setOf("open", "closed", "merged", "unmerged"), setOf("public", "private"), setOf("issue", "pr"))
                value == selected[key] || groups.any { value in it && selected[key] in it }
            } else key in selected
            typedThread || scopedConflict || selectedConflict
        }.toList()
        val type = when (kind) { SearchKind.Issues -> "is:issue"; SearchKind.Pulls -> "is:pr"; else -> null }
        return (tokens + listOfNotNull(type) + selected.map { (key, value) -> "$key:${quote(value.trim())}" }).joinToString(" ")
    }
    companion object {
        fun quote(value: String): String = if (value.any(Char::isWhitespace) && !value.startsWith('"')) "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"" else value
    }
}

object SearchCapabilities {
    private fun choices(vararg pairs: Pair<String, String>) = pairs.toList()
    private val state = SearchField("is", "状态", options = choices("open" to "开放", "closed" to "关闭"))
    private val dates = listOf(SearchField("created", "创建时间", ">=2025-01-01"), SearchField("updated", "更新时间", "2025-01-01..2026-01-01"))
    fun fields(kind: SearchKind): List<SearchField> = when (kind) {
        SearchKind.Repositories -> listOf(
            SearchField("in", "搜索位置", options = choices("name" to "名称", "description" to "描述", "readme" to "README", "name,description,readme" to "以上全部")),
            SearchField("topic", "主题", "android"), SearchField("language", "语言"), SearchField("stars", "Star 数量", ">100 或 10..100"), SearchField("forks", "Fork 数量", ">10"),
            SearchField("license", "许可证", "mit / apache-2.0 / SPDX ID"), SearchField("archived", "归档", options = choices("false" to "未归档", "true" to "已归档")),
            SearchField("fork", "Fork 仓库", options = choices("true" to "包括 Fork", "only" to "只看 Fork")), SearchField("is", "可见范围", options = choices("public" to "公开", "private" to "私有")),
            SearchField("size", "仓库大小（KB）", ">1000"), SearchField("pushed", "最近推送", ">=2026-01-01"), SearchField("created", "创建时间", ">=2025-01-01"),
        )
        SearchKind.Code -> listOf(SearchField("language", "语言"), SearchField("path", "路径", "src/main"), SearchField("filename", "文件名", "README.md"), SearchField("extension", "扩展名", "kt"), SearchField("size", "文件大小（字节）", "100..10000"), SearchField("in", "匹配位置", options = choices("file" to "内容", "path" to "路径", "file,path" to "内容和路径")))
        SearchKind.Issues, SearchKind.Pulls -> listOf(
            if (kind == SearchKind.Pulls) state.copy(options = state.options + choices("merged" to "已合并", "unmerged" to "未合并")) else state,
            SearchField("author", "作者", "登录名"), SearchField("assignee", "指派给", "登录名"), SearchField("mentions", "提及用户", "登录名"), SearchField("commenter", "评论者", "登录名"), SearchField("involves", "参与用户", "登录名"),
            SearchField("label", "标签", "bug"), SearchField("milestone", "里程碑"), SearchField("comments", "评论数量", ">10"), SearchField("in", "搜索位置", options = choices("title" to "标题", "body" to "正文", "comments" to "评论")),
        ) + dates + listOf(SearchField("closed", "关闭时间", ">=2026-01-01")) + if (kind == SearchKind.Pulls) listOf(SearchField("draft", "草稿", options = choices("true" to "草稿", "false" to "非草稿")), SearchField("review", "评审状态", options = choices("none" to "未评审", "required" to "需要评审", "approved" to "已批准", "changes_requested" to "需要修改")), SearchField("review-requested", "请求评审用户"), SearchField("base", "目标分支"), SearchField("head", "来源分支"), SearchField("merged", "合并时间", ">=2026-01-01")) else emptyList()
        SearchKind.Users -> listOf(SearchField("type", "账号类型", options = choices("user" to "用户", "org" to "组织")), SearchField("in", "搜索位置", options = choices("login" to "登录名", "name" to "姓名", "email" to "邮箱")), SearchField("location", "位置", "Shanghai"), SearchField("language", "仓库语言"), SearchField("repos", "公开仓库数量", ">10"), SearchField("followers", "关注者数量", ">100"), SearchField("created", "加入时间", ">=2020-01-01"))
        SearchKind.Topics -> listOf(SearchField("is", "主题类型", options = choices("featured" to "精选", "curated" to "已维护")), SearchField("repositories", "仓库数量", ">100"), SearchField("created", "创建时间", ">=2020-01-01"))
        SearchKind.Commits -> listOf(SearchField("author", "GitHub 作者", "登录名"), SearchField("committer", "GitHub 提交者", "登录名"), SearchField("author-name", "作者姓名"), SearchField("author-email", "作者邮箱"), SearchField("committer-name", "提交者姓名"), SearchField("committer-email", "提交者邮箱"), SearchField("author-date", "作者日期", ">=2026-01-01"), SearchField("committer-date", "提交日期", ">=2026-01-01"), SearchField("merge", "合并提交", options = choices("true" to "合并", "false" to "非合并")), SearchField("hash", "提交 SHA"))
        SearchKind.Discussions -> listOf(SearchField("in", "搜索位置", options = choices("title" to "标题", "body" to "正文", "comments" to "评论")), SearchField("author", "作者"), SearchField("commenter", "评论者"), SearchField("mentions", "提及"), SearchField("category", "讨论分类"), SearchField("is", "是否已回答", options = choices("answered" to "已回答", "unanswered" to "未回答"))) + dates
    }
    fun sorts(kind: SearchKind): List<SearchSort> = listOf(SearchSort(null, "最佳匹配")) + when (kind) {
        SearchKind.Repositories -> listOf(SearchSort("stars", "Star 数"), SearchSort("forks", "Fork 数"), SearchSort("updated", "更新时间"), SearchSort("help-wanted-issues", "需要帮助的 Issue"))
        SearchKind.Issues, SearchKind.Pulls -> listOf(SearchSort("created", "创建时间"), SearchSort("updated", "更新时间"), SearchSort("comments", "评论数"), SearchSort("reactions", "反应数"), SearchSort("interactions", "互动数"))
        SearchKind.Users -> listOf(SearchSort("followers", "关注者"), SearchSort("repositories", "仓库数"), SearchSort("joined", "加入时间"))
        SearchKind.Commits -> listOf(SearchSort("author-date", "作者日期"), SearchSort("committer-date", "提交日期"))
        else -> emptyList()
    }
    fun scoped(kind: SearchKind) = kind !in listOf(SearchKind.Users, SearchKind.Topics)
}
