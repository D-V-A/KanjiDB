package com.example.kanjidb.ui.lists

import androidx.compose.runtime.saveable.listSaver
import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.ui.LearningState

enum class KanjiGroupBy { JLPT, GRADE }
enum class KanjiSortBy { FREQUENCY, STROKES }
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

data class KanjiGroup(val level: Int?, val kanji: List<KanjiGroupEntry>)

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
    val grouped = filtered.groupBy { if (options.groupBy == KanjiGroupBy.JLPT) it.jlpt else it.grade }
    val levels = grouped.keys.sortedWith(Comparator { a, b ->
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            options.groupBy == KanjiGroupBy.JLPT -> b.compareTo(a)
            else -> a.compareTo(b)
        }
    }).let { if (options.reverseGroups) it.reversed() else it }
    return levels.map { KanjiGroup(it, grouped.getValue(it).sortedWith(sort)) }
}
