package com.example.kanjidb.ui.lists

import org.junit.Assert.*
import org.junit.Test

class CollectionFiltersStateTest {
    @Test fun allPagesStartCollapsed() { assertFalse(CollectionFiltersState().expanded) }
    @Test fun showHideChangesOnlyExpansion() {
        val state = CollectionFiltersState()
        state.toggle(locked = false); assertTrue(state.expanded)
        val restored = CollectionFiltersState.Saver.restore(listOf(state.expanded))!!
        assertTrue(restored.expanded)
        restored.toggle(locked = false); assertFalse(restored.expanded)
    }
    @Test fun enteringSelectionOrReorderCollapsesAndCannotExpandWhileLocked() {
        val state = CollectionFiltersState()
        state.toggle(locked = false); state.enterMode()
        state.toggle(locked = true)
        assertFalse(state.expanded)
        state.toggle(locked = false)
        assertTrue(state.expanded)
    }
}
