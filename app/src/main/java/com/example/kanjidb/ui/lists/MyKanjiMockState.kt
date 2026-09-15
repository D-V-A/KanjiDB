package com.example.kanjidb.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kanjidb.ui.LearningState

/** UI-only sample collection. Owned by the app composition, never saved to disk. */
internal class MyKanjiMockState {
    var learning by mutableStateOf(listOf("山", "川", "水", "火", "木", "金", "土", "日", "月", "人", "大", "小", "上", "下", "中", "左", "右", "白", "赤", "青", "空", "雨", "田", "花", "草", "虫", "犬", "貝", "石", "竹"))
        private set
    var known by mutableStateOf(listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "百", "千", "円", "年", "時", "分", "今", "先", "学", "生", "本", "名", "文", "字", "男", "女", "子", "目", "耳", "口"))
        private set
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

    fun removeSelected() = finishSelection(move = false)

    fun moveSelected() = finishSelection(move = true)

    private fun finishSelection(move: Boolean) {
        if (selected.isEmpty()) return
        val moved = kanji(selectionSection).filter { it in selected }
        when (selectionSection) {
            LearningState.LEARNING -> {
                learning = learning.filterNot { it in selected }
                if (move) known = (known + moved).distinct()
            }
            LearningState.KNOWN -> {
                known = known.filterNot { it in selected }
                if (move) learning = (learning + moved).distinct()
            }
            LearningState.NONE -> return
        }
        cancelSelection()
    }
}