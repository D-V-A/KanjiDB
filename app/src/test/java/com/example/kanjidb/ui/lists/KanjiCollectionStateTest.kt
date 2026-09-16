package com.example.kanjidb.ui.lists

import org.junit.Assert.*
import org.junit.Test

class KanjiCollectionStateTest {
    @Test fun subgroupCollapseAndHeaderSelectionPreservePresentationAcrossRestore() {
        val state = KanjiCollectionState()
        state.toggleExpanded("N5")
        state.toggleSubgroup("N5:UNRANKED")
        state.selectAll("N5", listOf("ranked", "unranked"))
        assertEquals(setOf("N5"), state.expandedKeys)
        assertEquals(setOf("N5:UNRANKED"), state.collapsedSubgroups)
        assertEquals(setOf("ranked", "unranked"), state.selected)
        assertNull(state.revealCharacter)
        val restored = KanjiCollectionState.restore(state.save())
        assertEquals(state.expandedKeys, restored.expandedKeys)
        assertEquals(state.collapsedSubgroups, restored.collapsedSubgroups)
        assertEquals(state.selected, restored.selected)
        assertNull(restored.revealCharacter)
        restored.cancel()
        restored.toggleSubgroup("N5:RANKED")
        assertEquals(setOf("N5:UNRANKED", "N5:RANKED"), restored.collapsedSubgroups)
        restored.toggleSubgroup("N5:UNRANKED")
        assertEquals(setOf("N5:RANKED"), restored.collapsedSubgroups)
    }

    @Test fun legacyStateRestoresFlagsButNeverReplaysOldCardReveal() {
        val state = KanjiCollectionState.restore(listOf("N5", "one", "1", "N5", "one", "two"))
        assertEquals("N5", state.section)
        assertEquals(setOf("N5"), state.expandedKeys)
        assertEquals(setOf("one", "two"), state.selected)
        assertNull(state.revealCharacter)
    }

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
        assertNull(restored.revealCharacter)
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
