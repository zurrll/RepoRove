package app.reporove.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import kotlin.math.max

/** Line numbers are painted outside selectable content; wrapping does not invent extra source lines. */
class CodeTextView(context: Context) : TextView(context) {
    var firstLine = 1
    var gutterColor = 0
    var translateSelection: (String) -> Unit = {}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var source: String? = null
    private var styleKey: String? = null
    init {
        typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setLineSpacing(0f, 1.25f)
        val inset = (context.resources.displayMetrics.density * 60).toInt(); setPadding(inset, 12, 20, 24)
        customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { menu.add(0, TRANSLATE, 0, "AI 翻译"); return true }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId != TRANSLATE) return false
                val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0); val end = maxOf(selectionStart, selectionEnd).coerceAtMost(text.length)
                val selected = text.subSequence(start, end).toString(); mode.finish(); if (selected.isNotBlank()) translateSelection(selected); return true
            }
            override fun onDestroyActionMode(mode: ActionMode) = Unit
        }
    }
    fun bind(content: String, language: String, foreground: Int, comment: Int, string: Int, keyword: Int, number: Int) {
        val key = "$language:$foreground:$comment:$string:$keyword:$number"
        if (source == content && styleKey == key) return
        source = content; styleKey = key; setTextColor(foreground)
        val styled = SpannableString(content)
        // Conservative lexical highlighting. Unknown formats remain readable plain text.
        val known = language.lowercase() in setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "rs", "go", "c", "cpp", "h", "cs", "sh", "bash", "json", "yaml", "yml", "toml", "gradle", "xml", "html", "css", "sql")
        if (known) {
            val regex = Regex("/\\*[\\s\\S]*?\\*/|//[^\\n]*|(?:^|(?<=\\s))#[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b(?:class|fun|val|var|if|else|return|import|package|public|private|protected|static|void|new|const|let|function|async|await|def|from|for|while|in|try|catch|except|throw|true|false|null|None|struct|enum|interface|override|suspend|when|object|export|extends|implements)\\b|\\b\\d+(?:\\.\\d+)?\\b", RegexOption.MULTILINE)
            regex.findAll(content).forEach { match ->
                val token = match.value.trimStart(); val color = when { token.startsWith("//") || token.startsWith("/*") || token.startsWith('#') -> comment; token.startsWith('"') || token.startsWith('\'') -> string; token.firstOrNull()?.isDigit() == true -> number; else -> keyword }
                styled.setSpan(ForegroundColorSpan(color), match.range.first, match.range.last + 1, 0)
            }
        }
        text = styled
    }
    override fun onDraw(canvas: Canvas) {
        val layout = layout
        if (layout != null) {
            paint.color = gutterColor; paint.textSize = textSize * .82f; paint.typeface = Typeface.MONOSPACE; paint.textAlign = Paint.Align.RIGHT
            var sourceLine = firstLine
            for (i in 0 until layout.lineCount) {
                val start = layout.getLineStart(i)
                if (i == 0 || start > 0 && text[start - 1] == '\n') {
                    canvas.drawText(sourceLine.toString(), paddingLeft - 16f * resources.displayMetrics.density, (paddingTop + layout.getLineBaseline(i)).toFloat(), paint)
                    sourceLine++
                }
            }
        }
        super.onDraw(canvas)
    }
    private var searchSpan: android.text.style.BackgroundColorSpan? = null
    fun markMatch(start: Int, length: Int, color: Int) {
        val value = text as? android.text.Spannable ?: return
        searchSpan?.let(value::removeSpan)
        if (start < 0 || length <= 0 || start + length > value.length) return
        searchSpan = android.text.style.BackgroundColorSpan((color and 0x00ffffff) or 0x55000000)
        value.setSpan(searchSpan, start, start + length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    fun lineY(line: Int): Int {
        val offset = line - firstLine
        val start = if (offset <= 0) 0 else text.indices.filter { text[it] == '\n' }.getOrNull(offset - 1)?.plus(1) ?: 0
        val layout = layout ?: return paddingTop
        return layout.getLineTop(layout.getLineForOffset(start)) + paddingTop
    }
    companion object { private const val TRANSLATE = 0x525654 }
}
