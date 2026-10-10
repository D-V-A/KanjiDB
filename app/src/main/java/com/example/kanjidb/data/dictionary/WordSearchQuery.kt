package com.example.kanjidb.data.dictionary

import java.util.Locale

internal data class WordSearchQuery(val text: String, val reading: String) {
    val katakana: String get() = toKatakana(reading)
}

/** Identical query preparation for SQL Word Search and local related-word filtering. */
internal fun prepareWordSearchQuery(raw: String): WordSearchQuery {
    val text = raw.trim().lowercase(Locale.ROOT)
    return WordSearchQuery(text, normalizeReading(text).orEmpty())
}

internal fun filterRelatedWords(words: List<DictionaryWord>, raw: String): List<DictionaryWord> {
    val query = prepareWordSearchQuery(raw)
    if (query.text.isEmpty()) return words
    val katakana = query.katakana
    return words.filter { word ->
        word.written.lowercase(Locale.ROOT).contains(query.text) ||
            (query.reading.isNotEmpty() &&
                (word.reading.contains(query.reading) || word.reading.contains(katakana))) ||
            word.meanings.any { it.lowercase(Locale.ROOT).contains(query.text) }
    }
}
