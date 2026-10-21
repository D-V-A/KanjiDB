package com.example.kanjidb.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.example.kanjidb.ui.settings.startupSelectorOptions
import com.example.kanjidb.ui.settings.themeSelectorOptions
import org.junit.Assert.*
import org.junit.Test

class SelectorBubbleTest {
    @Test fun tapShowsTheDirectlySelectedOptionLabel() {
        assertFalse(SelectorFeedback().visible)
        for (options in listOf(themeSelectorOptions, startupSelectorOptions)) {
            for (index in options.indices) {
                val feedback = SelectorFeedback().tap(index)
                assertTrue(feedback.visible)
                assertFalse(feedback.dragging)
                assertEquals(options[index].label, options[feedback.optionIndex].label)
            }
        }
    }

    @Test fun repeatedTapRestartsTimeoutEvenForTheSameOption() {
        val first = SelectorFeedback().tap(1)
        val repeated = first.tap(1)
        assertTrue(repeated.revision > first.revision)
        assertTrue(repeated.hide(first.revision).visible)
        assertFalse(repeated.hide(repeated.revision).visible)
    }

    @Test fun dragCancelsTapTimeoutAndCannotExpireWhileHeld() {
        val tap = SelectorFeedback().tap(0)
        val drag = tap.startDrag(1)
        assertTrue(drag.dragging)
        assertTrue(drag.hide(tap.revision).visible)
        assertTrue(drag.hide(drag.revision).visible)
    }

    @Test fun dragPreviewUsesClosestOptionAndKeepsOneTimerRevision() {
        var feedback = SelectorFeedback().startDrag(0)
        val revision = feedback.revision
        val positions = selectorSnapPositions(4)
        for (index in positions.indices) {
            feedback = feedback.preview(nearestSelectorIndex(positions[index], positions))
            assertEquals(startupSelectorOptions[index].label, startupSelectorOptions[feedback.optionIndex].label)
            assertEquals(revision, feedback.revision)
            assertTrue(feedback.visible)
            assertTrue(feedback.dragging)
        }
    }

    @Test fun releaseShowsFinalOptionAndStartsANewTimeout() {
        val drag = SelectorFeedback().startDrag(0).preview(2)
        val release = drag.release(2)
        assertFalse(release.dragging)
        assertEquals(2, release.optionIndex)
        assertTrue(release.visible)
        assertTrue(release.hide(drag.revision).visible)
        val hidden = release.hide(release.revision)
        assertFalse(hidden.visible)
        assertEquals(2, hidden.optionIndex) // Retain label/anchor for the fade-out.
    }

    @Test fun bubbleIsCenteredAboveTheTargetWhenThereIsRoom() {
        val anchor = IntRect(100, 200, 276, 252)
        val placement = selectorBubblePlacement(anchor, IntSize(400, 800), IntSize(100, 40), 66, 8)
        assertEquals(IntOffset(116, 160), placement.offset)
        assertEquals(50, placement.pointerX)
    }

    @Test fun leftEdgeClampsTheContainerButRetainsThePointerTarget() {
        val placement = selectorBubblePlacement(IntRect(0, 200, 176, 252),
            IntSize(400, 800), IntSize(120, 40), 22, 8)
        assertEquals(8, placement.offset.x)
        assertEquals(22, placement.offset.x + placement.pointerX)
    }

    @Test fun rightEdgeClampsTheContainerButRetainsThePointerTarget() {
        val placement = selectorBubblePlacement(IntRect(224, 200, 400, 252),
            IntSize(400, 800), IntSize(120, 40), 154, 8)
        assertEquals(272, placement.offset.x)
        assertEquals(378, placement.offset.x + placement.pointerX)
    }

    @Test fun verticalAndTinyWindowBoundsDoNotProduceInvalidPositions() {
        val top = selectorBubblePlacement(IntRect(0, 10, 176, 62),
            IntSize(200, 100), IntSize(100, 40), 22, 8)
        assertEquals(8, top.offset.y)
        val tiny = selectorBubblePlacement(IntRect(0, 0, 176, 52),
            IntSize(100, 30), IntSize(100, 30), 22, 8)
        assertEquals(IntOffset.Zero, tiny.offset)
        assertEquals(22, tiny.pointerX)
    }
}
