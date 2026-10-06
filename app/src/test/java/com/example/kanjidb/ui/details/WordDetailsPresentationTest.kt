package com.example.kanjidb.ui.details

import com.example.kanjidb.data.dictionary.DictionaryWordDetails
import org.junit.Assert.*
import org.junit.Test

class WordDetailsPresentationTest {
    @Test
    fun layoutThresholdCountsSupplementaryCharactersOnce() {
        assertTrue(isCompactWord("一二三四五"))
        assertFalse(isCompactWord("一二三四五六"))
        assertTrue(isCompactWord("𠮷𠮷𠮷𠮷𠮷"))
        assertFalse(isCompactWord("𠮷𠮷𠮷𠮷𠮷𠮷"))
    }

    @Test
    fun englishTitleIsFirstAndNotRepeatedAmongAdditionalMeanings() {
        val word = DictionaryWordDetails(1L, "今日", listOf("きょう"),
            linkedMapOf("fr" to listOf("aujourd'hui"), "en" to listOf("today", "these days", "today")),
            emptyList(), "きょう", emptyList())
        assertEquals("today", word.orderedMeanings.first())
        assertEquals(listOf("these days", "aujourd'hui"), word.orderedMeanings.drop(1))
        assertTrue(word.copy(meaningGroups = mapOf("en" to listOf("today"))).orderedMeanings.drop(1).isEmpty())
    }
}
