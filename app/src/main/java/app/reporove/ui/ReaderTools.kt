package app.reporove.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.ui.unit.dp

@Composable fun ReaderBar(title: String, back: (() -> Unit)? = null, context: (() -> Unit)? = null, contextLabel: String = "文件位置与版本", actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        back?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }
        Row(Modifier.weight(1f).then(if (context != null) Modifier.semantics { contentDescription = contextLabel }.clickable(onClickLabel = contextLabel, onClick = context) else Modifier).padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (context != null) Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

class BilingualToggle(val enabled: Boolean, val toggle: () -> Unit)
@Composable fun rememberBilingualToggle(identity: String): BilingualToggle {
    val app = LocalAppModel.current
    val nav = LocalAppNavigation.current
    val config by app.container.translations.config.collectAsState()
    var enabled by rememberSaveable(identity) { mutableStateOf(false) }
    LaunchedEffect(config) { if (config == null) enabled = false }
    return BilingualToggle(enabled) {
        if (config == null) nav.settingsSection("translation") else enabled = !enabled
    }
}
@Composable fun BilingualButton(control: BilingualToggle) {
    IconToggleButton(checked = control.enabled, onCheckedChange = { control.toggle() }) { Icon(Icons.Outlined.Translate, if (control.enabled) "关闭双语" else "开启双语", tint = if (control.enabled) LocalSemanticColors.current.link else MaterialTheme.colorScheme.onSurfaceVariant) }
}
