package com.example.kanjidb.ui.lists

import com.example.kanjidb.R
import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import org.junit.Assert.*
import org.junit.Test

class KanjiGroupPresentationTest {
    @Test fun directionLabelTracksSortWithoutChangingDirection() {
        val ascending = KanjiGroupOptions()
        assertEquals(R.string.groups_frequent_first, ascending.sortDirectionLabel)
        assertEquals(R.string.groups_simpler_first, ascending.copy(sortBy = KanjiSortBy.STROKES).sortDirectionLabel)
        val descending = ascending.copy(descending = true)
        assertEquals(R.string.groups_rarer_first, descending.sortDirectionLabel)
        assertEquals(R.string.groups_complex_first, descending.copy(sortBy = KanjiSortBy.STROKES).sortDirectionLabel)
    }

    private fun entry(character: String, rank: Int?) = KanjiGroupEntry(character, null, 1, rank, 3, true, 5)

    @Test fun mixedFrequencyGroupsKeepRankedFirstAndOnlyReverseRankedOrder() {
        val entries = listOf(entry("z", null), entry("r2", 2), entry("a", null), entry("r1", 1))
        for (descending in listOf(false, true)) {
            val group = groupKanji(entries, emptyMap(), KanjiGroupOptions(descending = descending)).single()
            assertEquals(listOf(FrequencyGroup.RANKED, FrequencyGroup.UNRANKED), group.frequencyGroups.map { it.kind })
            assertEquals(if (descending) listOf("r2", "r1") else listOf("r1", "r2"),
                group.frequencyGroups[0].kanji.map { it.character })
            assertEquals(listOf("a", "z"), group.frequencyGroups[1].kanji.map { it.character })
            assertEquals(group.kanji, group.frequencyGroups.flatMap { it.kanji })
        }
    }

    @Test fun homogeneousEmptyAndStrokeGroupsHaveNoRedundantSubgroups() {
        for (entries in listOf(emptyList(), listOf(entry("a", 1)), listOf(entry("a", null)))) {
            assertTrue(splitFrequencyGroups(entries, KanjiSortBy.FREQUENCY).isEmpty())
        }
        val mixed = listOf(entry("a", 1), entry("b", null))
        assertTrue(groupKanji(mixed, emptyMap(), KanjiGroupOptions(sortBy = KanjiSortBy.STROKES))
            .single().frequencyGroups.isEmpty())
    }

    private fun label(id: Int): String = when (id) {
        R.string.groups_jlpt_only -> "JLPT Only"
        R.string.groups_not_jlpt -> "Not JLPT"
        R.string.groups_grade_only -> "Grade Only"
        R.string.groups_no_grade_rule -> "No Grade"
        R.string.groups_joyo_only -> "J\u014dy\u014d Only"
        R.string.groups_not_joyo -> "Not J\u014dy\u014d"
        R.string.details_known -> "Known"
        R.string.details_learning -> "Learning"
        R.string.groups_either -> "Known or Learning"
        R.string.groups_neither -> "Neither"
        else -> error("Unexpected rule label: $id")
    }

    @Test fun summaryOmitsDisabledRulesAndUsesRequestedOrderAndSeparators() {
        assertEquals("", KanjiGroupOptions().rulesSummary(::label))
        assertEquals("Not JLPT, Not J\u014dy\u014d, Known", KanjiGroupOptions(jlpt = PresenceRule.NOT,
            joyo = PresenceRule.NOT, status = KanjiStatusRule.KNOWN).rulesSummary(::label))
        assertEquals("JLPT Only, Grade Only, J\u014dy\u014d Only, Known or Learning",
            KanjiGroupOptions(jlpt = PresenceRule.ONLY, grade = PresenceRule.ONLY,
                joyo = PresenceRule.ONLY, status = KanjiStatusRule.EITHER).rulesSummary(::label))
        assertEquals("No Grade, Learning", KanjiGroupOptions(grade = PresenceRule.NOT,
            status = KanjiStatusRule.LEARNING).rulesSummary(::label))
        assertEquals("Neither", KanjiGroupOptions(status = KanjiStatusRule.NEITHER).rulesSummary(::label))
    }

    @Test fun resetClearsOnlyRulesAndImmediatelyMatchesTheUnfilteredResult() {
        val configured = KanjiGroupOptions(groupBy = KanjiGroupBy.GRADE, reverseGroups = true,
            sortBy = KanjiSortBy.STROKES, descending = true, jlpt = PresenceRule.NOT,
            grade = PresenceRule.NOT, joyo = PresenceRule.ONLY, status = KanjiStatusRule.KNOWN)
        val reset = configured.resetRules()
        assertEquals(0, reset.activeRules)
        assertEquals("", reset.rulesSummary(::label))
        assertEquals(configured.groupBy, reset.groupBy)
        assertEquals(configured.reverseGroups, reset.reverseGroups)
        assertEquals(configured.sortBy, reset.sortBy)
        assertEquals(configured.descending, reset.descending)
        val entries = listOf(entry("a", 1), entry("b", null))
        assertTrue(groupKanji(entries, emptyMap(), configured).isEmpty())
        assertEquals(2, groupKanji(entries, emptyMap(), reset).single().kanji.size)
        assertEquals(reset, reset.resetRules())
    }
}
