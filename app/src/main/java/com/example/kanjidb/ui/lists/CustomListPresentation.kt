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

/** Removal through Custom Lists keeps the same selection available until explicit Finish. */
internal fun preserveRemovedListSelection(
    sections: List<KanjiSection>, state: KanjiCollectionState, membership: Set<String>,
    entries: List<KanjiGroupEntry>
): List<KanjiSection> {
    if (!state.selecting) return sections
    val metadata = entries.associateBy { it.character }
    return sections.map { section ->
        if (section.key != state.section) section
        else {
            val shown = section.cards.map { it.character }.toSet()
            val removedSelected = state.selected.filter { it !in membership && it !in shown }
            section.copy(cards = section.cards + removedSelected.map { KanjiCardItem(it, metadata[it]?.reading) })
        }
    }
}
