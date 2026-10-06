package app.reporove.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import app.reporove.core.model.ThemeId
import androidx.compose.ui.unit.sp
import app.reporove.core.model.Preferences
import app.reporove.core.model.ThemeMode

private val Paper = lightColorScheme(
    primary = Color(0xFF596746), onPrimary = Color(0xFFFAF8F2),
    primaryContainer = Color(0xFFE8EBDD), onPrimaryContainer = Color(0xFF30372D),
    secondary = Color(0xFF67715C), onSecondary = Color(0xFFFAF8F2),
    secondaryContainer = Color(0xFFE8EBDD), onSecondaryContainer = Color(0xFF30372D),
    tertiary = Color(0xFF71634D), tertiaryContainer = Color(0xFFEEE7D8), onTertiaryContainer = Color(0xFF393226),
    surfaceContainer = Color(0xFFF0F0E6), surfaceContainerHigh = Color(0xFFEDEDE2), surfaceContainerHighest = Color(0xFFE8EBDD),
    background = Color(0xFFFAF8F2), onBackground = Color(0xFF30372D),
    surface = Color(0xFFFAF8F2), onSurface = Color(0xFF30372D),
    surfaceVariant = Color(0xFFF0F0E6), onSurfaceVariant = Color(0xFF67715C),
    outline = Color(0xFF949C89), outlineVariant = Color(0xFFE3E2D8),
)
private val DarkPaper = darkColorScheme(
    primary = Color(0xFFC3CEAE), onPrimary = Color(0xFF273020),
    primaryContainer = Color(0xFF394431), onPrimaryContainer = Color(0xFFE1E8D4),
    secondary = Color(0xFFB4BDAB), onSecondary = Color(0xFF273020),
    secondaryContainer = Color(0xFF394431), onSecondaryContainer = Color(0xFFE1E8D4),
    tertiary = Color(0xFFD0C4AB), tertiaryContainer = Color(0xFF463F30), onTertiaryContainer = Color(0xFFEEE7D8),
    surfaceContainer = Color(0xFF262C22), surfaceContainerHigh = Color(0xFF2D3428), surfaceContainerHighest = Color(0xFF394431),
    background = Color(0xFF191D17), onBackground = Color(0xFFE3E6DA),
    surface = Color(0xFF191D17), onSurface = Color(0xFFE3E6DA),
    surfaceVariant = Color(0xFF262C22), onSurfaceVariant = Color(0xFFB4BDAB),
    outline = Color(0xFF839178), outlineVariant = Color(0xFF3B4334),
)

private val Clear = lightColorScheme(
    primary = Color(0xFF0969DA), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4FF), onPrimaryContainer = Color(0xFF0550AE),
    secondary = Color(0xFF57606A), secondaryContainer = Color(0xFFEAEEF2), onSecondaryContainer = Color(0xFF24292F),
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF1F2328),
    surface = Color.White, onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFF6F8FA), onSurfaceVariant = Color(0xFF59636E),
    surfaceContainer = Color(0xFFF6F8FA), surfaceContainerHigh = Color(0xFFEAEEF2), surfaceContainerHighest = Color(0xFFD8DEE4),
    outline = Color(0xFF8C959F), outlineVariant = Color(0xFFD1D9E0), error = Color(0xFFCF222E),
)
private val DarkClear = darkColorScheme(
    primary = Color(0xFF79C0FF), onPrimary = Color(0xFF071C32),
    primaryContainer = Color(0xFF142E4B), onPrimaryContainer = Color(0xFFB6E3FF),
    secondary = Color(0xFFB1BAC4), secondaryContainer = Color(0xFF21262D), onSecondaryContainer = Color(0xFFF0F6FC),
    background = Color(0xFF0D1117), onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF0D1117), onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF161B22), onSurfaceVariant = Color(0xFFB1BAC4),
    surfaceContainer = Color(0xFF161B22), surfaceContainerHigh = Color(0xFF21262D), surfaceContainerHighest = Color(0xFF30363D),
    outline = Color(0xFF8B949E), outlineVariant = Color(0xFF30363D), error = Color(0xFFFF7B72),
)

data class SemanticColors(val link: Color, val success: Color, val danger: Color, val merged: Color, val warning: Color, val code: Color, val dark: Boolean)
val LocalSemanticColors = staticCompositionLocalOf { SemanticColors(Color(0xFF0969DA), Color(0xFF1A7F37), Color(0xFFCF222E), Color(0xFF8250DF), Color(0xFF9A6700), Color(0xFFF6F8FA), false) }

@Composable fun RepoRoveTheme(preferences: Preferences, content: @Composable () -> Unit) {
    val dark = preferences.theme == ThemeMode.Dark || (preferences.theme == ThemeMode.System && isSystemInDarkTheme())
    val scheme = if (preferences.themeId == ThemeId.Paper) { if (dark) DarkPaper else Paper } else if (dark) DarkClear else Clear
    val semantic = SemanticColors(
        if (dark) Color(0xFF79C0FF) else Color(0xFF0969DA),
        if (dark) Color(0xFF7EE787) else Color(0xFF1A7F37),
        if (dark) Color(0xFFFF7B72) else Color(0xFFCF222E),
        if (dark) Color(0xFFD2A8FF) else Color(0xFF8250DF),
        if (dark) Color(0xFFE3B341) else Color(0xFF9A6700), scheme.surfaceContainer, dark,
    )
    val base = Typography()
    fun TextStyle.scaled() = copy(fontSize = fontSize * preferences.textScale, lineHeight = lineHeight * preferences.textScale, fontFamily = FontFamily.SansSerif)
    val typography = Typography(
        displayLarge = base.displayLarge.scaled(), displayMedium = base.displayMedium.scaled(), displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(), headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = (22 * preferences.textScale).sp, lineHeight = (28 * preferences.textScale).sp),
        titleLarge = base.titleLarge.scaled().copy(fontWeight = FontWeight.SemiBold), titleMedium = base.titleMedium.scaled().copy(fontWeight = FontWeight.SemiBold), titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled().copy(lineHeight = (26 * preferences.textScale).sp), bodyMedium = base.bodyMedium.scaled().copy(lineHeight = (22 * preferences.textScale).sp), bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(), labelMedium = base.labelMedium.scaled(), labelSmall = base.labelSmall.scaled(),
    )
    CompositionLocalProvider(LocalSemanticColors provides semantic) { MaterialTheme(colorScheme = scheme, typography = typography, content = content) }
}
