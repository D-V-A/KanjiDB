package com.example.kanjidb.ui.lists

import androidx.compose.runtime.saveable.listSaver
import com.example.kanjidb.R
import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.data.user.UserKanjiStateEntity
import com.example.kanjidb.ui.LearningState

enum class KanjiGroupBy { NONE, JLPT, GRADE }
enum class KanjiSortBy { NONE, FREQUENCY, STROKES }
enum class PresenceRule {
    ANY, ONLY, NOT;
    fun matches(present: Boolean): Boolean = when (this) {
        ANY -> true
        ONLY -> present
        NOT -> !present
    }
}
enum class KanjiStatusRule {
    ANY, KNOWN, LEARNING, EITHER, NEITHER;
    fun matches(state: LearningState): Boolean = when (this) {
        ANY -> true
        KNOWN -> state == LearningState.KNOWN
        LEARNING -> state == LearningState.LEARNING
        EITHER -> state != LearningState.NONE
        NEITHER -> state == LearningState.NONE
    }
}

data class KanjiGroupOptions(
    val groupBy: KanjiGroupBy = KanjiGroupBy.JLPT,
    val reverseGroups: Boolean = false,
    val sortBy: KanjiSortBy = KanjiSortBy.FREQUENCY,
    val descending: Boolean = false,
    val jlpt: PresenceRule = PresenceRule.ANY,
    val grade: PresenceRule = PresenceRule.ANY,
    val joyo: PresenceRule = PresenceRule.ANY,
    val status: KanjiStatusRule = KanjiStatusRule.ANY
) {
    val activeRules: Int get() = listOf(jlpt, grade, joyo).count { it != PresenceRule.ANY } +
        if (status == KanjiStatusRule.ANY) 0 else 1

    val sortDirectionLabel: Int get() = when (sortBy) {
        KanjiSortBy.NONE -> R.string.groups_rule_na
        KanjiSortBy.FREQUENCY -> if (descending) R.string.groups_rarer_first else R.string.groups_frequent_first
        KanjiSortBy.STROKES -> if (descending) R.string.groups_complex_first else R.string.groups_simpler_first
    }

    fun rulesSummary(label: (Int) -> String): String = buildList {
        when (jlpt) {
            PresenceRule.ONLY -> add(R.string.groups_jlpt_only)
            PresenceRule.NOT -> add(R.string.groups_not_jlpt)
            PresenceRule.ANY -> Unit
        }
        when (grade) {
            PresenceRule.ONLY -> add(R.string.groups_grade_only)
            PresenceRule.NOT -> add(R.string.groups_no_grade_rule)
            PresenceRule.ANY -> Unit
        }
        when (joyo) {
            PresenceRule.ONLY -> add(R.string.groups_joyo_only)
            PresenceRule.NOT -> add(R.string.groups_not_joyo)
            PresenceRule.ANY -> Unit
        }
        when (status) {
            KanjiStatusRule.KNOWN -> add(R.string.details_known)
            KanjiStatusRule.LEARNING -> add(R.string.details_learning)
            KanjiStatusRule.EITHER -> add(R.string.groups_either)
            KanjiStatusRule.NEITHER -> add(R.string.groups_neither)
            KanjiStatusRule.ANY -> Unit
        }
    }.joinToString(", ") { label(it) }

    fun resetRules(): KanjiGroupOptions = copy(jlpt = PresenceRule.ANY, grade = PresenceRule.ANY,
        joyo = PresenceRule.ANY, status = KanjiStatusRule.ANY)

    companion object {
        val Saver = listSaver<KanjiGroupOptions, String>(
            save = { listOf(it.groupBy.name, it.reverseGroups.toString(), it.sortBy.name,
                it.descending.toString(), it.jlpt.name, it.grade.name, it.joyo.name, it.status.name) },
            restore = { KanjiGroupOptions(KanjiGroupBy.valueOf(it[0]), it[1].toBoolean(),
                KanjiSortBy.valueOf(it[2]), it[3].toBoolean(), PresenceRule.valueOf(it[4]),
                PresenceRule.valueOf(it[5]), PresenceRule.valueOf(it[6]), KanjiStatusRule.valueOf(it[7])) }
        )
    }
}

enum class FrequencyGroup { RANKED, UNRANKED }
data class KanjiFrequencyGroup(val kind: FrequencyGroup, val kanji: List<KanjiGroupEntry>)
data class KanjiGroup(
    val level: Int?, val kanji: List<KanjiGroupEntry>,
    val frequencyGroups: List<KanjiFrequencyGroup> = emptyList()
)

/** Presentation only; retain existing sorted order and never split homogeneous groups. */
internal fun splitFrequencyGroups(kanji: List<KanjiGroupEntry>, sortBy: KanjiSortBy): List<KanjiFrequencyGroup> {
    if (sortBy != KanjiSortBy.FREQUENCY) return emptyList()
    val (ranked, unranked) = kanji.partition { it.frequency != null }
    if (ranked.isEmpty() || unranked.isEmpty()) return emptyList()
    return listOf(KanjiFrequencyGroup(FrequencyGroup.RANKED, ranked),
        KanjiFrequencyGroup(FrequencyGroup.UNRANKED, unranked))
}

/** Pure transformation; callers run large collections on Dispatchers.Default. */
fun groupKanji(
    entries: List<KanjiGroupEntry>,
    states: Map<String, LearningState>,
    options: KanjiGroupOptions
): List<KanjiGroup> {
    val filtered = entries.filter {
        options.jlpt.matches(it.jlpt != null) && options.grade.matches(it.grade != null) &&
            options.joyo.matches(it.isJoyo) &&
            options.status.matches(states[it.character] ?: LearningState.NONE)
    }
    val sort = Comparator<KanjiGroupEntry> { a, b ->
        if (options.sortBy == KanjiSortBy.NONE) return@Comparator a.character.compareTo(b.character)
        val first = if (options.sortBy == KanjiSortBy.FREQUENCY) a.frequency else a.strokeCount
        val second = if (options.sortBy == KanjiSortBy.FREQUENCY) b.frequency else b.strokeCount
        val comparison = when {
            first == null && second == null -> 0
            first == null -> 1
            second == null -> -1
            options.descending -> second.compareTo(first)
            else -> first.compareTo(second)
        }
        if (comparison == 0) a.character.compareTo(b.character) else comparison
    }
    val grouped = filtered.groupBy {
        when (options.groupBy) {
            KanjiGroupBy.NONE -> null
            KanjiGroupBy.JLPT -> it.jlpt
            KanjiGroupBy.GRADE -> it.grade
        }
    }
    val levels = grouped.keys.sortedWith(Comparator { a, b ->
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            options.groupBy == KanjiGroupBy.JLPT -> b.compareTo(a)
            else -> a.compareTo(b)
        }
    }).let { if (options.reverseGroups) it.reversed() else it }
    return levels.map { level ->
        val sorted = grouped.getValue(level).sortedWith(sort)
        KanjiGroup(level, sorted, splitFrequencyGroups(sorted, options.sortBy))
    }
}

internal data class PersonalKanjiSection(val state: LearningState, val groups: List<KanjiGroup>)

/** Same organization inside each user-owned section; Status is deliberately irrelevant here. */
internal fun groupMyKanji(
    entries: List<KanjiGroupEntry>, rows: List<UserKanjiStateEntity>, options: KanjiGroupOptions
): List<PersonalKanjiSection> {
    val dictionary = entries.associateBy { it.character }
    val personalOptions = options.copy(status = KanjiStatusRule.ANY)
    return listOf(LearningState.LEARNING, LearningState.KNOWN).map { state ->
        val owned = rows.filter { it.state == state }.map { row ->
            dictionary[row.character] ?: KanjiGroupEntry(row.character, null, null, null, null, false, null)
        }
        PersonalKanjiSection(state, groupKanji(owned, emptyMap(), personalOptions))
    }
}
