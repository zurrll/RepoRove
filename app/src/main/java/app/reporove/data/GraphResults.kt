package app.reporove.data

import app.reporove.core.model.*
import kotlinx.serialization.json.*

object GraphResults {
    fun requireData(root: JsonObject): JsonObject {
        require(root["errors"] == null) { "GitHub 未允许读取这些内容，请检查凭据权限后重试。" }
        return root["data"]?.jsonObject ?: throw IllegalArgumentException("GitHub 未返回内容。")
    }
    fun person(value: JsonElement?): User? = value?.takeUnless { it is JsonNull }?.jsonObject?.let { User(login = it.string("login"), avatarUrl = it.string("avatarUrl"), type = if (it.string("__typename") == "Organization") "Organization" else "User") }
    fun discussion(node: JsonObject): Discussion = Discussion(
        id = node.string("id"), number = node["number"]?.jsonPrimitive?.intOrNull ?: 0, title = node.string("title"), body = node.string("body"), url = node.string("url"), author = person(node["author"]),
        repository = node["repository"]?.jsonObject?.string("nameWithOwner").orEmpty(), category = node["category"]?.jsonObject?.string("name").orEmpty(), answered = node["isAnswered"]?.jsonPrimitive?.booleanOrNull == true,
        comments = node["comments"]?.jsonObject?.get("nodes")?.jsonArray.orEmpty().map { it.jsonObject }.map { Comment(it.string("id").fold(0L) { a, c -> a * 31 + c.code }, person(it["author"]) ?: User(login = "ghost"), it.string("body"), it.string("createdAt")) },
    )
    fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    const val DISCUSSION_FIELDS = "id number title body url isAnswered repository { nameWithOwner } category { name } author { login avatarUrl __typename } comments(first:20) { nodes { id body createdAt author { login avatarUrl __typename } } }"
}
