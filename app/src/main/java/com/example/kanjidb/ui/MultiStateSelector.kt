package com.example.kanjidb.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay

internal data class SelectorOption(val icon: ImageVector, val label: String)

/** Dimensions in dp; the thumb stays circular and independent of option count. */
internal fun selectorWidth(itemCount: Int): Float {
    require(itemCount >= 2)
    return 44f * itemCount
}

internal fun selectorSnapPositions(itemCount: Int): List<Float> {
    val width = selectorWidth(itemCount)
    return List(itemCount) { 22f + it * (width - 44f) / (itemCount - 1) }
}

internal fun nearestSelectorIndex(position: Float, positions: List<Float>): Int {
    require(positions.size >= 2)
    return positions.indices.minBy { abs(positions[it] - position) }
}

/**
 * Shares the pill/round-thumb visual language of Details' TwoIconSelector, but
 * highlights the selected icon instead of covering an inactive representation.
 * Only the caller owns the value and persistence; drag movement is local preview.
 */
@Composable
internal fun MultiStateSelector(
    options: List<SelectorOption>, selectedIndex: Int, onSelect: (Int) -> Unit,
    contentDescription: String, modifier: Modifier = Modifier
) {
    require(selectedIndex in options.indices)
    val positions = remember(options.size) { selectorSnapPositions(options.size) }
    val width = selectorWidth(options.size).dp
    val density = LocalDensity.current
    val motion = remember(options.size) { Animatable(positions[selectedIndex]) }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(motion.value) }
    var feedback by remember(options.size) { mutableStateOf(SelectorFeedback()) }
    val currentSelection by rememberUpdatedState(selectedIndex)
    val selectionChanged by rememberUpdatedState(onSelect)
    LaunchedEffect(selectedIndex, dragging, options.size) {
        if (!dragging) motion.animateTo(positions[selectedIndex], tween(180))
    }
    LaunchedEffect(feedback.revision, feedback.dragging) {
        if (feedback.visible && !feedback.dragging) {
            val revision = feedback.revision
            delay(1_000)
            feedback = feedback.hide(revision)
        }
    }
    val position = if (dragging) dragPosition else motion.value
    val previewIndex = if (dragging) nearestSelectorIndex(position, positions) else selectedIndex
    val settled = !dragging && !motion.isRunning && abs(position - positions[selectedIndex]) < 0.01f
    val lockProgress by animateFloatAsState(if (settled) 1f else 0f, tween(120), label = "Selector lock")
    // Immediately fit inside the track on movement, then finish the short shrink.
    // Grow beyond the track only after the snap completes.
    val diameter = (38f + (if (settled) 5f else 2f) * lockProgress).dp
    val dragState = rememberDraggableState { delta ->
        dragPosition = (dragPosition + with(density) { delta.toDp().value })
            .coerceIn(positions.first(), positions.last())
        feedback = feedback.preview(nearestSelectorIndex(dragPosition, positions))
    }
    Box(modifier.widthIn(max = width)) {
        // Scrolling is a last resort for exceptionally narrow parents or many options;
        // never squeeze icons together to satisfy a width constraint.
        Box(Modifier.widthIn(max = width).horizontalScroll(rememberScrollState())) {
            Box(Modifier.requiredWidth(width).height(52.dp)
                .draggable(dragState, Orientation.Horizontal,
                    onDragStarted = { start ->
                        motion.stop()
                        dragPosition = with(density) { start.x.toDp().value }
                            .coerceIn(positions.first(), positions.last())
                        dragging = true
                        feedback = feedback.startDrag(nearestSelectorIndex(dragPosition, positions))
                    },
                    onDragStopped = {
                        val target = nearestSelectorIndex(dragPosition, positions)
                        motion.snapTo(dragPosition)
                        dragging = false
                        feedback = feedback.release(target)
                        if (target != currentSelection) selectionChanged(target)
                    })
                .selectableGroup()
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = options[previewIndex].label
                }, contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape))
                Box(Modifier.offset { IntOffset(with(density) { position.dp.roundToPx() }, 0) }
                    .requiredSize(diameter)
                    .graphicsLayer { translationX = -size.width / 2 }
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                options.forEachIndexed { index, option ->
                    val active = index == previewIndex
                    val scale by animateFloatAsState(if (active) 1.1f else 1f, tween(120), label = "Selector icon scale")
                    val tint by animateColorAsState(if (active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), tween(120), label = "Selector icon tint")
                    Icon(option.icon, null, Modifier.offset {
                        IntOffset(with(density) { positions[index].dp.roundToPx() }, 0)
                    }.requiredSize(26.dp).graphicsLayer {
                        translationX = -size.width / 2
                        scaleX = scale
                        scaleY = scale
                    }, tint = tint)
                }
                // Midpoints between evenly spaced centers divide the control
                // into direct-selection zones, including the full edge slots.
                Row(Modifier.fillMaxSize()) {
                    options.forEachIndexed { index, option ->
                        Box(Modifier.weight(1f).fillMaxHeight()
                            .selectable(selected = index == previewIndex, role = Role.RadioButton,
                                interactionSource = null, indication = null,
                                onClick = {
                                    feedback = feedback.tap(index)
                                    if (index != currentSelection) selectionChanged(index)
                                })
                            .semantics { this.contentDescription = option.label })
                    }
                }
                SelectorBubble(feedback.visible, options[feedback.optionIndex].label, positions[feedback.optionIndex])
            }
        }
    }
}
