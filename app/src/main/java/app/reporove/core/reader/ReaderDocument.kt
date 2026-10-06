package app.reporove.core.reader

import app.reporove.data.GitHubRepository
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import java.net.URI

object MarkdownLinks {
    fun resolve(destination: String, fullName: String, ref: String, path: String, image: Boolean): String {
        if (destination.startsWith("#")) return destination
        if (destination.startsWith("//")) return "https:$destination"
        if (runCatching { URI(destination).isAbsolute }.getOrDefault(false)) return destination
        val origin = if (image) "https://raw.githubusercontent.com/$fullName/${GitHubRepository.encodePath(ref)}/" else "https://github.com/$fullName/blob/${GitHubRepository.encodePath(ref)}/"
        return runCatching { if (destination.startsWith('/')) URI(origin).resolve(destination.removePrefix("/")).toString() else URI(origin + GitHubRepository.encodePath(path)).resolve(destination).toString() }.getOrDefault(destination)
    }
    fun images(markdown: String, fullName: String, ref: String, path: String): String = Regex("(!\\[[^\\]]*]\\()([^\\s)]+)").replace(markdown) { it.groupValues[1] + resolve(it.groupValues[2], fullName, ref, path, true) }
}

data class ReaderPalette(val body: String, val muted: String, val background: String, val border: String, val code: String, val link: String, val green: String, val red: String, val purple: String, val amber: String, val dark: Boolean)
data class PreparedDocument(val html: String, val codeBlocks: List<String>)

object ReaderDocument {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).build()
    fun localMarkdown(text: String): String = renderer.render(parser.parse(text))

    fun prepare(html: String, palette: ReaderPalette, textScale: Float, fullName: String?, ref: String, path: String): PreparedDocument {
        val safe = Safelist.relaxed().addTags("details", "summary", "picture", "source", "input", "section", "sup", "sub", "kbd", "s", "del")
            .addAttributes(":all", "class", "id", "dir", "title", "aria-label")
            .addAttributes("input", "type", "checked", "disabled")
            .addAttributes("source", "srcset", "media", "type").addAttributes("img", "width", "height")
            .addAttributes("a", "name").addProtocols("a", "href", "#").preserveRelativeLinks(true)
        // Resolve first, then sanitize protocols. No content-provided script, style, iframe or handler survives.
        val raw = Jsoup.parseBodyFragment(html)
        raw.select("script,style,iframe,object,embed,form,svg").remove()
        raw.select("a[href],img[src],source[srcset]").forEach { node ->
            val attr = when (node.tagName()) { "img" -> "src"; "source" -> "srcset"; else -> "href" }
            if (fullName != null) {
                if (attr == "srcset") node.attr(attr, node.attr(attr).split(',').joinToString(",") { item -> val parts = item.trim().split(Regex("\\s+"), limit = 2); MarkdownLinks.resolve(parts.first(), fullName, ref, path, true) + if (parts.size == 2) " ${parts[1]}" else "" })
                else node.attr(attr, MarkdownLinks.resolve(node.attr(attr), fullName, ref, path, attr == "src"))
            }
        }
        val clean = Jsoup.parseBodyFragment(Jsoup.clean(raw.body().html(), "", safe, org.jsoup.nodes.Document.OutputSettings().prettyPrint(false)))
        // srcset has its own URL grammar; retain only HTTPS image candidates after cleaning.
        clean.select("source[srcset]").forEach { source ->
            val candidates = source.attr("srcset").split(',').filter { it.trim().startsWith("https://") }
            if (candidates.isEmpty()) source.remove() else source.attr("srcset", candidates.joinToString(","))
        }
        clean.select("source[media]").forEach { source ->
            val mode = Regex("prefers-color-scheme:\\s*(dark|light)").find(source.attr("media"))?.groupValues?.get(1)
            if (mode != null) { if ((mode == "dark") != palette.dark) source.remove() else source.removeAttr("media") }
        }
        clean.select("img[src]").forEach { img ->
            val src = img.attr("src")
            if ((!palette.dark && src.contains("gh-dark-mode-only")) || (palette.dark && src.contains("gh-light-mode-only"))) img.remove()
        }
        clean.select("input").forEach { it.attr("type", "checkbox").attr("disabled", "") }
        val headings = mutableMapOf<String, Int>()
        clean.select("h1,h2,h3,h4,h5,h6").forEach { h ->
            if (!h.hasAttr("id")) {
                val slug = h.text().lowercase().replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").replace(' ', '-')
                val count = headings.getOrDefault(slug, 0); headings[slug] = count + 1
                h.attr("id", "user-content-" + slug + if (count > 0) "-$count" else "")
            }
        }
        val code = mutableListOf<String>()
        clean.select("pre").forEachIndexed { index, pre ->
            code += pre.wholeText()
            val language = (pre.selectFirst("code")?.className()?.substringAfter("language-", "")?.substringBefore(' ')
                ?: pre.parent()?.className()?.substringAfter("highlight-source-", "")?.substringBefore(' ')).orEmpty()
            val toolbar = org.jsoup.nodes.Element("div").addClass("code-toolbar")
            toolbar.appendElement("span").text(language.ifEmpty { "代码" })
            toolbar.appendElement("a").attr("href", "https://reader.reporove.invalid/copy/$index").text("复制")
            pre.before(toolbar)
            if (language in listOf("mermaid", "math")) pre.before(org.jsoup.nodes.Element("p").addClass("render-note").text(if (language == "mermaid") "流程图源码（图形渲染待支持）" else "数学公式源码（公式渲染待支持）"))
        }
        clean.select("table").forEach { it.wrap("<div class=table-scroll></div>") }
        val css = """
            :root{color-scheme:${if (palette.dark) "dark" else "light"};--fg:${palette.body};--muted:${palette.muted};--bg:${palette.background};--border:${palette.border};--code:${palette.code};--link:${palette.link};--green:${palette.green};--red:${palette.red};--purple:${palette.purple};--amber:${palette.amber}}
            *{box-sizing:border-box}html,body{margin:0;padding:0;background:var(--bg);color:var(--fg)}body{padding:12px 20px 24px;font-family:system-ui,sans-serif;font-size:${16 * textScale}px;line-height:1.68;overflow-wrap:anywhere}p,ul,ol,dl,blockquote{margin:0 0 18px}li{margin:5px 0}li p{margin:6px 0}h1,h2,h3,h4,h5,h6{line-height:1.3;font-weight:700;margin:28px 0 16px;scroll-margin-top:16px}h1{font-size:1.65em}h2{font-size:1.35em;border-bottom:1px solid var(--border);padding-bottom:10px}h3{font-size:1.15em}strong{font-weight:750}a{color:var(--link);text-decoration:underline;text-underline-offset:3px}a.anchor{display:none}img{max-width:100%;height:auto;vertical-align:middle}img.emoji{height:1.2em;width:1.2em}blockquote{border-left:4px solid var(--border);padding:4px 16px;color:var(--muted)}blockquote p:last-child{margin-bottom:0}code,kbd,pre{font-family:ui-monospace,monospace;font-size:.9em}code{padding:2px 5px;border-radius:4px;background:var(--code)}pre{background:var(--code);border:1px solid var(--border);border-radius:0 0 8px 8px;margin:0 0 20px;padding:16px;overflow-x:auto;white-space:pre;line-height:1.6;overflow-wrap:normal}pre code{padding:0;background:none;font-size:1em}.code-toolbar{display:flex;justify-content:space-between;background:var(--code);border:1px solid var(--border);border-bottom:0;border-radius:8px 8px 0 0;padding:7px 12px;font-size:.78em;color:var(--muted)}.table-scroll{overflow-x:auto;margin-bottom:20px}table{border-collapse:collapse;width:max-content;min-width:100%;font-size:.9em}td,th{border:1px solid var(--border);padding:8px 12px;min-width:80px}th,tr:nth-child(2n){background:var(--code)}hr{height:1px;background:var(--border);border:0;margin:24px 0}details{border:1px solid var(--border);border-radius:8px;margin:16px 0;padding:12px}summary{font-weight:650;cursor:pointer}summary~*{margin-top:12px}input[type=checkbox]{accent-color:var(--green)}.markdown-alert{border-left:4px solid var(--link);padding:12px 16px;margin:20px 0;background:var(--code);border-radius:0 6px 6px 0}.markdown-alert-title{font-weight:700;color:var(--link)}.markdown-alert-tip{border-color:var(--green)}.markdown-alert-tip .markdown-alert-title{color:var(--green)}.markdown-alert-warning{border-color:var(--amber)}.markdown-alert-warning .markdown-alert-title{color:var(--amber)}.markdown-alert-caution{border-color:var(--red)}.markdown-alert-caution .markdown-alert-title{color:var(--red)}.markdown-alert-important{border-color:var(--purple)}.markdown-alert-important .markdown-alert-title{color:var(--purple)}.render-note{font-size:.8em;color:var(--muted)}.pl-k,.pl-smi{color:var(--purple)}.pl-s,.pl-pds{color:var(--link)}.pl-c1,.pl-en{color:var(--green)}.pl-c{color:var(--muted)}.pl-v{color:var(--amber)}::selection{background:var(--link);color:var(--bg)}
        """.trimIndent()
        return PreparedDocument("<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src https:; style-src 'unsafe-inline'; script-src 'none'; base-uri 'none'; form-action 'none'\"><style>$css</style></head><body>${clean.body().html()}</body></html>", code)
    }
}
