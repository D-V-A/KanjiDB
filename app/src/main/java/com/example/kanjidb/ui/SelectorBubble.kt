package com.example.kanjidb.ui

import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** One feedback instance; a stale timeout must never hide a newer interaction. */
internal data class SelectorFeedback(
    val optionIndex: Int = 0, val dragging: Boolean = false, val revision: Long = 0,
    val visible: Boolean = false
) {
    fun tap(index: Int) = SelectorFeedback(index, false, revision + 1, true)
    fun startDrag(index: Int) = SelectorFeedback(index, true, revision + 1, true)
    fun preview(index: Int) = copy(optionIndex = index)
    fun release(index: Int) = SelectorFeedback(index, false, revision + 1, true)
    fun hide(timeoutRevision: Long) =
        if (!dragging && revision == timeoutRevision) copy(visible = false) else this
}

internal data class SelectorBubblePlacement(val offset: IntOffset, val pointerX: Int)

internal fun selectorBubblePlacement(anchor: IntRect, window: IntSize, popup: IntSize,
    targetX: Int, margin: Int): SelectorBubblePlacement {
    fun bounded(value: Int, available: Int): Int {
        val max = available.coerceAtLeast(0)
        val inset = margin.coerceIn(0, max / 2)
        return value.coerceIn(inset, max - inset)
    }
    val target = anchor.left + targetX
    val x = bounded(target - popup.width / 2, window.width - popup.width)
    val y = bounded(anchor.top - popup.height, window.height - popup.height)
    return SelectorBubblePlacement(IntOffset(x, y), (target - x).coerceIn(0, popup.width))
}

/** Visual-only overlay, excluded from input and from the accessibility tree. */
@Composable
internal fun SelectorBubble(visible: Boolean, label: String, targetX: Float) {
    val opacity by animateFloatAsState(if (visible) 1f else 0f, tween(100), label = "Selector bubble fade")
    if (!visible && opacity == 0f) return
    val density = LocalDensity.current
    var pointerX by remember { mutableIntStateOf(0) }
    val targetPx = with(density) { targetX.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }
    val position = remember(targetPx, margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val placement = selectorBubblePlacement(anchorBounds, windowSize, popupContentSize, targetPx, margin)
                pointerX = placement.pointerX
                return placement.offset
            }
        }
    }
    val container = MaterialTheme.colorScheme.inverseSurface
    Popup(popupPositionProvider = position, properties = PopupProperties(
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        dismissOnBackPress = false, dismissOnClickOutside = false, excludeFromSystemGesture = false
    )) {
        Column(Modifier.widthIn(max = 240.dp).width(IntrinsicSize.Max)
            .graphicsLayer { alpha = opacity }.clearAndSetSemantics {}) {
            Surface(shape = MaterialTheme.shapes.small, color = container,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val tip = pointerX.toFloat().coerceIn(0f, size.width)
                val halfBase = 5.dp.toPx()
                drawPath(Path().apply {
                    moveTo((tip - halfBase).coerceAtLeast(0f), 0f)
                    lineTo(tip, size.height)
                    lineTo((tip + halfBase).coerceAtMost(size.width), 0f)
                    close()
                }, container)
            }
        }
    }
}
