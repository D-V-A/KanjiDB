package com.example.kanjidb.ui.lists

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.IntSize
import com.example.kanjidb.data.user.moveKanji

internal data class KanjiReorderDrop(
    val section: String, val character: String, val before: List<String>, val after: List<String>
)

/** Ephemeral preview only. The same LazyGrid and character keys render the persisted and dragged order. */
internal class KanjiGridReorder(private val grid: LazyGridState) {
    var character by mutableStateOf<String?>(null)
        private set
    var order by mutableStateOf<List<String>?>(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set
    var size by mutableStateOf(IntSize.Zero)
        private set
    var saving by mutableStateOf(false)
        private set
    var awaitingCommit by mutableStateOf(false)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var section: String? = null
        private set
    private var before = emptyList<String>()
    private var grab = Offset.Zero
    private var lastMoveLayout: LazyGridLayoutInfo? = null

    fun start(at: Offset, section: KanjiSection): Boolean {
        if (saving || awaitingCommit) return false
        val keys = section.cards.map { it.character }
        val item = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key in keys && Rect(it.offset.x.toFloat(), it.offset.y.toFloat(),
                (it.offset.x + it.size.width).toFloat(), (it.offset.y + it.size.height).toFloat()).contains(at)
        } ?: return false
        this.section = section.key
        character = item.key as String
        before = keys
        order = keys
        pointer = at
        position = Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
        grab = at - position
        size = item.size
        return true
    }

    fun move(at: Offset) {
        if (character == null) return
        pointer = at
        position = at - grab
    }

    fun updateTarget(top: Float, bottom: Float) {
        val dragged = character ?: return
        val current = order ?: return
        val layout = grid.layoutInfo
        // Do not reorder twice against stale item coordinates before the next layout.
        if (layout === lastMoveLayout) return
        val rawCenter = position + Offset(size.width / 2f, size.height / 2f)
        val halfHeight = size.height / 2f
        val center = Offset(rawCenter.x, rawCenter.y.coerceIn(top + halfHeight,
            maxOf(top + halfHeight, bottom - halfHeight)))
        val target = layout.visibleItemsInfo.firstOrNull {
            it.key != dragged && it.key in current &&
                Rect(it.offset.x.toFloat(), it.offset.y.toFloat(), (it.offset.x + it.size.width).toFloat(),
                    (it.offset.y + it.size.height).toFloat()).contains(center)
        } ?: return
        val next = moveKanji(current, dragged, target.key as String)
        if (next != current) {
            order = next
            lastMoveLayout = layout
        }
    }

    fun drop(): KanjiReorderDrop? {
        val dragged = character ?: return null
        val drop = KanjiReorderDrop(requireNotNull(section), dragged, before, requireNotNull(order))
        character = null
        saving = true
        return drop
    }

    fun complete(success: Boolean) {
        saving = false
        if (success) awaitingCommit = true else cancel()
    }

    fun sync(source: List<String>) {
        if (saving) return
        if (awaitingCommit && source == order) cancel()
        else if (order != null && source != before) cancel()
    }

    fun cancel() {
        character = null
        if (saving) return // An already submitted Room transaction owns its completion.
        order = null
        section = null
        awaitingCommit = false
        lastMoveLayout = null
    }
}

/** Captures eligibility on DOWN so the long press that enters selection can never start a drag. */
internal suspend fun PointerInputScope.detectKanjiReorder(
    eligible: (Offset) -> Boolean,
    start: (Offset) -> Boolean,
    move: (Offset) -> Unit,
    drop: () -> Unit,
    cancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!eligible(down.position)) return@awaitEachGesture
        var latest = down
        val interrupted = withTimeoutOrNull(250L) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || change.isConsumed ||
                    event.changes.count { it.pressed } > 1 ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                latest = change
            }
        }
        if (interrupted != null || !start(latest.position)) return@awaitEachGesture
        latest.consume()
        var released = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                change.consume()
                if (!change.pressed) {
                    released = true
                    drop()
                    break
                }
                move(change.position)
            }
        } finally {
            if (!released) cancel()
        }
    }
}
