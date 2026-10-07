package app.reporove.ui

import android.content.ClipboardManager
import android.content.Context
import android.view.ViewTreeObserver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.reporove.core.model.*
import app.reporove.core.offline.OfflineStore
import java.net.URI

fun clipboardGitHubLink(text: String): String? {
    val candidates = Regex("https://(?:www\\.)?(?:github\\.com|api\\.github\\.com)/[^\\s<>\"']+", RegexOption.IGNORE_CASE).findAll(text.take(16_000))
    return candidates.map { it.value.trimEnd('。', '，', ',', ')') }.firstOrNull { url ->
        val uri = runCatching { URI(url) }.getOrNull()
        uri?.userInfo == null && uri?.port in listOf(-1, 443) && GitHubLinks.parse(url) != null
    }
}

@Composable fun ClipboardLinkPrompt(prefs: Preferences, nav: AppNavigation, app: AppModel) {
    val context = LocalContext.current; val view = LocalView.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
    val memory = remember { context.getSharedPreferences("clipboard-prompts", Context.MODE_PRIVATE) }
    var pending by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    val enabled by rememberUpdatedState(prefs.clipboardLinks)
    fun check() {
        if (checked || !enabled || !view.hasWindowFocus()) return
        checked = true
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        val link = clipboardGitHubLink(text) ?: return
        if (memory.getString("handled-digest", null) != OfflineStore.digest(link)) pending = link
    }
    DisposableEffect(view, lifecycle) {
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { if (it) check() }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) checked = false; if (event == Lifecycle.Event.ON_RESUME) view.post { check() } }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus); lifecycle.addObserver(observer); view.post { check() }
        onDispose { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus); lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(enabled) { if (enabled) check() else pending = null }
    fun handled(link: String) { memory.edit().putString("handled-digest", OfflineStore.digest(link)).apply(); pending = null }
    pending?.let { link -> AlertDialog(onDismissRequest = { handled(link) }, title = { Text("打开剪贴板中的 GitHub 链接？") }, text = { Text(link) }, confirmButton = { TextButton(onClick = { handled(link); GitHubLinks.parse(link)?.let { nav.target(it, app) } }) { Text("打开") } }, dismissButton = { TextButton(onClick = { handled(link) }) { Text("暂不") } }) }
}
