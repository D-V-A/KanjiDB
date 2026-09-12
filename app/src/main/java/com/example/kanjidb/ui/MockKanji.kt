package com.example.kanjidb.ui

import androidx.annotation.StringRes
import com.example.kanjidb.R

enum class LearningState { NONE, LEARNING, KNOWN }

data class MockKanji(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val character: Int,
    val meanings: List<Int>,
    @param:StringRes val onyomi: Int,
    @param:StringRes val kunyomi: Int,
    val strokeCount: Int,
    val isJoyo: Boolean,
    val recommendedWords: List<MockWord>,
    val otherWords: List<MockWord>
)

data class MockWord(
    @param:StringRes val writing: Int,
    @param:StringRes val reading: Int,
    val meanings: List<Int>
)

val mockKanji = listOf(
    MockKanji(
        id = "mountain",
        title = R.string.mock_mountain_title,
        character = R.string.mock_mountain_glyph,
        meanings = listOf(R.string.mock_mountain_meaning),
        onyomi = R.string.mock_mountain_on,
        kunyomi = R.string.mock_mountain_kun,
        strokeCount = 3,
        isJoyo = true,
        recommendedWords = listOf(
            MockWord(R.string.mock_mountain_writing, R.string.mock_mountain_reading,
                listOf(R.string.mock_mountain_meaning)),
            MockWord(R.string.mock_climbing_writing, R.string.mock_climbing_reading,
                listOf(R.string.mock_climbing_meaning))
        ),
        otherWords = listOf(
            MockWord(R.string.mock_volcano_writing, R.string.mock_volcano_reading,
                listOf(R.string.mock_volcano_meaning, R.string.mock_volcano_second))
        )
    ),
    MockKanji(
        id = "water",
        title = R.string.mock_water_title,
        character = R.string.mock_water_glyph,
        meanings = listOf(R.string.mock_water_meaning),
        onyomi = R.string.mock_water_on,
        kunyomi = R.string.mock_water_kun,
        strokeCount = 4,
        isJoyo = true,
        recommendedWords = listOf(
            MockWord(R.string.mock_water_writing, R.string.mock_water_reading,
                listOf(R.string.mock_water_meaning)),
            MockWord(R.string.mock_wednesday_writing, R.string.mock_wednesday_reading,
                listOf(R.string.mock_wednesday_meaning))
        ),
        otherWords = listOf(
            MockWord(R.string.mock_supply_writing, R.string.mock_supply_reading,
                listOf(R.string.mock_supply_meaning))
        )
    ),
    MockKanji(
        id = "fire",
        title = R.string.mock_fire_title,
        character = R.string.mock_fire_glyph,
        meanings = listOf(R.string.mock_fire_meaning),
        onyomi = R.string.mock_fire_on,
        kunyomi = R.string.mock_fire_kun,
        strokeCount = 4,
        isJoyo = true,
        recommendedWords = listOf(
            MockWord(R.string.mock_fire_writing, R.string.mock_fire_reading,
                listOf(R.string.mock_fire_meaning)),
            MockWord(R.string.mock_tuesday_writing, R.string.mock_tuesday_reading,
                listOf(R.string.mock_tuesday_meaning))
        ),
        otherWords = listOf(
            MockWord(R.string.mock_fireworks_writing, R.string.mock_fireworks_reading,
                listOf(R.string.mock_fireworks_meaning))
        )
    )
)