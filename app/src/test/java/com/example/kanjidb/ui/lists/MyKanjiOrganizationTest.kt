package com.example.kanjidb.ui.lists

import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.data.user.UserKanjiStateEntity
import com.example.kanjidb.ui.LearningState
import org.junit.Assert.*
import org.junit.Test

class MyKanjiOrganizationTest {
    private fun entry(character: String, jlpt: Int? = null, grade: Int? = null,
        rank: Int? = null, strokes: Int? = null, joyo: Boolean = false) =
        KanjiGroupEntry(character, null, grade, rank, strokes, joyo, jlpt)
    private fun row(character: String, state: LearningState = LearningState.LEARNING) =
        UserKanjiStateEntity(character, state)
    private val none = KanjiGroupOptions(groupBy = KanjiGroupBy.NONE, sortBy = KanjiSortBy.NONE)
    private fun List<PersonalKanjiSection>.characters() = flatMap { it.groups }.flatMap { it.kanji }.map { it.character }

    @Test fun noGroupingOrSortingKeepsOwnedSectionsAndDeterministicCharacterOrder() {
        val entries = listOf(entry("z"), entry("a"), entry("b"), entry("outside"))
        val rows = listOf(row("z"), row("b", LearningState.KNOWN), row("a"))
        val result = groupMyKanji(entries, rows, none)
        assertEquals(listOf(LearningState.LEARNING, LearningState.KNOWN), result.map { it.state })
        assertEquals(listOf("a", "z"), result[0].groups.single().kanji.map { it.character })
        assertEquals(listOf("b"), result[1].groups.single().kanji.map { it.character })
        assertTrue(result.flatMap { it.groups }.all { it.frequencyGroups.isEmpty() })
        assertEquals(result, groupMyKanji(entries.reversed(), rows.reversed(), none.copy(descending = true, reverseGroups = true)))
    }

    @Test fun emptySectionsRemainAndMissingDictionaryCharactersAreNotLost() {
        val empty = groupMyKanji(emptyList(), emptyList(), none)
        assertEquals(2, empty.size)
        assertTrue(empty.all { it.groups.isEmpty() })
        val missing = groupMyKanji(emptyList(), listOf(row("missing")), none)
        assertEquals(listOf("missing"), missing.characters())
        assertNull(missing[0].groups.single().kanji.single().reading)
        assertTrue(groupMyKanji(emptyList(), listOf(row("missing")), none.copy(jlpt = PresenceRule.ONLY))
            .all { it.groups.isEmpty() })
    }

    @Test fun jlptOrderingAndExactReverseApplyIndependentlyInsideBothSections() {
        val entries = listOf(1, 5, null, 3, 2, 4).flatMapIndexed { i, level ->
            listOf(entry("L$i", jlpt = level), entry("K$i", jlpt = level))
        }
        val rows = entries.map { row(it.character, if (it.character.startsWith("L")) LearningState.LEARNING else LearningState.KNOWN) }
        for (reverse in listOf(false, true)) {
            val options = none.copy(groupBy = KanjiGroupBy.JLPT, reverseGroups = reverse)
            val order = listOf(5, 4, 3, 2, 1, null).let { if (reverse) it.reversed() else it }
            groupMyKanji(entries, rows, options).forEach { section ->
                assertEquals(order, section.groups.map { it.level })
                assertEquals(6, section.groups.flatMap { it.kanji }.size)
            }
        }
    }

    @Test fun gradesComeFromDataIncludingUnexpectedValuesAndOnlyNullIsMissing() {
        val entries = listOf(10, null, 0, 8, 9, 2, 14).mapIndexed { i, grade -> entry("$i", grade = grade) }
        val rows = entries.map { row(it.character) }
        val options = none.copy(groupBy = KanjiGroupBy.GRADE)
        val order = listOf(0, 2, 8, 9, 10, 14, null)
        assertEquals(order, groupMyKanji(entries, rows, options)[0].groups.map { it.level })
        assertEquals(order.reversed(), groupMyKanji(entries, rows, options.copy(reverseGroups = true))[0].groups.map { it.level })
    }

    @Test fun sortChangesOnlyCardOrderAndDoesNotCreateUserGroups() {
        val entries = listOf(entry("a", jlpt = 5, rank = 9, strokes = 2),
            entry("b", jlpt = 5, rank = 1, strokes = 8), entry("c", jlpt = 4))
        val rows = entries.map { row(it.character) }
        for (groupBy in KanjiGroupBy.entries) {
            val options = none.copy(groupBy = groupBy)
            val expectedLevels = groupMyKanji(entries, rows, options)[0].groups.map { it.level }
            for (sortBy in KanjiSortBy.entries) {
                assertEquals(expectedLevels, groupMyKanji(entries, rows, options.copy(sortBy = sortBy))[0].groups.map { it.level })
            }
        }
        val grouped = none.copy(groupBy = KanjiGroupBy.JLPT)
        assertEquals(listOf("b", "a"), groupMyKanji(entries, rows, grouped.copy(sortBy = KanjiSortBy.FREQUENCY))[0]
            .groups.first().kanji.map { it.character })
        assertEquals(listOf("a", "b"), groupMyKanji(entries, rows, grouped.copy(sortBy = KanjiSortBy.STROKES))[0]
            .groups.first().kanji.map { it.character })
    }

    @Test fun rankedSplitIsLocalToMixedGroupsAndNeverUsedForNoneOrStrokes() {
        val entries = listOf(entry("a", jlpt = 5, rank = 2), entry("b", jlpt = 5),
            entry("c", jlpt = 4, rank = 1), entry("d", jlpt = 4, rank = 3), entry("e", jlpt = 3))
        val rows = entries.map { row(it.character) }
        for (sort in KanjiSortBy.entries) {
            val groups = groupMyKanji(entries, rows, none.copy(groupBy = KanjiGroupBy.JLPT, sortBy = sort))[0].groups
            if (sort == KanjiSortBy.FREQUENCY) {
                assertEquals(listOf(FrequencyGroup.RANKED, FrequencyGroup.UNRANKED), groups[0].frequencyGroups.map { it.kind })
                assertEquals(listOf("a", "b"), groups[0].frequencyGroups.flatMap { it.kanji }.map { it.character })
            } else assertTrue(groups[0].frequencyGroups.isEmpty())
            assertTrue(groups.drop(1).all { it.frequencyGroups.isEmpty() })
        }
        assertEquals(2, groupMyKanji(entries, rows, none.copy(sortBy = KanjiSortBy.FREQUENCY))[0]
            .groups.single().frequencyGroups.size)
    }

    @Test fun personalRulesCombineWithAndWithoutStatusFilteringEitherSection() {
        val entries = listOf(entry("a", jlpt = 5, grade = 1, joyo = true),
            entry("b", jlpt = 5, grade = 1, joyo = true), entry("c", jlpt = 5), entry("d"))
        val rows = listOf(row("a"), row("b", LearningState.KNOWN), row("c"), row("d", LearningState.KNOWN))
        val options = none.copy(jlpt = PresenceRule.ONLY, grade = PresenceRule.ONLY, joyo = PresenceRule.ONLY,
            status = KanjiStatusRule.NEITHER)
        assertEquals(listOf("a", "b"), groupMyKanji(entries, rows, options).characters())
        assertEquals(listOf("d"), groupMyKanji(entries, rows, none.copy(jlpt = PresenceRule.NOT)).characters())
        assertEquals(listOf("c", "d"), groupMyKanji(entries, rows, none.copy(grade = PresenceRule.NOT, joyo = PresenceRule.NOT)).characters())
        val reset = options.copy(groupBy = KanjiGroupBy.GRADE, sortBy = KanjiSortBy.STROKES,
            reverseGroups = true, descending = true).resetRules()
        assertEquals(0, reset.activeRules)
        assertEquals(KanjiGroupBy.GRADE, reset.groupBy)
        assertEquals(KanjiSortBy.STROKES, reset.sortBy)
        assertTrue(reset.reverseGroups && reset.descending)
        assertEquals(setOf("a", "b", "c", "d"), groupMyKanji(entries, rows, reset).characters().toSet())
    }

    @Test fun groupHeaderSelectionStaysInsideOwningSectionAndCreatesNoReveal() {
        val entries = listOf(entry("L5", jlpt = 5), entry("L4", jlpt = 4), entry("K5", jlpt = 5), entry("filtered"))
        val rows = listOf(row("L5"), row("L4"), row("K5", LearningState.KNOWN), row("filtered"))
        val sections = groupMyKanji(entries, rows, none.copy(groupBy = KanjiGroupBy.JLPT, jlpt = PresenceRule.ONLY))
        val state = KanjiCollectionState()
        state.toggleExpanded("LEARNING")
        state.toggleSubgroup("LEARNING:JLPT:5")
        state.selectAll(sections[0].state.name, sections[0].groups.first().kanji.map { it.character })
        assertEquals("LEARNING", state.section)
        assertEquals(setOf("L5"), state.selected)
        assertNull(state.revealCharacter)
        assertEquals(setOf("LEARNING:JLPT:5"), state.collapsedSubgroups)
        state.selectAll(sections[0].state.name, sections[0].groups.flatMap { it.kanji }.map { it.character })
        assertEquals(setOf("L5", "L4"), state.selected)
        val restored = KanjiCollectionState.restore(state.save())
        assertEquals(state.selected, restored.selected)
        assertEquals(state.expandedKeys, restored.expandedKeys)
        assertEquals(state.collapsedSubgroups, restored.collapsedSubgroups)
        assertNull(restored.revealCharacter)
        state.cancel()
        state.selectAll(sections[1].state.name, sections[1].groups.single().kanji.map { it.character })
        assertEquals("KNOWN", state.section)
        assertEquals(setOf("K5"), state.selected)
    }

    @Test fun nestedClearanceIndicesRespectCollapsedUserAndTechnicalGroups() {
        val a = KanjiCardItem("a", null)
        val b = KanjiCardItem("b", null)
        val ranked = KanjiSubgroup("L:N5:RANKED", "Ranked", listOf(a))
        val unranked = KanjiSubgroup("L:N5:UNRANKED", "Unranked", listOf(b))
        val group = KanjiSubgroup("L:N5", "N5", listOf(a, b), listOf(ranked, unranked), selectable = true)
        assertEquals(listOf("subheader:L:N5", "subheader:L:N5:RANKED", "a", "subheader:L:N5:UNRANKED", "b"),
            subgroupItemKeys(listOf(group), emptySet()))
        assertEquals(listOf("subheader:L:N5"), subgroupItemKeys(listOf(group), setOf("L:N5")))
        assertEquals(listOf("subheader:L:N5", "subheader:L:N5:RANKED", "subheader:L:N5:UNRANKED", "b"),
            subgroupItemKeys(listOf(group), setOf(ranked.key)))
        assertTrue(group.selectable)
        assertFalse(ranked.selectable || unranked.selectable)
    }
}
