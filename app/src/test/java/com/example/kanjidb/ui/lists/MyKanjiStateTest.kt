package com.example.kanjidb.ui.lists

import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.data.user.UserKanjiStateEntity
import org.junit.Assert.*
import org.junit.Test

class MyKanjiStateTest {
    @Test fun newCollectionIsEmptyAndCollapsed() {
        val state = MyKanjiState()
        assertTrue(state.learning.isEmpty())
        assertTrue(state.known.isEmpty())
        assertFalse(state.learningExpanded)
        assertFalse(state.knownExpanded)
    }

    @Test fun toggleSemantics() {
        for (target in listOf(LearningState.LEARNING, LearningState.KNOWN)) {
            assertEquals(target, LearningState.NONE.toggle(target))
            assertEquals(LearningState.NONE, target.toggle(target))
        }
        assertEquals(LearningState.KNOWN, LearningState.LEARNING.toggle(LearningState.KNOWN))
        assertEquals(LearningState.LEARNING, LearningState.KNOWN.toggle(LearningState.LEARNING))
    }

    @Test fun selectionPreservesExpansionAndCannotCrossSections() {
        for (learning in listOf(false, true)) for (known in listOf(false, true)) {
            val state = MyKanjiState()
            state.updateCollections(listOf(
                UserKanjiStateEntity("a", LearningState.LEARNING),
                UserKanjiStateEntity("b", LearningState.KNOWN)))
            if (learning) state.toggleExpanded(LearningState.LEARNING)
            if (known) state.toggleExpanded(LearningState.KNOWN)
            state.beginSelection(LearningState.LEARNING, "a")
            state.toggleSelection("b")
            assertEquals(setOf("a"), state.selected)
            state.toggleExpanded(LearningState.KNOWN)
            state.toggleSelection("a")
            assertEquals(LearningState.LEARNING, state.selectionSection)
            state.cancelSelection()
            assertEquals(learning, state.learningExpanded)
            assertEquals(known, state.knownExpanded)
        }
    }
}
