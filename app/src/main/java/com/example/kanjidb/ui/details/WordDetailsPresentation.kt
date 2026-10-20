package com.example.kanjidb.ui.details

import com.example.kanjidb.data.dictionary.DictionaryWordDetails
import com.example.kanjidb.data.dictionary.orderedWordMeanings
import com.example.kanjidb.data.dictionary.ReadingMeaningGroup

internal fun isCompactWord(written: String): Boolean =
    written.codePointCount(0, written.length) <= 5

internal val DictionaryWordDetails.orderedMeanings: List<String>
    get() = orderedWordMeanings(meaningGroups)

internal fun ReadingMeaningGroup.otherMeaningTexts(primary: String?): List<String> =
    glosses.filter { it.language == "en" }.ifEmpty { glosses }
        .map { it.text }.distinct().filter { it != primary }
