package com.example.kanjidb.ui.lists

import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.data.user.UserKanjiStateEntity
import com.example.kanjidb.data.user.manualOrder
import com.example.kanjidb.data.user.moveKanji
import com.example.kanjidb.data.user.reorderedKanjiRows
import com.example.kanjidb.ui.LearningState
import org.junit.Assert.*
import org.junit.Test

class ManualKanjiOrderTest {
    private val options = KanjiGroupOptions(groupBy = KanjiGroupBy.NONE, sortBy = KanjiSortBy.MANUAL)
    private val entries = listOf(
        KanjiGroupEntry("a", null, 1, 1, 5, true, 5),
        KanjiGroupEntry("b", null, 2, 2, 2, true, 4),
        KanjiGroupEntry("c", null, null, null, 1, false, 5),
        KanjiGroupEntry("x", null, 1, 3, 4, true, 5))
    private val rows = listOf(
        UserKanjiStateEntity("a", LearningState.LEARNING, 2),
        UserKanjiStateEntity("b", LearningState.LEARNING, 0),
        UserKanjiStateEntity("c", LearningState.LEARNING, 1),
        UserKanjiStateEntity("x", LearningState.KNOWN, 0))
    private fun learning(settings: KanjiGroupOptions) = groupMyKanji(entries, rows, settings)[0].groups
        .flatMap { it.kanji }.map { it.character }

    @Test fun automaticSortsAndManualDirectionNeverRewriteStoredOrder() {
        assertEquals(listOf("b", "c", "a"), learning(options))
        assertEquals(listOf("a", "b", "c"), learning(options.copy(sortBy = KanjiSortBy.FREQUENCY)))
        assertEquals(listOf("c", "b", "a"), learning(options.copy(sortBy = KanjiSortBy.STROKES)))
        assertEquals(listOf("b", "c", "a"), learning(options.copy(descending = true)))
        assertEquals(listOf("b", "c", "a"), manualOrder(rows, LearningState.LEARNING).map { it.character })
        assertEquals(listOf("x"), manualOrder(rows, LearningState.KNOWN).map { it.character })
    }

    @Test fun groupingAndRulesUseSubsequencesOfOneStateOrder() {
        val jlpt = groupMyKanji(entries, rows, options.copy(groupBy = KanjiGroupBy.JLPT))[0].groups
        assertEquals(listOf(5, 4), jlpt.map { it.level })
        assertEquals(listOf("c", "a"), jlpt[0].kanji.map { it.character })
        assertEquals(listOf("b"), jlpt[1].kanji.map { it.character })
        assertEquals(listOf("b", "a"), learning(options.copy(grade = PresenceRule.ONLY)))
        assertEquals(listOf("c"), learning(options.copy(joyo = PresenceRule.NOT)))
        assertEquals(listOf("b", "c", "a"), learning(options))
    }

    @Test fun reorderGateRequiresEveryConditionRegardlessOfSavedDirection() {
        assertTrue(options.manualReorderAvailable)
        assertTrue(options.copy(reverseGroups = true, descending = true).manualReorderAvailable)
        for (group in listOf(KanjiGroupBy.JLPT, KanjiGroupBy.GRADE))
            assertFalse(options.copy(groupBy = group).manualReorderAvailable)
        for (sort in listOf(KanjiSortBy.FREQUENCY, KanjiSortBy.STROKES))
            assertFalse(options.copy(sortBy = sort).manualReorderAvailable)
        for (rule in listOf(PresenceRule.ONLY, PresenceRule.NOT)) {
            assertFalse(options.copy(jlpt = rule).manualReorderAvailable)
            assertFalse(options.copy(grade = rule).manualReorderAvailable)
            assertFalse(options.copy(joyo = rule).manualReorderAvailable)
        }
        assertFalse(options.copy(status = KanjiStatusRule.KNOWN).manualReorderAvailable)
    }

    @Test fun movePreviewAndDropPositionsWorkBothDirectionsWithoutCrossStateEntries() {
        val before = listOf("b", "c", "a")
        assertEquals(listOf("c", "a", "b"), moveKanji(before, "b", "a"))
        val after = moveKanji(before, "a", "b")
        assertEquals(listOf("a", "b", "c"), after)
        assertEquals(before, moveKanji(before, "a", "x"))
        val saved = requireNotNull(reorderedKanjiRows(rows, LearningState.LEARNING, before, after))
        assertEquals(listOf("a", "b", "c"), saved.map { it.character })
        assertEquals(listOf(0L, 1L, 2L), saved.map { it.manualPosition })
        assertTrue(saved.all { it.state == LearningState.LEARNING })
        assertEquals(0L, rows.last().manualPosition)
    }

    @Test fun successfulDropDeselectsOnlyDraggedCardAndKeepsSelectionMode() {
        val state = KanjiCollectionState()
        state.begin("LEARNING", listOf("a"))
        state.toggle("b")
        state.toggle("c")
        val entry = state.selectionEntryId
        state.clearReveal()
        state.finishReorder("b")
        assertEquals(setOf("a", "c"), state.selected)
        assertNull(state.revealCharacter)
        assertEquals(entry, state.selectionEntryId)
        state.finishReorder("a")
        state.finishReorder("c")
        assertTrue(state.selecting)
        assertTrue(state.selected.isEmpty())
    }

    @Test fun snackbarEntryEventIsNotReplayedByToggleSelectAllOrRestore() {
        val state = KanjiCollectionState()
        assertEquals(0, state.selectionEntryId)
        state.selectAll("KNOWN", listOf("a", "b"))
        assertEquals(1, state.selectionEntryId)
        state.selectAll("KNOWN", listOf("c"))
        state.toggle("b")
        assertEquals(1, state.selectionEntryId)
        val restored = KanjiCollectionState.restore(state.save())
        assertTrue(restored.selecting)
        assertEquals(0, restored.selectionEntryId)
        state.cancel()
        state.begin("LEARNING", listOf("d"))
        assertEquals(2, state.selectionEntryId)
    }
}
