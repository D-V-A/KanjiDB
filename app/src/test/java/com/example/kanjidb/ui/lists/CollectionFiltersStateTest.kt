package com.example.kanjidb.ui.lists

import org.junit.Assert.*
import org.junit.Test

class CollectionFiltersStateTest {
    @Test fun allPagesStartCollapsed() { assertFalse(CollectionFiltersState().expanded) }

    @Test fun showHideAndRestorePreserveNormalPreference() {
        val state = CollectionFiltersState()
        state.toggle(locked = false)
        val restored = CollectionFiltersState.Saver.restore(listOf(state.expanded))!!
        assertTrue(restored.visibleExpanded(locked = false))
        restored.toggle(locked = false)
        assertFalse(restored.expanded)
    }

    private fun temporaryModePreservesPreference(initialExpanded: Boolean) {
        val state = CollectionFiltersState()
        if (initialExpanded) state.toggle(locked = false)
        assertFalse(state.visibleExpanded(locked = true))
        state.toggle(locked = true)
        assertEquals(initialExpanded, state.expanded)
        val restored = CollectionFiltersState.Saver.restore(listOf(state.expanded))!!
        assertFalse(restored.visibleExpanded(locked = true))
        assertEquals(initialExpanded, restored.visibleExpanded(locked = false))
        assertEquals(initialExpanded, state.visibleExpanded(locked = false))
    }

    @Test fun selectionPreservesExpandedPreference() = temporaryModePreservesPreference(true)
    @Test fun selectionPreservesCollapsedPreference() = temporaryModePreservesPreference(false)
    @Test fun reorderPreservesExpandedPreference() = temporaryModePreservesPreference(true)
    @Test fun reorderPreservesCollapsedPreference() = temporaryModePreservesPreference(false)
}
