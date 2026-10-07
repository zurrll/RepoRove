package app.reporove.ui

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.reporove.core.model.*
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable inline fun <reified T : ViewModel> screenModel(key: String, noinline create: () -> T): T {
    val factory = remember(key) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    } }
    return viewModel(key = key, factory = factory)
}

fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    require(uri.scheme == "https" || uri.scheme == "http") { "此链接类型暂不支持。" }
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "未找到可以打开此链接的应用。", Toast.LENGTH_SHORT).show() }
}

fun dateLabel(value: String?): String = try { Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) } catch (_: Exception) { "" }
fun numberLabel(value: Int): String = if (value >= 1000) "%.1fk".format(value / 1000.0) else value.toString()
fun bytesLabel(value: Long): String = when { value < 1024 -> "$value B"; value < 1024 * 1024 -> "%.1f KB".format(value / 1024.0); else -> "%.1f MB".format(value / (1024.0 * 1024)) }

@Composable fun RepoRow(repo: Repository, preferences: Preferences, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    val nav = LocalAppNavigation.current
    val app = LocalAppModel.current
    val languageColor = remember(repo.language) { app.container.catalogs.languages.items.firstOrNull { it.name == repo.language }?.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } }
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = if (preferences.compact) 12.dp else 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(repo.owner, Modifier.size(36.dp).clickable { nav.profile(repo.owner.login) }, "查看 ${repo.owner.login} 主页")
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(repo.owner.login, Modifier.clickable { nav.profile(repo.owner.login) }.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = LocalSemanticColors.current.link)
                Text(repo.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onSave) { Icon(if (saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder, if (saved) "移出稍后看" else "稍后看", tint = MaterialTheme.colorScheme.primary) }
        }
        repo.description?.takeIf(String::isNotBlank)?.let { Text(it, Modifier.padding(top = 10.dp), style = if (preferences.compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, maxLines = if (preferences.compact) 2 else 3, overflow = TextOverflow.Ellipsis) }
        if (repo.topics.isNotEmpty()) Text(repo.topics.take(3).joinToString(" · "), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repo.language?.let { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(9.dp).clip(CircleShape).background(languageColor ?: MaterialTheme.colorScheme.onSurfaceVariant)); Text(it, style = MaterialTheme.typography.bodySmall) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.StarBorder, null, Modifier.size(14.dp), tint = LocalSemanticColors.current.warning); Text(" ${numberLabel(repo.stars)}", style = MaterialTheme.typography.bodySmall) }
            Text(dateLabel(repo.pushedAt ?: repo.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (repo.isPrivate) Text("私有", style = MaterialTheme.typography.bodySmall)
            if (repo.archived) Text("已归档", style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable fun EmptyState(title: String, text: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        action?.let { OutlinedButton(onClick = onAction) { Text(it) } }
    }
}

@Composable fun LoadingIndicator() { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }

@Composable fun Note(text: String) { Text(text, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable fun <T> Resource(state: LoadState<T>, retry: () -> Unit, showCacheNote: Boolean = true, content: @Composable (T) -> Unit) {
    when (state) {
        LoadState.Loading -> LoadingIndicator()
        is LoadState.Failed -> EmptyState("暂时无法加载", state.message, "重试", retry)
        is LoadState.Ready -> { if (showCacheNote && state.offline) Note("当前展示缓存内容，联网后可刷新。" ); content(state.value) }
    }
}

fun <T> LazyListScope.listFeedback(state: ListState<T>, refresh: () -> Unit, more: () -> Unit, empty: String = "暂无内容") {
    if (state.offline) item { Note("当前展示缓存内容，联网后可刷新。") }
    if (state.incomplete) item { Note("GitHub 返回了部分搜索结果，可以缩小范围后重试。") }
    state.error?.let { error -> item { EmptyState("加载遇到问题", error, "重试", refresh) } }
    if (state.loading) item { LoadingIndicator() }
    else if (state.items.isEmpty() && state.error == null) item { EmptyState(empty, action = if (state.hasMore) "继续查找" else "刷新", onAction = if (state.hasMore) more else refresh) }
    else if (state.hasMore && state.error == null) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton(onClick = more) { Text("加载更多") } } }
}

@Composable fun <T> ChoiceRow(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values) { value -> FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label(value)) }) }
    }
}

@Composable fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

@Composable fun ActionRow(title: String, detail: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable fun UserAvatar(user: User?, modifier: Modifier = Modifier, description: String? = null) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier), contentAlignment = Alignment.Center) {
        Icon(if (user?.type == "Organization") Icons.Outlined.Business else Icons.Outlined.AccountCircle, null, Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!user?.avatarUrl.isNullOrBlank()) AsyncImage(user?.avatarUrl, null, Modifier.fillMaxSize())
    }
}

@Composable fun PersonRow(user: User, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UserAvatar(user, Modifier.size(36.dp))
        Column(Modifier.weight(1f)) { Text(user.name ?: user.login, style = MaterialTheme.typography.titleSmall, color = LocalSemanticColors.current.link); if (user.type == "Organization") Text("组织", style = MaterialTheme.typography.bodySmall) }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "查看主页", Modifier.size(18.dp))
    }
}

@Composable fun AuthorLink(user: User) {
    val nav = LocalAppNavigation.current
    Row(Modifier.clickable { nav.profile(user.login) }.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UserAvatar(user, Modifier.size(28.dp))
        Text(user.login, style = MaterialTheme.typography.bodyMedium, color = LocalSemanticColors.current.link)
    }
}

@Composable fun StatusBadge(status: String, pull: Boolean = false) {
    val semantic = LocalSemanticColors.current
    val color = when { status == "已合并" -> semantic.merged; status in listOf("开放", "success", "completed") -> semantic.success; status in listOf("已关闭", "failure", "cancelled", "timed_out") -> semantic.danger; status in listOf("草稿", "queued", "in_progress") -> semantic.warning; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = .12f)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(if (pull) Icons.AutoMirrored.Outlined.MergeType else if (status == "success") Icons.Outlined.CheckCircle else Icons.Outlined.Info, null, Modifier.size(16.dp), tint = color)
        Text(status, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable fun LabelBadge(label: Label) {
    val background = remember(label.color) { runCatching { Color(android.graphics.Color.parseColor("#${label.color}")) }.getOrDefault(Color(0xFF57606A)) }
    val foreground = if (background.luminance() > .179) Color.Black else Color.White
    Text(label.name, Modifier.clip(RoundedCornerShape(5.dp)).background(background).padding(horizontal = 7.dp, vertical = 3.dp), color = foreground, style = MaterialTheme.typography.labelSmall)
}

@Composable fun IssueRow(issue: Issue, pull: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(issue.title, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatusBadge(issue.status, pull); Text("#${issue.number} · ${issue.comments} 评论", style = MaterialTheme.typography.bodySmall) }
        if (issue.labels.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { issue.labels.forEach { LabelBadge(it) } }
        AuthorLink(issue.user)
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
