package com.example.kanjidb.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kanjidb.ui.LearningState

/** Transient collection snapshot and selection/expansion state; Room owns all saved data. */
internal class MyKanjiState {
    var learning by mutableStateOf(emptyList<String>())
        private set
    var known by mutableStateOf(emptyList<String>())
        private set

    fun updateCollections(rows: List<com.example.kanjidb.data.user.UserKanjiStateEntity>) {
        learning = rows.filter { it.state == LearningState.LEARNING }.map { it.character }
        known = rows.filter { it.state == LearningState.KNOWN }.map { it.character }
        selected = selected.intersect(kanji(selectionSection).toSet())
    }
    var learningExpanded by mutableStateOf(false)
        private set
    var knownExpanded by mutableStateOf(false)
        private set
    var selectionSection by mutableStateOf(LearningState.NONE)
        private set
    var selected by mutableStateOf(emptySet<String>())
        private set

    fun kanji(section: LearningState): List<String> = when (section) {
        LearningState.LEARNING -> learning
        LearningState.KNOWN -> known
        LearningState.NONE -> emptyList()
    }

    fun expanded(section: LearningState): Boolean = when (section) {
        LearningState.LEARNING -> learningExpanded
        LearningState.KNOWN -> knownExpanded
        LearningState.NONE -> false
    }

    fun toggleExpanded(section: LearningState) {
        // Selection temporarily controls visibility without changing either saved expansion flag.
        if (selectionSection != LearningState.NONE) return
        when (section) {
            LearningState.LEARNING -> learningExpanded = !learningExpanded
            LearningState.KNOWN -> knownExpanded = !knownExpanded
            LearningState.NONE -> Unit
        }
    }

    fun beginSelection(section: LearningState, character: String) {
        if (selectionSection != LearningState.NONE || character !in kanji(section)) return
        selectionSection = section
        selected = setOf(character)
    }

    fun toggleSelection(character: String) {
        if (character !in kanji(selectionSection)) return
        selected = if (character in selected) selected - character else selected + character
    }

    fun cancelSelection() {
        selected = emptySet()
        selectionSection = LearningState.NONE
    }

}
