package app.reporove.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.*
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.reporove.core.model.Preferences
import app.reporove.core.reader.ReaderDocument
import app.reporove.core.reader.ReaderPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

// Retained source-compatible helper for existing tests and callers.
typealias MarkdownLinks = app.reporove.core.reader.MarkdownLinks

@Composable fun MarkdownBody(markdown: String, prefs: Preferences, modifier: Modifier = Modifier, fullName: String? = null, ref: String = "main", path: String = "README.md", preview: Boolean = false, fill: Boolean = false, renderedHtml: String? = null, initialAnchor: String = "") {
    val context = LocalContext.current
    val app = LocalAppModel.current
    val nav = LocalAppNavigation.current
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    fun Color.css() = "#%06x".format(toArgb() and 0xffffff)
    val palette = ReaderPalette(colors.onSurface.css(), colors.onSurfaceVariant.css(), colors.background.css(), colors.outlineVariant.css(), semantic.code.css(), semantic.link.css(), semantic.success.css(), semantic.danger.css(), semantic.merged.css(), semantic.warning.css(), semantic.dark)
    var isPrivate by remember(fullName) { mutableStateOf(false) }
    val privateDocument by rememberUpdatedState(isPrivate)
    val imageCache = remember(fullName, ref) { object : LinkedHashMap<String, ByteArray>(16, .75f, true) {} }
    var html by remember(markdown, fullName, renderedHtml) { mutableStateOf<String?>(renderedHtml) }
    var fallback by remember(markdown, fullName) { mutableStateOf(false) }
    var offline by remember(markdown, fullName) { mutableStateOf(false) }
    LaunchedEffect(markdown, fullName, renderedHtml) {
        if (renderedHtml != null) { html = renderedHtml; return@LaunchedEffect }
        try {
            isPrivate = fullName?.let { app.container.repository.repository(it).data.isPrivate } ?: false
            val result = app.container.repository.markdown(markdown, fullName); html = result.data; offline = result.offline }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { html = withContext(Dispatchers.Default) { ReaderDocument.localMarkdown(markdown) }; fallback = true }
    }
    val document by produceState<app.reporove.core.reader.PreparedDocument?>(null, html, palette, prefs.textScale, fullName, ref, path) {
        val source = html
        value = if (source == null) null else withContext(Dispatchers.Default) { ReaderDocument.prepare(source, palette, prefs.textScale, fullName, ref, path) }
    }
    var height by remember(markdown) { mutableStateOf(200) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var previousDocument by remember { mutableStateOf<String?>(null) }
    var previousBody by remember { mutableStateOf<String?>(null) }
    var readingY by rememberSaveable(fullName, ref, path) { mutableStateOf(0) }
    var anchorApplied by rememberSaveable(fullName, ref, path, initialAnchor) { mutableStateOf(false) }
    var restoringY by remember { mutableStateOf(0) }
    var loadingDocument by remember { mutableStateOf(true) }
    val currentDocument by rememberUpdatedState(document)
    fun measure(view: WebView) {
        if (!fill && !preview) view.evaluateJavascript("Math.ceil(document.body.getBoundingClientRect().height)") { value -> value.toDoubleOrNull()?.let { height = it.toInt() } }
    }
    val navigate by rememberUpdatedState<(String) -> Unit> { url -> nav.open(url, app) { openUrl(context, it) } }
    DisposableEffect(Unit) { onDispose { webView?.apply { stopLoading(); destroy() }; webView = null } }
    Column(modifier) {
        if (fallback) Note("暂时使用基础排版；联网刷新可重试 GitHub 完整格式。")
        else if (offline) Note("当前使用已缓存的 GitHub 排版。")
        val prepared = document
        if (prepared == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else AndroidView(
            modifier = if (fill) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(if (preview) 360.dp else height.coerceIn(48, 100000).dp),
            factory = { ctx -> WebView(ctx).apply {
                webView = this
                setBackgroundColor(colors.background.toArgb())
                settings.apply {
                    textZoom = (resources.configuration.fontScale * 100).toInt()
                    javaScriptEnabled = true // Only app-owned height/anchor evaluations; CSP rejects all document scripts.
                    allowFileAccess = false; allowContentAccess = false; domStorageEnabled = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setSupportMultipleWindows(false); builtInZoomControls = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url
                        val prefix = "/$fullName/$ref/"
                        if (!privateDocument || fullName == null || url.host != "raw.githubusercontent.com" || !url.path.orEmpty().startsWith(prefix)) return null
                        return try {
                            val bytes = synchronized(imageCache) { imageCache[url.toString()] } ?: runBlocking(Dispatchers.IO) { app.container.repository.privateImage(fullName, ref, url.path.orEmpty().removePrefix(prefix)) }.also { value ->
                                synchronized(imageCache) { imageCache[url.toString()] = value; while (imageCache.values.sumOf { it.size } > 8 * 1024 * 1024 && imageCache.size > 1) imageCache.remove(imageCache.keys.first()) }
                            }
                            val extension = url.lastPathSegment?.substringAfterLast('.').orEmpty().lowercase()
                            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/png"
                            WebResourceResponse(mime, null, bytes.inputStream())
                        } catch (_: Exception) { WebResourceResponse("text/plain", "UTF-8", 403, "Unavailable", emptyMap(), "".byteInputStream()) }
                    }
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (initialAnchor.isNotBlank() && !anchorApplied) { anchorApplied = true; val id = org.json.JSONObject.quote(initialAnchor.removePrefix("user-content-")); view.evaluateJavascript("(document.getElementById($id)||document.getElementById('user-content-'+$id))?.scrollIntoView()", null) }
                        else view.scrollTo(0, restoringY)
                        loadingDocument = false
                        measure(view); postDelayed({ measure(view) }, 600) }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (request.url.host == "reader.reporove.invalid" && request.url.path?.startsWith("/copy/") == true) {
                            request.url.lastPathSegment?.toIntOrNull()?.let { index -> currentDocument?.codeBlocks?.getOrNull(index)?.let { code ->
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("代码", code))
                                Toast.makeText(context, "已复制代码", Toast.LENGTH_SHORT).show()
                            } }; return true
                        }
                        if (request.url.host == "reader.reporove.invalid" && !request.url.fragment.isNullOrEmpty()) {
                            val id = request.url.fragment.orEmpty().removePrefix("user-content-")
                            val quoted = org.json.JSONObject.quote(id)
                            evaluateJavascript("(document.getElementById($quoted)||document.getElementById('user-content-'+$quoted)||document.getElementsByName($quoted)[0])?.scrollIntoView()", null)
                            return true
                        }
                        if (request.isForMainFrame && request.url.scheme in listOf("http", "https")) navigate(url)
                        return true
                    }
                }
                setOnScrollChangeListener { _, _, y, _, _ -> if (!loadingDocument) readingY = y }
                setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_UP) postDelayed({ measure(this) }, 150); false }
            } },
            update = { view ->
                view.setBackgroundColor(colors.background.toArgb())
                if (previousDocument != prepared.html) {
                    val body = prepared.html.substringAfter("<body>")
                    val onlyStyle = previousBody == body
                    previousBody = body; previousDocument = prepared.html
                    if (onlyStyle) {
                        val css = prepared.html.substringAfter("<style>").substringBefore("</style>")
                        view.evaluateJavascript("document.querySelector('style').textContent=" + org.json.JSONObject.quote(css)) { measure(view) }
                    } else {
                        restoringY = readingY; loadingDocument = true
                        view.loadDataWithBaseURL("https://reader.reporove.invalid/", prepared.html, "text/html", "UTF-8", null)
                    }

                }
            },
        )
    }
}
