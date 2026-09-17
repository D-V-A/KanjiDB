package com.example.kanjidb.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/** Shared enter-always header; saved across navigation, reset on each tab change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollapsingCollectionHeader(
    currentPage: Int,
    scrollEnabled: Boolean,
    keepVisible: Boolean,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    // Key only the header state: leaving a tab discards its collapse state, while
    // Details -> Back restores it for the same tab. Pager/grid composition is unchanged.
    val state = key(currentPage) { rememberTopAppBarState() }
    val enabled by rememberUpdatedState(scrollEnabled)
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior(state, canScroll = { enabled })
    val connection = remember(behavior) {
        UserScrollHeaderConnection(behavior.nestedScrollConnection) { enabled }
    }
    Layout(
        modifier = modifier.clipToBounds().nestedScroll(connection),
        content = { Box { header() }; Box { content() } }
    ) { measurables, constraints ->
        // Measure the real header before the pager, including on the first restored layout.
        val headerPlaceable = measurables[0].measure(constraints.copy(minHeight = 0))
        val limit = -headerPlaceable.height.toFloat()
        if (state.heightOffsetLimit != limit) {
            // Keep the same collapse fraction when changing font/width within the current tab.
            val fraction = state.collapsedFraction
            state.heightOffsetLimit = limit
            state.heightOffset = limit * fraction
        }
        // The placeholder has no vertical scrolling with which to bring its tabs back.
        val offset = if (keepVisible) 0 else state.heightOffset.roundToInt()
        val visibleHeight = (headerPlaceable.height + offset).coerceAtLeast(0)
        val contentPlaceable = measurables[1].measure(
            Constraints.fixed(constraints.maxWidth, (constraints.maxHeight - visibleHeight).coerceAtLeast(0))
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            headerPlaceable.placeRelative(0, offset)
            contentPlaceable.placeRelative(0, visibleHeight)
        }
    }
}

/** Programmatic card clearance must not move the header; horizontal pager flings must not snap it. */
internal class UserScrollHeaderConnection(
    private val delegate: NestedScrollConnection,
    private val enabled: () -> Boolean
) : NestedScrollConnection {
    private var verticalGesture = false
    private var verticalFling = false

    private fun accepts(source: NestedScrollSource) =
        enabled() && (source == NestedScrollSource.UserInput || verticalFling)

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!accepts(source) || available.y == 0f) return Offset.Zero
        if (source == NestedScrollSource.UserInput) verticalGesture = true
        return delegate.onPreScroll(Offset(0f, available.y), source)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!accepts(source) ||
            (consumed.y == 0f && available.y == 0f)) return Offset.Zero
        return delegate.onPostScroll(Offset(0f, consumed.y), Offset(0f, available.y), source)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Fling frames also use SideEffect; admit them only after a real vertical gesture.
        verticalFling = verticalGesture && enabled() && available.y != 0f
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val settle = verticalGesture && enabled()
        verticalGesture = false
        verticalFling = false
        return if (settle) delegate.onPostFling(Velocity(0f, consumed.y), Velocity(0f, available.y))
        else Velocity.Zero
    }
}
