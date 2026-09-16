package com.example.kanjidb.ui.lists

import org.junit.Assert.*
import org.junit.Test

class KanjiCollectionStateTest {
    @Test fun headerSelectionPreservesViewportWhileCardSelectionRequestsReveal() {
        val state = KanjiCollectionState()
        state.selectAll("N5", listOf("one", "two"))
        assertTrue(state.selecting)
        assertEquals(setOf("one", "two"), state.selected)
        assertNull(state.revealCharacter)
        assertNull(KanjiCollectionState.restore(state.save()).revealCharacter)

        state.cancel()
        state.begin("N5", listOf("two"))
        assertEquals("two", state.revealCharacter)
        state.selectAll("N5", listOf("one", "two"))
        assertEquals(setOf("one", "two"), state.selected)
        assertNull(state.revealCharacter)

        state.toggle("three")
        assertEquals("three", state.revealCharacter)
    }

    @Test fun emptySelectionRemainsActiveUntilExplicitCancel() {
        val state = KanjiCollectionState()
        state.begin("a", listOf("one"))
        state.toggle("one")
        assertTrue(state.selecting)
        assertTrue(state.selected.isEmpty())
        state.cancel()
        assertFalse(state.selecting)
    }

    @Test fun selectAllUsesOnlyProvidedFilteredCardsAndCanIncludeAnotherGroup() {
        val state = KanjiCollectionState()
        state.selectAll("N5", listOf("visible1", "visible2"))
        state.selectAll("N4", listOf("visible3"))
        assertEquals(setOf("visible1", "visible2", "visible3"), state.selected)
        state.retain(setOf("visible2"))
        assertEquals(setOf("visible2"), state.selected)
        state.retain(emptySet())
        assertTrue(state.selecting)
        assertTrue(state.selected.isEmpty())
    }

    @Test fun expansionAndSelectionSurviveSaveRestoreAndCancel() {
        val state = KanjiCollectionState()
        state.toggleExpanded("LEARNING")
        state.begin("KNOWN", listOf("one", "two"))
        state.toggleExpanded("KNOWN")
        val restored = KanjiCollectionState.restore(state.save())
        assertEquals("KNOWN", restored.section)
        assertEquals(setOf("one", "two"), restored.selected)
        assertEquals("one", restored.revealCharacter)
        assertEquals(setOf("LEARNING"), restored.expandedKeys)
        restored.cancel()
        assertEquals(setOf("LEARNING"), restored.expandedKeys)
    }

    @Test fun noSelectionForEmptyHeaderAndBeginDoesNotReplaceActiveSelection() {
        val state = KanjiCollectionState()
        state.selectAll("empty", emptyList())
        assertFalse(state.selecting)
        state.begin("one", listOf("a"))
        state.begin("two", listOf("b"))
        assertEquals("one", state.section)
        assertEquals(setOf("a"), state.selected)
        state.toggle("b")
        assertEquals("b", state.revealCharacter)
    }
}
