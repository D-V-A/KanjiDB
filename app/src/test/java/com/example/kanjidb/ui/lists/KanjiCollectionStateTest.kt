package com.example.kanjidb.ui.lists

import org.junit.Assert.*
import org.junit.Test

class KanjiCollectionStateTest {
    @Test fun listsOverviewAndSwitchingPreserveUniqueSelectionAndTrainingPool() {
        val state = KanjiCollectionState()
        state.toggleExpanded("A")
        state.begin("A", listOf("\u65e5", "\u6708"), multiSection = true)
        state.collapseSections(listOf("A"))
        assertTrue(state.selecting)
        assertEquals(setOf("\u65e5", "\u6708"), state.selected)
        assertFalse(state.isExpanded("A", multiSection = true))
        assertNull(state.revealCharacter)
        assertNull(state.revealSection)
        state.toggleExpanded("B", duringSelection = true)
        state.begin("B", listOf("\u65e5", "\u6c34"), multiSection = true)
        assertEquals(setOf("\u65e5", "\u6708", "\u6c34"), state.selected)
        assertEquals("B", state.revealSection)
        state.toggle("\u65e5", "B")
        assertEquals(setOf("\u6708", "\u6c34"), state.selected)
        state.selectAll("C", listOf("\u6c34", "\u6728", "\u91d1"), expand = true)
        assertEquals(setOf("\u6708", "\u6c34", "\u6728", "\u91d1"), state.selected)
        state.collapseSections(listOf("B", "C"))
        val restored = KanjiCollectionState.restore(state.save())
        assertEquals(state.selected, restored.selected)
        assertTrue(restored.selecting)
        assertTrue(restored.expandedKeys.isEmpty())
        val training = com.example.kanjidb.ui.training.TrainingSession.start(
            com.example.kanjidb.ui.training.TrainingMode.MY_LISTS, restored.selected.toList())
        assertEquals(state.selected, training.pool.toSet())
        assertEquals(state.selected.size, training.pool.size)
    }

    @Test fun listHeaderTogglePreservesOutsideTargetsAndUnselectsSharedTargetsEverywhere() {
        val state = KanjiCollectionState()
        state.begin("A", listOf("\u65e5", "\u6708"), multiSection = true)
        state.begin("B", listOf("\u65e5", "\u6c34"), multiSection = true)
        state.toggleAll("A", listOf("\u65e5", "\u6708", "\u706b"))
        assertEquals(setOf("\u65e5", "\u6708", "\u706b", "\u6c34"), state.selected)
        state.toggleAll("A", listOf("\u65e5", "\u6708", "\u706b"))
        assertEquals(setOf("\u6c34"), state.selected)
        state.selectAll("B", listOf("\u65e5", "\u6c34"))
        assertEquals(setOf("\u65e5", "\u6c34"), state.selected)
        // Removing A cannot lose shared targets that remain in B.
        state.retain(setOf("\u65e5", "\u6c34"))
        assertEquals(setOf("\u65e5", "\u6c34"), state.selected)
    }

    @Test fun listRevealUsesItsOwnSectionAndIsTransientAcrossRestore() {
        val state = KanjiCollectionState()
        state.begin("A", listOf("\u65e5"), multiSection = true)
        state.toggle("\u6708", "B")
        assertEquals("\u6708", state.revealCharacter)
        assertEquals("B", state.revealSection)
        val restored = KanjiCollectionState.restore(state.save())
        assertNull(restored.revealCharacter)
        assertNull(restored.revealSection)
        state.clearReveal()
        assertNull(state.revealSection)
        val otherTab = KanjiCollectionState()
        assertFalse(otherTab.selecting)
        assertTrue(otherTab.selected.isEmpty())
    }

    @Test fun headerToggleClearsOnlyItsContextAndKeepsSelectionMode() {
        val state = KanjiCollectionState()
        state.selectAll("list", listOf("one", "two", "outside"))
        state.toggleAll("list", listOf("one", "two"))
        assertEquals(setOf("outside"), state.selected)
        assertTrue(state.selecting)
        state.toggleAll("list", listOf("one", "two"))
        assertEquals(setOf("one", "two", "outside"), state.selected)
        state.toggle("two")
        state.toggleAll("list", listOf("one", "two"))
        assertEquals(setOf("one", "two", "outside"), state.selected)
        assertNull(state.revealCharacter)
    }

    @Test fun entireListHeaderClearsSelectionWithoutExiting() {
        val state = KanjiCollectionState()
        state.selectAll("list", listOf("one", "two"))
        state.toggleAll("list", listOf("one", "two"))
        assertTrue(state.selected.isEmpty())
        assertEquals("list", state.section)
    }

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

    @Test fun groupsLongPressExtendsSelectionAcrossSectionsWithoutResetOrDuplicates() {
        val state = KanjiCollectionState()
        state.begin("N5", listOf("日"), multiSection = true)
        val entry = state.selectionEntryId
        state.begin("N4", listOf("日", "月"), multiSection = true)
        state.selectAll("N3", listOf("月", "火"))
        assertEquals(setOf("日", "月", "火"), state.selected)
        assertEquals(entry, state.selectionEntryId)
        assertEquals("N5", state.section)
        val restored = KanjiCollectionState.restore(state.save())
        assertEquals(state.selected, restored.selected)
    }

    @Test fun groupsCanExpandOtherGroupsDuringSelectionWithoutLosingSelection() {
        val state = KanjiCollectionState()
        state.begin("N5", listOf("日"), multiSection = true)
        state.toggleExpanded("N4", duringSelection = true)
        state.toggleSubgroup("N4:UNRANKED", duringSelection = true)
        assertTrue("N4" in state.expandedKeys)
        assertTrue("N4:UNRANKED" in state.collapsedSubgroups)
        assertEquals(setOf("日"), state.selected)
        state.begin("N4", listOf("月"), multiSection = true)
        state.toggle("日")
        assertEquals(setOf("月"), state.selected)
    }

    @Test fun isolatedCollectionsCannotExtendByLongPressOrExpandAnotherSection() {
        val state = KanjiCollectionState()
        state.begin("LEARNING", listOf("日"))
        state.begin("KNOWN", listOf("月"))
        state.toggleExpanded("KNOWN")
        assertEquals(setOf("日"), state.selected)
        assertEquals("LEARNING", state.section)
        assertTrue(state.expandedKeys.isEmpty())
    }

    @Test fun partialMembershipRemovalKeepsRemainingSelection() {
        val state = KanjiCollectionState()
        state.begin("list:1", listOf("日", "月"))
        state.retain(setOf("月", "火"))
        assertEquals(setOf("月"), state.selected)
        assertTrue(state.selecting)
        assertEquals("list:1", state.section)
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

    @Test fun selectedGroupsCanCollapseWithoutChangingSharedSelection() {
        val state = KanjiCollectionState()
        state.selectAll("N5", listOf("a", "b"), expand = true)
        state.selectAll("N4", listOf("b", "c"), expand = true)
        assertEquals(setOf("a", "b", "c"), state.selected)
        state.toggleExpanded("N5", duringSelection = true)
        state.toggleExpanded("N4", duringSelection = true)
        assertFalse(state.isExpanded("N5", multiSection = true))
        assertFalse(state.isExpanded("N4", multiSection = true))
        assertTrue(state.selecting)
        assertEquals(setOf("a", "b", "c"), state.selected)
        val restored = KanjiCollectionState.restore(state.save())
        assertFalse(restored.isExpanded("N5", multiSection = true))
        assertEquals(state.selected, restored.selected)
        restored.toggleExpanded("N5", duringSelection = true)
        assertTrue(restored.isExpanded("N5", multiSection = true))
        assertEquals(state.selected, restored.selected)
    }

    @Test fun groupHeaderSelectAllReopensCollapsedGroupAndAllowsCollapsingAgain() {
        val state = KanjiCollectionState()
        state.selectAll("N5", listOf("a"), expand = true)
        state.toggleExpanded("N5", duringSelection = true)
        state.selectAll("N5", listOf("a", "b"), expand = true)
        assertTrue(state.isExpanded("N5", multiSection = true))
        assertNull(state.revealCharacter)
        state.toggleExpanded("N5", duringSelection = true)
        assertFalse(state.isExpanded("N5", multiSection = true))
        assertEquals(setOf("a", "b"), state.selected)
    }

    @Test fun isolatedSelectionStillDisplaysItsActiveSection() {
        val state = KanjiCollectionState()
        state.begin("LEARNING", listOf("a"))
        assertTrue(state.isExpanded("LEARNING", multiSection = false))
        assertFalse(state.isExpanded("KNOWN", multiSection = false))
    }
}
