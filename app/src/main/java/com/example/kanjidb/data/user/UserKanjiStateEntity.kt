package com.example.kanjidb.data.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.kanjidb.ui.LearningState

@Entity(tableName = "kanji_state")
data class UserKanjiStateEntity(
    @PrimaryKey val character: String,
    val state: LearningState
) {
    init {
        require(character.isNotEmpty())
        require(state != LearningState.NONE) { "NONE is represented by an absent row" }
    }
}
