package com.example.kanjidb.ui.lists

import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.data.user.*
import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.ui.training.availableTrainingPool
import com.example.kanjidb.ui.training.missingTrainingWarning
import org.junit.Assert.*
import org.junit.Test

class MissingKanjiTest {
    private val missing = String(Character.toChars(0x20000))
    private val known = KanjiGroupEntry("日", "ニチ", 1, 1, 4, true, 5)

    @Test fun selectionContainingMissingDisablesOnlyTraining() {
        assertTrue(selectionTrainingEnabled(listOf("日"), setOf("日")))
        assertFalse(selectionTrainingEnabled(listOf("日", missing), setOf("日")))
        assertFalse(selectionTrainingEnabled(emptyList(), setOf("日")))
        val draft = CustomListDraft(emptyList(), listOf("日", missing))
        assertNull(draft.create("Mixed list"))
        assertEquals(listOf("日", missing), draft.selected)
    }
    @Test fun trainingFilteringRetainsOrderWithoutTruncatingUnicodeAndHandlesEmptyPool() {
        assertEquals(listOf("日"), availableTrainingPool(listOf(missing, "日", "日"), setOf("日")))
        assertEquals(emptyList<String>(), availableTrainingPool(listOf(missing), setOf("日")))
    }
    @Test fun listTrainingKeepsValidKanjiAndReportsUniqueMissingCount() {
        val source = listOf("日", missing, missing)
        val filtered = availableTrainingPool(source, setOf("日"))
        assertTrue(filtered.isNotEmpty())
        assertEquals(listOf("日"), filtered)
        assertEquals("1 kanji is not available in the current dictionary and will be excluded from training.",
            missingTrainingWarning(source, filtered))
        assertEquals("2 kanji are not available in the current dictionary and will be excluded from training.",
            missingTrainingWarning(listOf(missing, "月"), emptyList()))
        assertNull(missingTrainingWarning(listOf("日"), listOf("日")))
    }
    @Test fun customListKeepsMissingMemberAndManualPositionWithExplicitAvailability() {
        val list = CustomListWithKanji(CustomListEntity(1, "Mixed", 0),
            listOf(CustomListKanjiEntity(1, missing, 2), CustomListKanjiEntity(1, "日", 4)))
        val cards = customListSection(list, mapOf("日" to known), emptyMap(), KanjiGroupOptions.CollectionDefaults).cards
        assertEquals(listOf(missing, "日"), cards.map { it.character })
        assertFalse(cards.first().dictionaryAvailable)
        assertTrue(cards.last().dictionaryAvailable)
        // Same user records automatically recover when metadata becomes available.
        val recovered = customListSection(list, mapOf("日" to known, missing to known.copy(character = missing)),
            emptyMap(), KanjiGroupOptions.CollectionDefaults)
        assertTrue(recovered.cards.all { it.dictionaryAvailable })
    }
    @Test fun personalGroupsRetainMissingAndDictionaryGroupsRemainDictionaryOnly() {
        val rows = listOf(UserKanjiStateEntity(missing, LearningState.LEARNING, 1),
            UserKanjiStateEntity("日", LearningState.LEARNING, 3))
        val groups = groupMyKanji(listOf(known), rows, KanjiGroupOptions.CollectionDefaults)
        assertEquals(listOf(missing, "日"), groups.first().groups.single().kanji.map { it.character })
        assertEquals(listOf("日"), groupKanji(listOf(known), emptyMap(), KanjiGroupOptions.CollectionDefaults)
            .single().kanji.map { it.character })
    }
}
