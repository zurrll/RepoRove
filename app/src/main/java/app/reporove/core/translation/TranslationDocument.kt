package app.reporove.core.translation

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

@Serializable data class TranslationPart(val id: Int, val text: String)

/** Translate text nodes, never markup. IDs and all attributes/links/code remain in the original DOM. */
class TranslationDocument(html: String) {
    private val document = Jsoup.parseBodyFragment(html)
    private val nodes = mutableListOf<Pair<TextNode, List<TranslationPart>>>()
    val parts: List<TranslationPart>
    init {
        val collected = mutableListOf<TranslationPart>()
        fun visit(node: Node, protected: Boolean = false) {
            val skip = protected || node.nodeName() in setOf("code", "pre", "script", "style", "svg", "kbd")
            if (node is TextNode && !skip) {
                val text = node.wholeText
                if (text.any(Char::isLetter) && !text.trim().matches(Regex("https?://\\S+"))) {
                    val chunks = split(text).map { TranslationPart(collected.size, it).also(collected::add) }
                    nodes += node to chunks
                }
            } else node.childNodes().forEach { visit(it, skip) }
        }
        visit(document.body()); parts = collected
    }
    fun render(translated: Map<Int, String>): String {
        require(parts.all { translated[it.id] != null }) { "译文不完整。" }
        nodes.forEach { (node, chunks) -> node.text(chunks.joinToString("") { translated.getValue(it.id) }) }
        return document.body().html()
    }
    companion object {
        fun split(text: String, limit: Int = 1800): List<String> {
            val chunks = mutableListOf<String>(); var rest = text
            while (rest.length > limit) {
                val bound = rest.take(limit)
                val cut = bound.indexOfLast { it in ".。!！?？\n " }.takeIf { it > limit / 2 }?.plus(1) ?: limit
                chunks += rest.take(cut); rest = rest.drop(cut)
            }
            if (rest.isNotEmpty()) chunks += rest
            return chunks
        }
        fun validate(expected: List<TranslationPart>, actual: List<TranslationPart>): Map<Int, String> {
            require(actual.size == expected.size && actual.map { it.id }.toSet() == expected.map { it.id }.toSet() && actual.all { it.text.isNotBlank() && it.text.length <= 12_000 }) { "翻译服务返回不完整，请重试。" }
            return actual.associate { it.id to it.text }
        }
    }
}
