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
    @Test fun removedMembershipImmediatelyHidesCardsAndPrunesSelectionWithoutExitingMode() {
        val selection = KanjiCollectionState()
        selection.begin("1", listOf("日", "月"))
        val updated = list.copy(memberships = list.memberships.filter { it.character == "火" })
        val shown = retainCustomListMembership(listOf(section()), listOf(updated))
        assertEquals(listOf("火"), shown.single().cards.map { it.character })
        selection.retain(updated.characters.toSet())
        assertTrue(selection.selected.isEmpty())
        assertTrue(selection.selecting)
        assertEquals("1", selection.section)
        assertEquals(shown, retainCustomListMembership(shown, listOf(updated)))
    }
    @Test fun rulesStillPruneSelectedCharactersThatRemainMembers() {
        val selection = KanjiCollectionState(); selection.begin("1", listOf("日"))
        val filtered = section().copy(cards = emptyList())
        val shown = retainCustomListMembership(listOf(filtered), listOf(list))
        selection.retain(shown.single().cards.map { it.character }.toSet())
        assertTrue(shown.single().cards.isEmpty())
        assertTrue(selection.selected.isEmpty())
        assertTrue(selection.selecting)
    }
    @Test fun expansionSurvivesSelectionAndFinish() {
        val selection = KanjiCollectionState()
        selection.toggleExpanded("1"); selection.toggleExpanded("2")
        selection.begin("1", listOf("日")); selection.selectAll("1", listOf("日", "月"))
        selection.cancel()
        assertEquals(setOf("1", "2"), selection.expandedKeys)
    }
    @Test fun globalSortAndRulesApplyToAllListsWithoutChangingOrdersOrTrainingPools() {
        val other = list.copy(list = CustomListEntity(2, "Other", 1),
            memberships = list.memberships.map { it.copy(listId = 2) })
        val lists = listOf(list, other)
        val metadata = entries.associateBy { it.character }
        val manual = customListSections(lists, metadata, emptyMap(), options)
        assertEquals(listOf(list.characters, other.characters), manual.map { it.cards.map { card -> card.character } })
        val sorted = customListSections(lists, metadata, emptyMap(), options.copy(sortBy = KanjiSortBy.FREQUENCY))
        assertTrue(sorted.all { it.cards.map { card -> card.character } == listOf("月", "日", "火") })
        val filtered = customListSections(lists, metadata, emptyMap(), options.copy(joyo = PresenceRule.ONLY))
        assertTrue(filtered.all { it.cards.map { card -> card.character } == listOf("日", "月") })
        assertEquals(list.characters, customListTrainingPool(lists, 1))
        assertEquals(other.characters, customListTrainingPool(lists, 2))
    }

    @Test fun draftCountsSurviveStagingAndRestoreWithoutPersistence() {
        val draft = CustomListDraft(listOf(list), listOf("日", "水"))
        assertEquals(3, draft.targets.single().kanjiCount)
        draft.create("Staged")
        assertEquals(2, draft.targets.first().kanjiCount)
        val restored = CustomListDraft.restore(draft.save())
        assertEquals(draft.targets, restored.targets)
        assertEquals(ListMembershipState.CHECKED, restored.state(0))
        assertEquals(ListMembershipState.PARTIAL, restored.state(1))
    }

    @Test fun stagedCountsFollowStagedTargetWhileExistingCountsStayActual() {
        val draft = CustomListDraft(listOf(list), listOf("日", "水"))
        draft.create("Staged")
        draft.tap(0)
        assertEquals(0, draft.targets.first().kanjiCount)
        draft.tap(0)
        assertEquals(2, draft.targets.first().kanjiCount)
        draft.tap(1)
        assertEquals(3, draft.targets[1].kanjiCount)
    }

    @Test fun legacyDraftRestoresWithoutCountField() {
        val saved = listOf("1", "日", "1", "1", "Practice", "", "CHECKED")
        val restored = CustomListDraft.restore(saved)
        assertEquals(ListMembershipState.CHECKED, restored.state(0))
        assertEquals(0, restored.targets.single().kanjiCount)
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
