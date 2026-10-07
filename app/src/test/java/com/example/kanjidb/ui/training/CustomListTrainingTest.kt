package com.example.kanjidb.ui.training

import com.example.kanjidb.data.user.*
import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.ui.lists.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CustomListTrainingTest {
    @Test fun explicitPoolsAboveFiftyRetainEveryTargetInBothSessionBuilders() {
        val pool = (1..120).map { "kanji$it" }
        val session = TrainingSession.start(TrainingMode.MY_LISTS, pool, Random(1))
        assertEquals(pool, session.pool)
        assertEquals(pool.toSet(), session.questionOrder.toSet())
        val plan = WordTrainingSelection.build(emptyList(), pool, emptySet(), WordTrainingSettings())
        assertEquals(pool, plan.pool)
        assertEquals(120, plan.coverage.size)
        assertEquals(50, selectTrainingPool(pool, 120, Random(1)).size)
    }

    private fun list(id: Long, order: Long, vararg characters: String) =
        CustomListWithKanji(CustomListEntity(id, "List $id", order),
            characters.mapIndexed { i, char -> CustomListKanjiEntity(id, char, i.toLong()) })
    @Test fun noNonemptyListsUnavailable() {
        assertTrue(nonemptyCustomLists(emptyList()).isEmpty())
        assertTrue(nonemptyCustomLists(listOf(list(1, 0))).isEmpty())
    }
    @Test fun emptyListsExcluded() {
        assertEquals(listOf(2L), nonemptyCustomLists(listOf(list(1, 1), list(2, 0, "日"))).map { it.list.id })
    }
    @Test fun defaultFirstNonemptyInManualOrder() {
        val lists = listOf(list(1, 3), list(2, 0, "日"), list(3, 2, "月"))
        assertEquals(listOf("月"), customListTrainingPool(lists, null))
    }
    @Test fun poolContainsFullMembership() {
        val lists = listOf(list(1, 0, "日", "月", "火"))
        assertEquals(listOf("日", "月", "火"), customListTrainingPool(lists, 1))
    }
    @Test fun visualFiltersDoNotAffectTrainingPool() {
        val lists = listOf(list(1, 0, "日", "月", "火"))
        val options = KanjiGroupOptions(groupBy = KanjiGroupBy.NONE, sortBy = KanjiSortBy.STROKES,
            jlpt = PresenceRule.NOT, status = KanjiStatusRule.KNOWN)
        assertFalse(options.manualReorderAvailable)
        assertTrue(customListSection(lists.single(), emptyMap(), emptyMap(), options).cards.isEmpty())
        assertEquals(3, customListTrainingPool(lists, 1).size)
    }
    @Test fun removedOrEmptiedSelectionFallsBackToFirstNonempty() {
        assertEquals(listOf("月"), customListTrainingPool(listOf(list(1, 2), list(2, 1, "月")), 1))
    }
    @Test fun quantityLogicAndRandomSelectionReused() {
        val source = (1..37).map { "kanji$it" }
        assertEquals(37, sessionSizes(source.size).last())
        assertEquals(2, sessionSizes(2).single())
        val pool = selectTrainingPool(source, 37, Random(1))
        assertEquals(source.toSet(), pool.toSet())
        assertNotEquals(source, pool)
    }
    @Test fun listSessionReusesNewResultActionsAndAttemptShuffle() {
        var session = TrainingSession.start(TrainingMode.MY_LISTS, listOf("日", "月", "火"), Random(1))
        val first = session.currentCharacter!!
        session = session.reveal().answer(TrainingResult.CORRECT)
        assertEquals(listOf(LearningState.LEARNING, LearningState.KNOWN), session.allowedActions(first))
        val second = session.currentCharacter!!
        session = session.reveal().answer(TrainingResult.INCORRECT)
        assertEquals(listOf(LearningState.LEARNING), session.allowedActions(second))
    }
}
