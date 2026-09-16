package com.example.kanjidb.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import com.example.kanjidb.data.user.UserKanjiStateEntity
import com.example.kanjidb.ui.LearningState

/** Room-derived collection snapshot; all selection/expansion behavior lives in the common state. */
internal class MyKanjiState(val collection: KanjiCollectionState = KanjiCollectionState()) {
    var learning by mutableStateOf(emptyList<String>())
        private set
    var known by mutableStateOf(emptyList<String>())
        private set
    val learningExpanded get() = expanded(LearningState.LEARNING)
    val knownExpanded get() = expanded(LearningState.KNOWN)
    val selectionSection get() = collection.section?.let(LearningState::valueOf) ?: LearningState.NONE
    val selected get() = collection.selected

    fun updateCollections(rows: List<UserKanjiStateEntity>) {
        learning = rows.filter { it.state == LearningState.LEARNING }.map { it.character }
        known = rows.filter { it.state == LearningState.KNOWN }.map { it.character }
        collection.retain(kanji(selectionSection).toSet())
    }
    fun kanji(section: LearningState): List<String> = when (section) {
        LearningState.LEARNING -> learning
        LearningState.KNOWN -> known
        LearningState.NONE -> emptyList()
    }
    fun expanded(section: LearningState) = section.name in collection.expandedKeys
    fun toggleExpanded(section: LearningState) = collection.toggleExpanded(section.name)
    fun beginSelection(section: LearningState, character: String) {
        if (character in kanji(section)) collection.begin(section.name, listOf(character))
    }
    fun toggleSelection(character: String) {
        if (character in kanji(selectionSection)) collection.toggle(character)
    }
    fun cancelSelection() = collection.cancel()

    companion object {
        val Saver = listSaver<MyKanjiState, String>(
            save = { it.collection.save() }, restore = { MyKanjiState(KanjiCollectionState.restore(it)) }
        )
    }
}
