package com.example.kanjidb.ui.lists

import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.data.user.*
import com.example.kanjidb.ui.LearningState
import org.junit.Assert.*
import org.junit.Test

class CustomListPresentationTest {
    private val options = KanjiGroupOptions(groupBy = KanjiGroupBy.NONE, sortBy = KanjiSortBy.MANUAL)
    private val entries = listOf(
        KanjiGroupEntry("日", "ニチ", 1, 2, 4, true, 5),
        KanjiGroupEntry("月", "ゲツ", 1, 1, 3, true, 4),
        KanjiGroupEntry("火", "カ", null, null, 5, false, null))
    private val list = CustomListWithKanji(CustomListEntity(1, "Practice", 0),
        listOf(CustomListKanjiEntity(1, "日", 0), CustomListKanjiEntity(1, "月", 1), CustomListKanjiEntity(1, "火", 2)))
    private fun section(settings: KanjiGroupOptions = options) = customListSection(
        list, entries.associateBy { it.character }, mapOf("日" to LearningState.KNOWN), settings)
    @Test fun manualUsesMembershipIndices() {
        assertEquals(listOf("日", "月", "火"), section().cards.map { it.character })
    }
    @Test fun sortsUseSharedNullLastSemanticsWithoutWritingMembershipOrder() {
        assertEquals(listOf("月", "日", "火"), section(options.copy(sortBy = KanjiSortBy.FREQUENCY)).cards.map { it.character })
        assertEquals(listOf("月", "日", "火"), section(options.copy(sortBy = KanjiSortBy.STROKES)).cards.map { it.character })
        assertEquals(listOf("日", "月", "火"), list.characters)
    }
    @Test fun sharedRulesFilterVisualSubsetButNotTrainingPool() {
        val filtered = section(options.copy(status = KanjiStatusRule.KNOWN))
        assertEquals(listOf("日"), filtered.cards.map { it.character })
        assertEquals(listOf("日", "月", "火"), customListTrainingPool(listOf(list), 1))
    }
    @Test fun missingDictionaryCharacterStillDisplaysInList() {
        assertEquals(3, customListSection(list, emptyMap(), emptyMap(), options).cards.size)
    }
    @Test fun removedSelectedKanjiRemainAvailableUntilFinish() {
        val selection = KanjiCollectionState()
        selection.begin("1", listOf("日", "月"))
        val remaining = section().copy(cards = section().cards.filter { it.character == "火" })
        val preserved = preserveRemovedListSelection(listOf(remaining), selection, setOf("火"), entries)
        selection.retain(preserved.single().cards.map { it.character }.toSet())
        assertEquals(setOf("日", "月"), selection.selected)
        assertTrue(selection.selecting)
        selection.cancel()
        assertEquals(listOf(remaining), preserveRemovedListSelection(listOf(remaining), selection, setOf("火"), entries))
    }
    @Test fun rulesStillPruneSelectedCharactersThatRemainMembers() {
        val selection = KanjiCollectionState(); selection.begin("1", listOf("日"))
        val filtered = section().copy(cards = emptyList())
        assertTrue(preserveRemovedListSelection(listOf(filtered), selection, list.characters.toSet(), entries).single().cards.isEmpty())
    }
    @Test fun expansionSurvivesSelectionAndFinish() {
        val selection = KanjiCollectionState()
        selection.toggleExpanded("1"); selection.toggleExpanded("2")
        selection.begin("1", listOf("日")); selection.selectAll("1", listOf("日", "月"))
        selection.cancel()
        assertEquals(setOf("1", "2"), selection.expandedKeys)
    }
    @Test fun stagedDraftRestorationPreservesPartialAndCheckedState() {
        val draft = CustomListDraft(listOf(list), listOf("日", "水"))
        assertEquals(ListMembershipState.PARTIAL, draft.state(0))
        draft.create(" New list "); val restored = CustomListDraft.restore(draft.save())
        assertEquals(draft.selected, restored.selected)
        assertEquals(draft.targets, restored.targets)
        assertEquals(ListMembershipState.CHECKED, restored.state(0))
        assertEquals(ListMembershipState.PARTIAL, restored.state(1))
        restored.tap(1); restored.tap(1)
        assertEquals(ListMembershipState.UNCHECKED, restored.state(1))
    }
}
