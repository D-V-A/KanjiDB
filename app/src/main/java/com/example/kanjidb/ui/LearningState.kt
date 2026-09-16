package com.example.kanjidb.ui

enum class LearningState {
    NONE, LEARNING, KNOWN;

    fun toggle(target: LearningState): LearningState {
        require(target != NONE)
        return if (this == target) NONE else target
    }
}
