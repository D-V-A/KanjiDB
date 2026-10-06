package com.example.kanjidb.ui.details

import com.example.kanjidb.data.dictionary.DictionaryWordDetails
import com.example.kanjidb.data.dictionary.orderedWordMeanings

internal fun isCompactWord(written: String): Boolean =
    written.codePointCount(0, written.length) <= 5

internal val DictionaryWordDetails.orderedMeanings: List<String>
    get() = orderedWordMeanings(meaningGroups)
