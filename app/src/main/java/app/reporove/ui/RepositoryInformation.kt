package app.reporove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reporove.core.model.*
import java.util.Locale

@Composable fun repositoryLanguageColor(language: String?): Color {
    val catalogs = LocalAppModel.current.container.catalogs
    val fallback = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(language, fallback) { catalogs.languages.items.firstOrNull { it.name == language }?.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: fallback }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RepositoryMetadata(app: AppModel, repo: Repository) {
    var expanded by remember { mutableStateOf(false) }
    val model = screenModel("languages:${repo.fullName}") { ResourceModel<Map<String, Long>>() }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(repo.fullName) { model.configure(repo.fullName) { app.container.repository.languages(repo.fullName, it) } }
    val entries = (state as? LoadState.Ready)?.value.orEmpty().filterValues { it > 0 }.toList().sortedByDescending { it.second }
    val total = entries.sumOf { it.second.toDouble() }
    Column(Modifier.padding(top = 6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.clickable { expanded = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(repositoryLanguageColor(repo.language)))
                Text(repo.language ?: "语言构成", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Outlined.ArrowDropDown, "查看语言占比", Modifier.size(18.dp))
            }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.StarBorder, null, Modifier.size(18.dp), tint = LocalSemanticColors.current.warning); Text(numberLabel(repo.stars), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.AutoMirrored.Outlined.CallSplit, null, Modifier.size(18.dp), tint = LocalSemanticColors.current.link); Text(numberLabel(repo.forks), style = MaterialTheme.typography.bodyMedium) }
            repo.license?.spdxId?.let { Text(it, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(if (repo.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(if (repo.isPrivate) "私有" else "公开", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (total > 0) Row(Modifier.fillMaxWidth().height(16.dp).clickable { expanded = true }, verticalAlignment = Alignment.CenterVertically) { entries.forEach { (language, bytes) -> Box(Modifier.weight((bytes / total).toFloat().coerceAtLeast(.0001f)).height(5.dp).background(repositoryLanguageColor(language))) } }
    }
    if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false }) {
        SectionTitle("语言构成", "刷新", model::refresh)
        Resource(state, model::refresh) { _ ->
            if (entries.isEmpty()) EmptyState("没有语言统计")
            else LazyColumn(Modifier.heightIn(max = 440.dp)) { items(entries, key = { it.first }) { (language, bytes) -> ListItem(leadingContent = { Box(Modifier.size(12.dp).clip(CircleShape).background(repositoryLanguageColor(language))) }, headlineContent = { Text(language) }, trailingContent = { Text(String.format(Locale.getDefault(), "%.1f%%", bytes / total * 100)) }) } }
        }
        Note("按 GitHub 统计的代码字节计算。")
    }
}
