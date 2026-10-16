package app.reporove.core.translation

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.security.MessageDigest

/** A complete semantic block, with non-translatable inline content kept locally. */
data class TranslationBlock(val id: String, val text: String, val protected: List<String>) {
    fun valid(result: String): Boolean = result.isNotBlank() && result.length <= 24000 &&
        protected.indices.all { index -> Regex(Regex.escape(token(index))).findAll(result).count() == 1 } &&
        Regex("__RR_\\d+__").findAll(result).all { it.value in protected.indices.map(::token) }
    fun render(result: String): String {
        require(valid(result)) { "译文未保留代码或链接，请重试。" }
        var output = org.jsoup.nodes.TextNode(result).outerHtml()
        protected.forEachIndexed { index, html -> output = output.replace(token(index), html) }
        return output
    }
    companion object { fun token(index: Int) = "__RR_${index}__" }
}
data class BilingualDocument(val html: String, val blocks: List<TranslationBlock>)

object TranslationDocument {
    fun prepare(html: String): BilingualDocument {
        val doc = Jsoup.parse(html)
        val blocks = mutableListOf<TranslationBlock>()
        val tags = setOf("p", "li", "dt", "dd", "summary", "h1", "h2", "h3", "h4", "h5", "h6")
        doc.select("p,li,dt,dd,summary,h1,h2,h3,h4,h5,h6,div.table-cell").toList().forEach { element ->
            if (element.parents().any { it.tagName() in setOf("pre", "code", "kbd") } || element.classNames().any { it in setOf("render-note", "table-hint", "markdown-alert-title") }) return@forEach
            // Nested paragraphs/list items are independent blocks. Only wrap direct inline runs.
            val runs = mutableListOf<MutableList<Node>>()
            var run = mutableListOf<Node>()
            element.childNodes().toList().forEach { node ->
                if (node is Element && (node.isBlock || node.tagName() in tags)) {
                    if (run.isNotEmpty()) runs += run
                    run = mutableListOf()
                } else run += node
            }
            if (run.isNotEmpty()) runs += run
            runs.forEach runLoop@ { nodes ->
                val protected = mutableListOf<String>()
                fun text(node: Node): String = when {
                    node is TextNode -> node.wholeText
                    node is Element && node.tagName() in setOf("code", "kbd", "samp", "a") -> {
                        if (node.text().isBlank()) "" else TranslationBlock.token(protected.size).also { protected += node.clone().apply {
                            removeAttr("id"); removeAttr("name")
                            select("[id],[name]").forEach { it.removeAttr("id"); it.removeAttr("name") }
                        }.outerHtml() }
                    }
                    node is Element && node.tagName() in setOf("img", "picture", "input", "svg") -> ""
                    node is Element && node.tagName() == "br" -> "\n"
                    else -> node.childNodes().joinToString("") { text(it) }
                }
                val source = nodes.joinToString("") { text(it) }.trim()
                val prose = source.replace(Regex("__RR_\\d+__"), "").replace(Regex("https?://\\S+"), "")
                val foreign = prose.count { it.isLetter() && it !in '\u4e00'..'\u9fff' }
                val chinese = prose.count { it in '\u4e00'..'\u9fff' }
                if (foreign < 3 || chinese > foreign) return@runLoop
                if (source.length > 20000) {
                    nodes.last().after(Element("span").addClass("rr-translation").addClass("rr-error").text("这段内容过长，暂时保留原文。"))
                    return@runLoop
                }
                val id = "rr-${blocks.size}"
                val span = Element("span").attr("data-rr-source", id)
                nodes.first().before(span)
                nodes.forEach(span::appendChild)
                blocks += TranslationBlock(id, source, protected)
            }
        }
        doc.head().appendElement("style").text("body.rr-bilingual{overflow-anchor:none}.rr-translation{display:block;font:inherit;font-weight:400;color:var(--fg);border-inline-start:2px solid var(--border);padding-inline-start:.75em;margin:.35em 0 .8em;line-height:1.65}.rr-translation a{color:var(--link)}.rr-translation code{font-size:.9em}.rr-translation.rr-error{font-size:.85em;color:var(--muted)}h1 .rr-translation,h2 .rr-translation,h3 .rr-translation{font-size:.8em;line-height:1.5}body:not(.rr-bilingual) .rr-translation{display:none}")
        doc.outputSettings().prettyPrint(false)
        return BilingualDocument(doc.outerHtml(), blocks)
    }
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
