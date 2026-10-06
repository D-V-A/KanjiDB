package com.example.kanjidb.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
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

/** Page-owned controls below fixed tabs; saved across navigation, reset on each tab change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollapsingCollectionHeader(
    currentPage: Int,
    scrollEnabled: Boolean,
    modifier: Modifier = Modifier,
    controlsVisible: Boolean = true,
    filtersExpanded: Boolean = true,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    // Key only the header state: leaving a tab discards its collapse state, while
    // Details -> Back restores it for the same tab. Pager/grid composition is unchanged.
    val state = key(currentPage, controlsVisible, filtersExpanded) { rememberTopAppBarState() }
    val enabled by rememberUpdatedState(scrollEnabled)
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior(state, canScroll = { enabled })
    val connection = remember(behavior) {
        UserScrollHeaderConnection(behavior.nestedScrollConnection) { enabled }
    }
    Layout(
        modifier = modifier.clipToBounds().nestedScroll(connection),
        content = { Box { if (controlsVisible) header() }; Box { content() } }
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
        val offset = state.heightOffset.roundToInt()
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

/** Saved normal-mode preference; selection/reorder only suppress its presentation. */
internal class CollectionFiltersState(initialExpanded: Boolean = false) {
    var expanded by mutableStateOf(initialExpanded)
        private set
    fun toggle(locked: Boolean) { if (!locked) expanded = !expanded }
    fun visibleExpanded(locked: Boolean): Boolean = expanded && !locked
    companion object {
        val Saver = listSaver<CollectionFiltersState, Boolean>(
            save = { listOf(it.expanded) }, restore = { CollectionFiltersState().apply { expanded = it[0] } })
    }
}

@Composable
internal fun CollectionFiltersHeader(
    currentPage: Int, activePage: Boolean, locked: Boolean, enabled: Boolean,
    modifier: Modifier = Modifier, initiallyExpanded: Boolean = false, controls: @Composable () -> Unit, content: @Composable () -> Unit
) {
    val filters = rememberSaveable(saver = CollectionFiltersState.Saver) { CollectionFiltersState(initiallyExpanded) }
    val expanded = filters.visibleExpanded(locked)
    CollapsingCollectionHeader(currentPage = currentPage,
        scrollEnabled = activePage && enabled && !locked, controlsVisible = !locked,
        filtersExpanded = expanded,
        modifier = modifier, header = {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (expanded) controls()
                TextButton(onClick = { filters.toggle(locked) }, enabled = enabled && !locked,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (expanded) "Hide filters" else "Show filters")
                }
            }
        }, content = content)
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
