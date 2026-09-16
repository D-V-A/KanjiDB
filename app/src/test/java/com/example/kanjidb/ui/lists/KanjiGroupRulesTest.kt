package com.example.kanjidb.ui.lists

import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.ui.LearningState
import org.junit.Assert.*
import org.junit.Test

class KanjiGroupRulesTest {
    private fun entry(character: String, grade: Int? = null, jlpt: Int? = null,
        frequency: Int? = null, strokes: Int? = null, joyo: Boolean = false) =
        KanjiGroupEntry(character, null, grade, frequency, strokes, joyo, jlpt)

    @Test fun jlptComplexityAndReverseIncludeMissingLastAndFirst() {
        val entries = listOf(null, 1, 5, 3, 2, 4).mapIndexed { i, level -> entry("$i", jlpt = level) }
        val options = KanjiGroupOptions()
        assertEquals(listOf(5, 4, 3, 2, 1, null), groupKanji(entries, emptyMap(), options).map { it.level })
        assertEquals(listOf(null, 1, 2, 3, 4, 5),
            groupKanji(entries, emptyMap(), options.copy(reverseGroups = true)).map { it.level })
    }

    @Test fun gradeEightNineTenAreRealGroupsAndOnlyNullIsNoGrade() {
        val entries = listOf(null, 10, 9, 8, 6, 5, 4, 3, 2, 1).mapIndexed { i, grade -> entry("$i", grade = grade) }
        val options = KanjiGroupOptions(groupBy = KanjiGroupBy.GRADE)
        val order = listOf(1, 2, 3, 4, 5, 6, 8, 9, 10, null)
        assertEquals(order, groupKanji(entries, emptyMap(), options).map { it.level })
        assertEquals(order.reversed(), groupKanji(entries, emptyMap(), options.copy(reverseGroups = true)).map { it.level })
        assertEquals(1, groupKanji(entries, emptyMap(), options).last().kanji.size)
    }

    @Test fun eachPresenceRuleAndContradictoryCombinationAreRespected() {
        val entries = listOf(entry("a", grade = 1, jlpt = 5, joyo = true), entry("b"))
        fun characters(options: KanjiGroupOptions) = groupKanji(entries, emptyMap(), options)
            .flatMap { it.kanji }.map { it.character }.toSet()
        assertEquals(setOf("a", "b"), characters(KanjiGroupOptions()))
        for (rule in listOf(PresenceRule.ONLY, PresenceRule.NOT)) {
            val expected = if (rule == PresenceRule.ONLY) setOf("a") else setOf("b")
            assertEquals(expected, characters(KanjiGroupOptions(jlpt = rule)))
            assertEquals(expected, characters(KanjiGroupOptions(grade = rule)))
            assertEquals(expected, characters(KanjiGroupOptions(joyo = rule)))
        }
        assertTrue(characters(KanjiGroupOptions(jlpt = PresenceRule.ONLY, grade = PresenceRule.NOT)).isEmpty())
        assertTrue(groupKanji(emptyList(), emptyMap(), KanjiGroupOptions()).isEmpty())
        assertEquals(3, KanjiGroupOptions(jlpt = PresenceRule.ONLY, grade = PresenceRule.NOT,
            status = KanjiStatusRule.NEITHER).activeRules)
    }

    @Test fun statusRulesDistinguishMissingStateAndBothSavedStates() {
        val entries = listOf(entry("none"), entry("known"), entry("learning"))
        val states = mapOf("known" to LearningState.KNOWN, "learning" to LearningState.LEARNING)
        val expected = mapOf(
            KanjiStatusRule.ANY to setOf("none", "known", "learning"),
            KanjiStatusRule.KNOWN to setOf("known"),
            KanjiStatusRule.LEARNING to setOf("learning"),
            KanjiStatusRule.EITHER to setOf("known", "learning"),
            KanjiStatusRule.NEITHER to setOf("none")
        )
        expected.forEach { (rule, characters) ->
            assertEquals(characters, groupKanji(entries, states, KanjiGroupOptions(status = rule))
                .flatMap { it.kanji }.map { it.character }.toSet())
        }
    }

    @Test fun sortingKeepsNullsLastAndCharacterTiesStableInBothDirections() {
        val entries = listOf(entry("z"), entry("b", frequency = 2, strokes = 2),
            entry("c", frequency = 1, strokes = 1), entry("a", frequency = 2, strokes = 2), entry("y"))
        for (sort in KanjiSortBy.entries) for (descending in listOf(false, true)) {
            val options = KanjiGroupOptions(sortBy = sort, descending = descending)
            val expected = if (descending) listOf("a", "b", "c", "y", "z") else listOf("c", "a", "b", "y", "z")
            assertEquals(expected, groupKanji(entries, emptyMap(), options).single().kanji.map { it.character })
            assertEquals(expected, groupKanji(entries.reversed(), emptyMap(), options).single().kanji.map { it.character })
        }
    }

    @Test fun filteredOutGroupsAreOmittedAndExistingStatusChangesUpdateResult() {
        val entries = listOf(entry("a", jlpt = 5), entry("b", jlpt = 1))
        val options = KanjiGroupOptions(status = KanjiStatusRule.NEITHER)
        assertEquals(listOf(5), groupKanji(entries, mapOf("b" to LearningState.KNOWN), options).map { it.level })
        assertTrue(groupKanji(entries, mapOf("a" to LearningState.LEARNING, "b" to LearningState.KNOWN), options).isEmpty())
    }
}
