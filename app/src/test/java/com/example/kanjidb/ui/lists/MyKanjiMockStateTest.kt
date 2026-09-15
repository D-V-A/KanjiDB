package com.example.kanjidb.ui.lists

import com.example.kanjidb.ui.LearningState
import org.junit.Assert.*
import org.junit.Test

class MyKanjiMockStateTest {
    @Test
    fun initialSectionsAreCollapsedAndCollectionsAreDisjoint() {
        val state = MyKanjiMockState()
        assertFalse(state.learningExpanded)
        assertFalse(state.knownExpanded)
        assertEquals(30, state.learning.size)
        assertEquals(30, state.known.size)
        assertTrue(state.learning.intersect(state.known.toSet()).isEmpty())
    }

    @Test
    fun deselectingLastCardKeepsModeUntilExplicitCancel() {
        val state = MyKanjiMockState()
        val character = state.learning.first()
        state.beginSelection(LearningState.LEARNING, character)
        assertEquals(setOf(character), state.selected)
        state.toggleSelection(character)
        assertTrue(state.selected.isEmpty())
        assertEquals(LearningState.LEARNING, state.selectionSection)
        state.cancelSelection()
        assertEquals(LearningState.NONE, state.selectionSection)
        assertTrue(state.selected.isEmpty())
    }

    @Test
    fun cancelPreservesAllExpansionCombinationsForEitherSection() {
        for (learningExpanded in listOf(false, true)) {
            for (knownExpanded in listOf(false, true)) {
                for (section in listOf(LearningState.LEARNING, LearningState.KNOWN)) {
                    val state = MyKanjiMockState()
                    if (learningExpanded) state.toggleExpanded(LearningState.LEARNING)
                    if (knownExpanded) state.toggleExpanded(LearningState.KNOWN)
                    state.beginSelection(section, state.kanji(section).first())
                    state.toggleExpanded(LearningState.LEARNING)
                    state.toggleExpanded(LearningState.KNOWN)
                    state.cancelSelection()
                    assertEquals(learningExpanded, state.learningExpanded)
                    assertEquals(knownExpanded, state.knownExpanded)
                }
            }
        }
    }

    @Test
    fun moveMultipleThenMoveBackKeepsCountsAndNoDuplicates() {
        val state = MyKanjiMockState()
        val moved = state.learning.take(2)
        state.toggleExpanded(LearningState.LEARNING)
        state.beginSelection(LearningState.LEARNING, moved[0])
        state.toggleSelection(moved[1])
        state.moveSelected()
        assertEquals(28, state.learning.size)
        assertEquals(32, state.known.size)
        assertTrue(state.known.containsAll(moved))
        assertEquals(LearningState.NONE, state.selectionSection)
        assertTrue(state.selected.isEmpty())
        assertTrue(state.learningExpanded)
        assertFalse(state.knownExpanded)
        state.beginSelection(LearningState.KNOWN, moved[0])
        state.toggleSelection(moved[1])
        state.moveSelected()
        assertEquals(30, state.learning.size)
        assertEquals(30, state.known.size)
        assertEquals(60, (state.learning + state.known).distinct().size)
    }

    @Test
    fun removeWorksForEitherSectionWithoutChangingOtherListOrExpansion() {
        for (section in listOf(LearningState.LEARNING, LearningState.KNOWN)) {
            val state = MyKanjiMockState()
            state.toggleExpanded(LearningState.KNOWN)
            val removed = state.kanji(section).take(2)
            state.beginSelection(section, removed[0])
            state.toggleSelection(removed[1])
            state.removeSelected()
            assertEquals(28, state.kanji(section).size)
            assertEquals(58, state.learning.size + state.known.size)
            assertFalse((state.learning + state.known).any { it in removed })
            assertEquals(LearningState.NONE, state.selectionSection)
            assertTrue(state.selected.isEmpty())
            assertFalse(state.learningExpanded)
            assertTrue(state.knownExpanded)
        }
    }

    @Test
    fun selectionCannotCrossSectionsAndEmptyActionsDoNothing() {
        val state = MyKanjiMockState()
        val character = state.learning.first()
        state.beginSelection(LearningState.LEARNING, character)
        state.beginSelection(LearningState.KNOWN, state.known.first())
        state.toggleSelection(state.known.first())
        assertEquals(setOf(character), state.selected)
        state.toggleSelection(character)
        state.removeSelected()
        state.moveSelected()
        assertEquals(LearningState.LEARNING, state.selectionSection)
        assertEquals(30, state.learning.size)
        assertEquals(30, state.known.size)
    }
}