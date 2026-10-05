package com.example.kanjidb.ui.lists

import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.data.user.CustomListWithKanji
import com.example.kanjidb.ui.LearningState

internal fun customListSection(
    list: CustomListWithKanji, metadata: Map<String, KanjiGroupEntry>,
    states: Map<String, LearningState>, options: KanjiGroupOptions
): KanjiSection {
    val members = list.characters.map { character ->
        metadata[character] ?: KanjiGroupEntry(character, null, null, null, null, false, null)
    }
    val organized = groupKanji(members, states, options.copy(groupBy = KanjiGroupBy.NONE),
        list.memberships.associate { it.character to it.manualIndex }).flatMap { it.kanji }
    return KanjiSection(list.list.id.toString(), list.list.name, organized.cards())
}

/** A single tab-owned set of options applies equally to every list. */
internal fun customListSections(
    lists: List<CustomListWithKanji>, metadata: Map<String, KanjiGroupEntry>,
    states: Map<String, LearningState>, options: KanjiGroupOptions
): List<KanjiSection> = lists.map { customListSection(it, metadata, states, options) }

/** Immediately hide removed memberships while background organization catches up with Room. */
internal fun retainCustomListMembership(
    sections: List<KanjiSection>, lists: List<CustomListWithKanji>
): List<KanjiSection> {
    val membership = lists.associate { it.list.id.toString() to it.characters.toSet() }
    return sections.filter { it.key in membership }.map { section ->
        section.copy(cards = section.cards.filter { it.character in membership.getValue(section.key) })
    }
}
