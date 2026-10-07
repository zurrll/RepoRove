package app.reporove.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** The header consumes upward scroll first; it returns only after the child reaches its start. */
@Composable internal fun RepositoryLayout(
    fullName: String,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var collapsedFraction by rememberSaveable(fullName) { mutableFloatStateOf(0f) }
    var headerHeight by remember { mutableFloatStateOf(0f) }
    fun moveHeader(delta: Float): Float {
        if (headerHeight <= 0f) return 0f
        val oldOffset = -collapsedFraction * headerHeight
        val newOffset = (oldOffset + delta).coerceIn(-headerHeight, 0f)
        collapsedFraction = -newOffset / headerHeight
        return newOffset - oldOffset
    }
    val scroll = rememberScrollableState { moveHeader(it) }
    val connection = remember(fullName) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0f) Offset(0f, moveHeader(available.y)) else Offset.Zero
        }
    }
    Column(Modifier.fillMaxSize().nestedScroll(connection).scrollable(scroll, Orientation.Vertical).testTag("repository-layout")) {
        Layout(content = header, modifier = Modifier.fillMaxWidth().clipToBounds().testTag("repository-header")) { children, constraints ->
            val placeable = children.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            headerHeight = placeable.height.toFloat()
            val offset = (-collapsedFraction * placeable.height).roundToInt()
            layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) {
                placeable.placeRelative(0, offset)
            }
        }
        content()
    }
}
