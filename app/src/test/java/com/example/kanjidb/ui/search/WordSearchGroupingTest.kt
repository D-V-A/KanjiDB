package com.example.kanjidb.ui.search

import com.example.kanjidb.data.dictionary.DictionaryWord
import com.example.kanjidb.data.dictionary.WordSearchResult
import org.junit.Assert.*
import org.junit.Test

class WordSearchGroupingTest {
    private fun result(id: Long, written: String, meaning: String? = "Sunday") =
        WordSearchResult(DictionaryWord(id, written, "reading", listOfNotNull(meaning)), meaning, null, null)

    @Test fun orderedSubsequenceRespectsOrderAndCodePoints() {
        assertTrue(orderedSubsequence("AB", "ABC"))
        assertTrue(orderedSubsequence("AC", "ABC"))
        assertTrue(orderedSubsequence("A", "ABC"))
        assertFalse(orderedSubsequence("CA", "ABC"))
        assertFalse(orderedSubsequence("AA", "ABC"))
        assertFalse(orderedSubsequence("", "ABC"))
        assertTrue(orderedSubsequence("𠮷日", "𠮷曜日"))
        assertFalse(orderedSubsequence("\uD842", "𠮷"))
    }

    @Test fun groupsLongestFirstButPreservesIdentitiesAndRankingForEqualLengths() {
        val words = listOf(result(1, "日"), result(2, "日曜"), result(3, "日曜日"))
        assertEquals(listOf(3L, 2L, 1L), groupWordSearchResults(words).single().map { it.word.entryId })
        val equal = listOf(result(2, "日曜"), result(1, "日曜"))
        assertEquals(equal, groupWordSearchResults(equal).single())
    }

    @Test fun noBridgeMergingDifferentMeaningsOrMissingMeanings() {
        val groups = groupWordSearchResults(listOf(result(1, "A"), result(2, "AB"), result(3, "AC")))
        assertEquals(listOf(listOf(2L, 1L), listOf(3L)), groups.map { it.map { row -> row.word.entryId } })
        assertEquals(2, groupWordSearchResults(listOf(result(1, "日"), result(2, "日曜", "day"))).size)
        assertEquals(2, groupWordSearchResults(listOf(result(1, "日", null), result(2, "日曜", null))).size)
        assertEquals(2, groupWordSearchResults(listOf(result(1, "ABC"), result(2, "CA"))).size)
    }
}
