package com.example.kanjidb.ui.details

import com.example.kanjidb.data.dictionary.DictionaryWordDetails

internal fun isCompactWord(written: String): Boolean =
    written.codePointCount(0, written.length) <= 5

internal val DictionaryWordDetails.orderedMeanings: List<String>
    get() = (meaningGroups["en"].orEmpty() + meaningGroups.filterKeys { it != "en" }.values.flatten()).distinct()
