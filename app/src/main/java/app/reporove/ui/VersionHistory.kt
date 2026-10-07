package app.reporove.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.reporove.core.network.AppJson
import app.reporove.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable data class VersionNote(val version: String, val date: String, val changes: List<String>)
@Composable fun VersionHistoryScreen() {
    val context = LocalContext.current
    val notes by produceState<List<VersionNote>?>(null) { value = withContext(Dispatchers.IO) { context.assets.open("changelog.json").bufferedReader().use { AppJson.decodeFromString<List<VersionNote>>(it.readText()) } } }
    val records = notes ?: run { LoadingIndicator(); return }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Note("当前版本 ${BuildConfig.VERSION_NAME} · 本机更新记录") }
        items(records, key = { it.version }) { note ->
            var expanded by rememberSaveable(note.version) { mutableStateOf(note.version == BuildConfig.VERSION_NAME) }
            ActionRow("${note.version}${if (note.version == BuildConfig.VERSION_NAME) " · 当前" else ""}", note.date) { expanded = !expanded }
            if (expanded) Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                note.changes.forEach { change -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("•", color = LocalSemanticColors.current.link); Text(change, style = MaterialTheme.typography.bodyMedium) } }
            }
            HorizontalDivider()
        }
    }
}
