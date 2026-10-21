package com.example.kanjidb.ui.lists

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.kanjidb.R
import com.example.kanjidb.data.dictionary.KanjiGroupEntry

internal fun List<KanjiGroupEntry>.cards(available: Set<String>? = null) = map {
    KanjiCardItem(it.character, it.reading, available == null || it.character in available)
}

@Composable
internal fun kanjiGroupTitle(groupBy: KanjiGroupBy, level: Int?): String = when (groupBy) {
    KanjiGroupBy.NONE -> stringResource(R.string.groups_rule_na)
    KanjiGroupBy.JLPT -> if (level == null) stringResource(R.string.groups_no_jlpt)
        else stringResource(R.string.groups_jlpt_level, level)
    KanjiGroupBy.GRADE -> if (level == null) stringResource(R.string.groups_no_grade)
        else stringResource(R.string.groups_grade_level, level)
}

@Composable
internal fun frequencySubgroups(key: String, group: KanjiGroup, available: Set<String>? = null): List<KanjiSubgroup> =
    group.frequencyGroups.map { subgroup ->
        KanjiSubgroup("$key:${subgroup.kind.name}",
            stringResource(if (subgroup.kind == FrequencyGroup.RANKED) R.string.groups_ranked else R.string.groups_unranked),
            subgroup.kanji.cards(available))
    }

@Composable
internal fun personalKanjiSections(
    sections: List<PersonalKanjiSection>, groupBy: KanjiGroupBy, available: Set<String>? = null
): List<KanjiSection> = sections.map { section ->
    val key = section.state.name
    val subgroups = if (groupBy == KanjiGroupBy.NONE) {
        section.groups.flatMap { frequencySubgroups(key, it, available) }
    } else section.groups.map { group ->
        val groupKey = "$key:${groupBy.name}:${group.level}"
        KanjiSubgroup(groupKey, kanjiGroupTitle(groupBy, group.level), group.kanji.cards(available),
            subgroups = frequencySubgroups(groupKey, group, available), selectable = true)
    }
    KanjiSection(key, stringResource(if (section.state == com.example.kanjidb.ui.LearningState.LEARNING)
        R.string.details_learning else R.string.details_known),
        section.groups.flatMap { it.kanji }.cards(available), subgroups)
}
