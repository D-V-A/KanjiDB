package com.example.kanjidb.ui.search

import com.example.kanjidb.data.dictionary.WordSearchResult
import java.util.Locale

internal fun orderedSubsequence(short: String, long: String): Boolean {
    val wanted = short.codePoints().toArray()
    if (wanted.isEmpty()) return false
    var index = 0
    for (point in long.codePoints().toArray()) {
        if (point == wanted[index] && ++index == wanted.size) return true
    }
    return false
}

/** Complete pairwise compatibility prevents short forms bridging unrelated longer forms. */
internal fun groupWordSearchResults(results: List<WordSearchResult>): List<List<WordSearchResult>> {
    val groups = mutableListOf<MutableList<WordSearchResult>>()
    for (result in results) {
        val meaning = result.primaryMeaning?.trim()?.lowercase(Locale.ROOT)
        val group = if (meaning.isNullOrEmpty()) null else groups.firstOrNull { group ->
            group.all {
                it.primaryMeaning?.trim()?.lowercase(Locale.ROOT) == meaning &&
                    (orderedSubsequence(it.word.written, result.word.written) ||
                        orderedSubsequence(result.word.written, it.word.written))
            }
        }
        if (group == null) groups.add(mutableListOf(result)) else group.add(result)
    }
    // Stable sort preserves relevance/priority order for equal lengths; groups retain best-hit order.
    return groups.map { group -> group.sortedByDescending { it.word.written.codePointCount(0, it.word.written.length) } }
}
