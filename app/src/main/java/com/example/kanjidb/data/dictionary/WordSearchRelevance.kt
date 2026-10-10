package com.example.kanjidb.data.dictionary

import java.util.Locale

data class MeaningMatch(val quality: Int, val secondary: Boolean)

/** Search-only metadata; shared dictionary summaries and Details keep their own glosses. */
data class WordSearchResult(
    val word: DictionaryWord,
    val primaryMeaning: String?,
    val matchedMeaning: String?,
    val meaningMatch: MeaningMatch?,
    val formRank: Int = 9,
    val common: Int = 0,
    val priority: Int = 0
) {
    val displayMeaning get() = matchedMeaning ?: primaryMeaning.orEmpty()
    internal val rank get() = minOf(formRank, meaningMatch?.let { it.quality + 1 } ?: 9)
}

/** Exact, whole word/phrase, parenthetical word/phrase, then in-word substring. */
internal fun englishMeaningMatch(meaning: String, query: String, primary: Boolean): MeaningMatch? {
    val text = meaning.trim().lowercase(Locale.ROOT)
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return null
    if (text == q) return MeaningMatch(0, !primary)
    val depths = IntArray(text.length)
    var depth = 0
    text.forEachIndexed { index, c ->
        if (c == '(' || c == '[' || c == '{') depth++
        depths[index] = depth
        if (c == ')' || c == ']' || c == '}') depth = maxOf(0, depth - 1)
    }
    var best = 4
    var start = text.indexOf(q)
    while (start >= 0) {
        val end = start + q.length
        val whole = (start == 0 || !Character.isLetterOrDigit(text.codePointBefore(start))) &&
            (end == text.length || !Character.isLetterOrDigit(text.codePointAt(end)))
        val quality = if (!whole) 3 else if ((start until end).any { depths[it] > 0 }) 2 else 1
        best = minOf(best, quality)
        start = text.indexOf(q, start + 1)
    }
    return if (best == 4) null else MeaningMatch(best, !primary)
}

internal val wordSearchOrder = compareBy<WordSearchResult> { it.rank }
    .thenBy { it.meaningMatch?.quality ?: 9 }
    .thenBy { it.meaningMatch?.secondary ?: false }
    .thenByDescending { it.common }
    .thenByDescending { it.priority }
    .thenBy { it.word.written }
    .thenBy { it.word.entryId }

